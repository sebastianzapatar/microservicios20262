# ☸️ Manifiestos de Kubernetes

Esta carpeta contiene la definición completa del Sistema de Salud como objetos
de Kubernetes. **No se aplica con `kubectl apply -f k8s/`**, sino desde la raíz
del proyecto con:

```bash
kubectl apply -k .
```

El `-k` usa el `kustomization.yaml` de la raíz, que además de aplicar estos
archivos genera el ConfigMap del realm de Keycloak y mete todo en el namespace
`salud`. Para el camino automatizado (construir imágenes incluidas):

```bash
./scripts/k8s-deploy.sh
```

---

## Qué hay en cada archivo

| Archivo | Qué crea |
| :--- | :--- |
| `00-namespace.yaml` | El namespace `salud`, donde vive todo lo demás |
| `01-config.yaml` | `ConfigMap` (valores no sensibles) + `Secret` (credenciales) |
| `02-databases.yaml` | PostgreSQL, MongoDB y MySQL, cada uno con su `PVC` y su `Service` |
| `03-keycloak.yaml` | Keycloak, que monta el realm desde un ConfigMap generado |
| `04-eureka.yaml` | Eureka Server (service discovery) |
| `05-microservices.yaml` | Pacientes, Historial Médico y FastAPI |
| `06-gateway.yaml` | API Gateway, la única puerta de entrada al sistema |

Los números no los interpreta Kubernetes: son para que el orden de lectura
coincida con el orden de dependencia. `kustomize` sí ordena la aplicación real
(primero Namespace, ConfigMaps y Secrets; al final los Deployments).

**Total: 25 objetos.**

---

## Decisiones que conviene entender

### `strategy: Recreate` en las bases de datos
Un `PersistentVolumeClaim` con `accessModes: ReadWriteOnce` solo admite **un
Pod montándolo a la vez**. Con la estrategia por defecto (`RollingUpdate`),
Kubernetes crearía el Pod nuevo antes de borrar el viejo y el nuevo se quedaría
en `Pending` para siempre. `Recreate` apaga primero y arranca después.

### `initContainers` en lugar de `depends_on`
Kubernetes arranca todos los Pods a la vez, sin respetar dependencias. Un
servicio Spring Boot con JPA que no encuentra PostgreSQL **muere al arrancar**.
El `initContainer` espera a que la base responda antes de dejar arrancar al
contenedor principal; mientras tanto el Pod aparece como `Init:0/1`, que es
mucho más claro que un `CrashLoopBackOff` en bucle.

`pacientes-fastapi-service` lleva además un `initContainer` que espera a
**Eureka**. No es simetría porque sí: los clientes Eureka de Spring reintentan
el registro indefinidamente, pero `py-eureka-client` lanza una excepción si el
servidor no responde al primer intento y no vuelve a intentarlo. Sin esa espera
el servicio Python puede quedar corriendo pero invisible para el Gateway, que
respondería `503` en `/api/usuarios` de forma permanente.

### `startupProbe` además de readiness y liveness
| Sonda | Pregunta | Si falla |
| :--- | :--- | :--- |
| `startupProbe` | ¿Ya terminó de arrancar? | Reinicia, pero da 5 minutos de margen |
| `readinessProbe` | ¿Puede recibir tráfico? | El `Service` deja de enviarle peticiones |
| `livenessProbe` | ¿Está vivo o colgado? | **Reinicia el Pod** |

Mientras el `startupProbe` no pase, las otras dos **no se ejecutan**. Es lo que
evita que una JVM lenta en Minikube sea reiniciada por el `livenessProbe` justo
antes de terminar de arrancar.

### `imagePullPolicy: Never`
Significa "no bajes nada de internet, usa la imagen que ya está en el nodo".
Por eso las imágenes se construyen dentro del Docker de Minikube
(`eval $(minikube docker-env)`, que es justo lo que hace `scripts/k8s-build.sh`).
Si falta una imagen, el error es explícito y fácil de diagnosticar.

### El realm de Keycloak no está en un YAML
`keycloak/realm-export.json` es un JSON grande que no se incrusta cómodamente
en un ConfigMap escrito a mano. El `kustomization.yaml` lo genera con
`configMapGenerator`, desde el **mismo archivo que monta Docker Compose**: una
sola fuente de verdad para los dos modos de despliegue.

Al nombre generado se le añade el hash del contenido
(`keycloak-realm-config-57k99mtb4f`) y la referencia del Deployment se reescribe
sola. Si editas el realm y vuelves a aplicar, Keycloak se reinicia con el realm
nuevo en lugar de quedarse con el viejo.

### Por qué Keycloak se consulta con `port-forward` y no por NodePort
Keycloak firma el claim `iss` de cada token con la URL pública configurada en
`03-keycloak.yaml` (`localhost:8080`), y los microservicios comparan ese valor
contra `SPRING_..._ISSUER_URI` de `01-config.yaml`. Si pides el token por otra
URL, el `iss` no coincide y **todo responde 401**. `kubectl port-forward` mantiene
las dos URLs alineadas.

Los NodePort (`30080` Keycloak, `30090` Gateway, `30876` Eureka) siguen
definidos como alternativa; para usarlos hay que ajustar `KC_HOSTNAME` y el
`ISSUER_URI` a la IP que devuelve `minikube ip`.

---

## Equivalencias con Docker Compose

| Docker Compose | Kubernetes |
| :--- | :--- |
| `services:` | `Deployment` + `Service` |
| `.env` + `environment:` | `ConfigMap` + `Secret` |
| `volumes:` | `PersistentVolumeClaim` |
| `depends_on: service_healthy` | `initContainers` |
| `healthcheck:` | `startupProbe` / `readinessProbe` / `livenessProbe` |
| `ports:` (publicar al host) | `NodePort` o `kubectl port-forward` |
| red `salud-network` | DNS interno del namespace |
| `docker compose up -d` | `kubectl apply -k .` |
| `docker compose down -v` | `kubectl delete namespace salud` |

---

## Nota sobre los secretos

`01-config.yaml` trae las contraseñas en claro dentro de un `Secret`. Es
deliberado: es un proyecto de clase y así se ve el objeto completo. Un `Secret`
de Kubernetes **no cifra nada**, solo codifica en base64. En un clúster real se
usan Sealed Secrets, External Secrets o el gestor de secretos del proveedor
cloud, y este archivo nunca se versionaría.
