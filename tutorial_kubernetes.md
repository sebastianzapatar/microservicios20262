# ☸️ Tutorial de Kubernetes con Minikube

Este tutorial tiene **dos partes**:

- **Parte A — Fundamentos (§1–§8):** primeros pasos con Kubernetes usando una imagen sencilla de Nginx. Empieza aquí si nunca has usado K8s.
- **Parte B — Desplegar este proyecto (§9–§16):** llevar los 5 microservicios del Sistema de Salud a un clúster real de Minikube.

> [!NOTE]
> Todos los comandos se ejecutan desde la **raíz del proyecto**
> (la carpeta que contiene `compose.yml` y `k8s/`).

---
---

# PARTE A — Fundamentos

## 1️⃣ Requisitos Previos

Antes de comenzar, necesitas tener instaladas tres herramientas clave en tu sistema:

1. **Docker**: El motor para correr los contenedores.
2. **kubectl**: La herramienta de línea de comandos para comunicarte con cualquier clúster de Kubernetes.
   - *Mac (Homebrew)*: `brew install kubectl`
   - *Windows (Winget)*: `winget install -e --id Kubernetes.kubectl`
3. **Minikube**: Una versión ligera de Kubernetes diseñada para ejecutarse localmente en tu computadora.
   - *Mac*: `brew install minikube`
   - *Windows*: `winget install minikube`

Verifica las instalaciones:
```bash
docker --version
kubectl version --client
minikube version
```

---

## 2️⃣ Iniciar el Clúster de Minikube

Minikube creará una máquina virtual o un contenedor Docker que actuará como tu nodo maestro y trabajador al mismo tiempo.

Abre tu terminal y ejecuta:
```bash
minikube start
```
*Este proceso puede tardar unos minutos la primera vez mientras descarga las imágenes necesarias.*

Para verificar que tu clúster está corriendo y que `kubectl` está conectado, ejecuta:
```bash
kubectl get nodes
```
Deberías ver un nodo llamado `minikube` con el estado `Ready`.

---

## 3️⃣ Crear tu primer Pod

Un **Pod** es la unidad más pequeña en Kubernetes. En lugar de ejecutar contenedores directamente, Kubernetes ejecuta Pods que envuelven a los contenedores.

Vamos a correr un Pod simple usando la imagen de `nginx` (un servidor web):

```bash
kubectl run mi-primer-pod --image=nginx
```

**Explicación del comando:**
- `run`: Le dice a Kubernetes que cree un Pod.
- `mi-primer-pod`: Es el nombre que le dimos a nuestro Pod.
- `--image=nginx`: Le indica que descargue y use la imagen oficial de Nginx desde Docker Hub.

---

## 4️⃣ Ver el estado del Pod

Para ver si nuestro Pod se creó correctamente, usamos el comando para listar los Pods:

```bash
kubectl get pods
```

Deberías ver una salida similar a esta:
```text
NAME            READY   STATUS    RESTARTS   AGE
mi-primer-pod   1/1     Running   0          30s
```
Si el estado dice `ContainerCreating`, espera unos segundos y vuelve a ejecutar el comando.

---

## 5️⃣ Interactuar con el Pod (Port Forwarding)

Como el Pod se está ejecutando dentro de Minikube, no podemos acceder a la página web directamente desde nuestro navegador. Para hacerlo, Kubernetes ofrece el "Port Forwarding" (redirección de puertos).

Ejecuta este comando y déjalo corriendo en la terminal:
```bash
kubectl port-forward pod/mi-primer-pod 8080:80
```
Ahora, abre tu navegador y visita: [http://localhost:8080](http://localhost:8080).
¡Verás la página de bienvenida de Nginx! (Presiona `Ctrl+C` en la terminal para detener la redirección cuando termines).

---

## 6️⃣ Los 3 comandos de diagnóstico que siempre vas a usar

Cuando algo no funciona, este es el orden en que se investiga:

```bash
# 1. ¿En qué estado está? (Running, Pending, CrashLoopBackOff, ImagePullBackOff...)
kubectl get pods

# 2. ¿Qué le pasó? Muestra los eventos: descarga de imagen, montaje de volúmenes,
#    fallos de probes. Aquí aparece el 90% de los errores de configuración.
kubectl describe pod mi-primer-pod

# 3. ¿Qué dice la aplicación? Muestra la salida estándar del contenedor.
kubectl logs mi-primer-pod
kubectl logs mi-primer-pod -f      # -f = seguir en vivo (como tail -f)
```

| Estado | Significado | Dónde mirar |
| :--- | :--- | :--- |
| `Pending` | No hay nodo/recursos, o falta un volumen o ConfigMap | `describe` |
| `ContainerCreating` | Descargando imagen o montando volúmenes | `describe` |
| `ImagePullBackOff` | No encuentra la imagen | `describe` |
| `CrashLoopBackOff` | El contenedor arranca y se muere en bucle | `logs` |
| `Running` pero `0/1` | Arrancó pero el readinessProbe falla | `describe` + `logs` |

---

## 7️⃣ Matar (Eliminar) el Pod

En Kubernetes, puedes eliminar recursos fácilmente por su nombre. Vamos a destruir el Pod que acabamos de crear:

```bash
kubectl delete pod mi-primer-pod
```

Verás el mensaje `pod "mi-primer-pod" deleted`. Si vuelves a ejecutar `kubectl get pods`, verás que la lista está vacía.

> [!TIP]
> **Resiliencia en K8s**: En la vida real (producción), no creamos Pods sueltos con `kubectl run`. Usamos **Deployments**. Si eliminas un Pod que pertenece a un Deployment, Kubernetes inmediatamente creará uno nuevo para reemplazarlo, asegurando alta disponibilidad.
>
> Pruébalo tú mismo en la §13 de este tutorial.

---

## 8️⃣ Apagar Minikube

Cuando termines de practicar y quieras liberar los recursos (RAM y CPU) de tu computadora, puedes detener Minikube:

```bash
minikube stop
```

Si deseas borrar el clúster por completo (esto borrará toda su configuración y datos internos), ejecuta:
```bash
minikube delete
```

---
---

# PARTE B — Desplegar el Sistema de Salud

Ya sabes crear un Pod suelto. Ahora vamos a desplegar el sistema completo:
**3 bases de datos + Keycloak + 5 microservicios**, definidos en la carpeta `k8s/`.

## 9️⃣ Qué hay dentro de la carpeta `k8s/`

Los archivos están numerados en el orden en que deben aplicarse:

| Archivo | Qué crea | Por qué va en ese orden |
| :--- | :--- | :--- |
| `01-config.yaml` | `ConfigMap` + `Secret` | Los demás Pods leen sus variables de aquí |
| `02-databases.yaml` | PostgreSQL, MongoDB, MySQL (+ sus `PVC` y `Service`) | Los microservicios necesitan sus bases listas |
| `02b-rabbitmq.yaml` | RabbitMQ (+ su `PVC` y `Service`) | Los microservicios se hablan por sus colas |
| `03-keycloak.yaml` | Keycloak (Identity Provider) | Emite los tokens JWT |
| `04-eureka.yaml` | Eureka Server | Todos se registran aquí al arrancar |
| `05-microservices.yaml` | Pacientes, Historial Médico y FastAPI | Se registran en Eureka |
| `06-gateway.yaml` | API Gateway | Descubre a los demás vía Eureka |

**Objetos de Kubernetes que vas a ver, y para qué sirve cada uno:**

- **Deployment** — describe el estado deseado ("quiero 1 réplica de esta imagen"). Si el Pod muere, lo recrea.
- **Service** — nombre DNS + IP estable para llegar a los Pods. Por eso un Pod puede conectarse a `postgres-salud:5432` sin saber su IP.
- **PersistentVolumeClaim (PVC)** — pide disco a Kubernetes. Sin él, los datos de las bases se perderían al reiniciar el Pod.
- **ConfigMap** — configuración no sensible (nombres de bases, URLs).
- **Secret** — credenciales (usuarios, contraseñas).
- **NodePort** — abre un puerto del nodo hacia afuera para poder entrar desde tu máquina.

---

## 🔟 Paso 1: Preparar Minikube y construir las imágenes

Los manifiestos usan `imagePullPolicy: Never`, lo que significa **"no descargues nada de internet, usa la imagen que ya está en el nodo"**. Por eso hay que construir las imágenes **dentro del Docker de Minikube**, no en el de tu Mac/PC.

```bash
# 1. Arrancar el clúster con recursos suficientes para 9 contenedores
minikube start --cpus=4 --memory=8192

# 2. ⚠️ EL PASO QUE MÁS SE OLVIDA:
#    apuntar tu terminal al daemon Docker de Minikube.
eval $(minikube docker-env)
```

> [!WARNING]
> `eval $(minikube docker-env)` **solo afecta a la terminal donde lo ejecutas**.
> Si abres una pestaña nueva, tendrás que repetirlo. Si no lo haces, las imágenes
> se construyen en tu Docker local, Minikube no las encuentra y los Pods quedan
> en `ImagePullBackOff`.
>
> En **Windows PowerShell** el comando es:
> `& minikube -p minikube docker-env --shell powershell | Invoke-Expression`

```bash
# 3. Construir las 5 imágenes (dentro de Minikube).
#    Los nombres deben coincidir EXACTAMENTE con los de los manifiestos.
docker build -t eureka-server:latest ./eureka-server
docker build -t gateway-service:latest ./gateway-service
docker build -t pacientes-service:latest ./pacientes-service
docker build -t historial-medico-service:latest ./historial-medico-service
docker build -t pacientes-fastapi-service:latest ./pacientes-fastapi-service

# 4. Verificar que las 5 imágenes están dentro de Minikube
docker images | grep -E "eureka|gateway|pacientes|historial"
```

*La primera construcción tarda varios minutos porque Gradle descarga todas las dependencias de Spring Boot.*

---

## 1️⃣1️⃣ Paso 2: Crear el ConfigMap del realm de Keycloak

El archivo `03-keycloak.yaml` monta la configuración del realm (usuarios, roles y client) desde un ConfigMap que **no se puede escribir cómodamente dentro de un YAML**, porque es un JSON grande. Se crea directamente desde el archivo:

```bash
kubectl create configmap keycloak-realm-config \
  --from-file=realm-export.json=keycloak/realm-export.json
```

Verifica que se creó:
```bash
kubectl get configmap keycloak-realm-config
```

> [!WARNING]
> Si te saltas este paso, el Pod de Keycloak se quedará para siempre en
> `ContainerCreating` con el error
> `configmap "keycloak-realm-config" not found` (visible con `kubectl describe pod`).

---

## 1️⃣2️⃣ Paso 3: Desplegar el sistema

Aplica los manifiestos en orden. `kubectl apply -f` acepta una carpeta completa y respeta el orden alfabético de los archivos, que es justo por lo que están numerados:

```bash
kubectl apply -f k8s/
```

Salida esperada:
```text
configmap/salud-config created
secret/salud-secrets created
persistentvolumeclaim/postgres-pvc created
deployment.apps/postgres-salud created
service/postgres-salud created
... (22 objetos en total)
```

Ahora observa cómo arranca todo, en vivo:
```bash
kubectl get pods -w
```
*(`-w` = watch. Presiona `Ctrl+C` para salir.)*

**Esto tarda entre 2 y 5 minutos.** Es normal ver `CrashLoopBackOff` o `0/1` durante el arranque: los microservicios Java intentan conectarse a bases de datos que todavía se están inicializando, se reinician y lo vuelven a intentar. Kubernetes hace exactamente eso: **reintentar hasta que el sistema converja al estado deseado**.

Estado final esperado (todos `Running` y `1/1`):
```text
NAME                                         READY   STATUS    RESTARTS   AGE
eureka-server-xxxxxxxxx-xxxxx                1/1     Running   0          3m
gateway-service-xxxxxxxxx-xxxxx              1/1     Running   1          3m
historial-medico-service-xxxxxxxxx-xxxxx     1/1     Running   1          3m
keycloak-salud-xxxxxxxxx-xxxxx               1/1     Running   0          3m
mongo-salud-xxxxxxxxx-xxxxx                  1/1     Running   0          3m
mysql-salud-xxxxxxxxx-xxxxx                  1/1     Running   0          3m
pacientes-fastapi-service-xxxxxxxxx-xxxxx    1/1     Running   0          3m
pacientes-service-xxxxxxxxx-xxxxx            1/1     Running   1          3m
postgres-salud-xxxxxxxxx-xxxxx               1/1     Running   0          3m
```

Revisa también los Services que se crearon:
```bash
kubectl get svc
```

---

## 1️⃣3️⃣ Paso 4: Ver el self-healing en acción

Este es el experimento que demuestra la diferencia entre `kubectl run` (§7) y un Deployment:

```bash
# Mata el Pod del servicio de pacientes
kubectl delete pod -l app=pacientes-service

# Míralo renacer inmediatamente (con un nombre nuevo)
kubectl get pods -l app=pacientes-service -w
```

El Deployment detecta que hay 0 réplicas cuando debería haber 1 y crea un Pod nuevo. **Nadie tuvo que intervenir.**

Ahora escálalo horizontalmente:
```bash
kubectl scale deployment pacientes-service --replicas=3
kubectl get pods -l app=pacientes-service
```

Las 3 réplicas se registran en Eureka y el Gateway empieza a balancear entre ellas (por eso las rutas usan `lb://PACIENTES-SERVICE`). Para volver atrás:
```bash
kubectl scale deployment pacientes-service --replicas=1
```

---

## 1️⃣4️⃣ Paso 5: Abrir los puertos hacia tu máquina

Los Pods viven dentro del clúster. Para hablar con ellos desde tu terminal, abre dos redirecciones. **Cada una ocupa su propia terminal**:

```bash
# Terminal 1 — Keycloak (emite los tokens)
kubectl port-forward svc/keycloak-salud 8080:8080

# Terminal 2 — API Gateway (puerta de entrada al sistema)
kubectl port-forward svc/gateway-service 8090:8090
```

Opcionalmente, para ver el panel de Eureka en el navegador:
```bash
# Terminal 3
kubectl port-forward svc/eureka-server 8761:8761
```
Y abre [http://localhost:8761](http://localhost:8761): deberías ver registrados `GATEWAY-SERVICE`, `PACIENTES-SERVICE`, `HISTORIAL-MEDICO-SERVICE` y `PACIENTES-FASTAPI-SERVICE`.

> [!IMPORTANT]
> **¿Por qué `port-forward` y no el NodePort?**
> Keycloak firma los tokens con una URL pública (el claim `iss`), configurada en
> `03-keycloak.yaml` como `localhost:8080`. Los microservicios comparan ese valor
> con `SPRING_..._ISSUER_URI` del `01-config.yaml`. Si pidieras el token por otra
> URL distinta a `localhost:8080`, el `iss` no coincidiría y **todo respondería 401**.
> Usar `port-forward` mantiene las dos URLs alineadas.
>
> Los NodePort (`30080`, `30090`, `30876`) siguen definidos como alternativa; para
> usarlos, ajusta `KC_HOSTNAME` e `ISSUER_URI` a la IP que devuelve `minikube ip`.

---

## 1️⃣5️⃣ Paso 6: Probar el sistema completo

### A. Flujo con Keycloak (servicios Java)

**1. Pedir un token a Keycloak:**
```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8080/realms/salud-system/protocol/openid-connect/token" \
  -d "client_id=microservicios-client" \
  -d "client_secret=microservicios-secret" \
  -d "username=medico" \
  -d "password=medico123" \
  -d "grant_type=password" | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

echo "${TOKEN:0:40}..."   # debe imprimir el inicio del token, no estar vacío
```

**2. Crear un paciente a través del Gateway:**
```bash
curl -X POST "http://localhost:8090/api/pacientes" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Ana",
    "apellido": "Gomez",
    "email": "ana@salud.com",
    "telefono": "3001234567",
    "fechaNacimiento": "1990-05-20",
    "direccion": "Calle 10 #20-30",
    "tipoDocumento": "CC",
    "numeroDocumento": "1001"
  }'
```

**3. Crear un historial médico (esto valida el paciente llamando al otro microservicio):**
```bash
curl -X POST "http://localhost:8090/api/historiales" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "pacienteId": 1,
    "diagnostico": "Gripe comun",
    "tratamiento": "Reposo e hidratacion",
    "medico": "Dr. Perez",
    "tipoConsulta": "General",
    "notas": "Control en 5 dias"
  }'
```

**4. Ver la comunicación inter-servicio (Pacientes ➜ Eureka ➜ Historial):**
```bash
curl "http://localhost:8090/api/pacientes/1/historial" -H "Authorization: Bearer $TOKEN"
```
La respuesta combina los datos del paciente (PostgreSQL) con su historial (MongoDB), **obtenidos por dos microservicios distintos**.

**5. Comprobar que la seguridad funciona (sin token debe dar 401):**
```bash
curl -i "http://localhost:8090/api/pacientes"
```

### B. Flujo con el JWT propio de FastAPI (servicio Python)

```bash
# 1. Registrar un usuario (queda en MySQL)
curl -X POST "http://localhost:8090/api/usuarios/registro" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Estudiante","email":"estudiante@salud.com","password":"123"}'

# 2. Login -> devuelve un JWT propio de FastAPI (HS256), NO de Keycloak
FASTAPI_TOKEN=$(curl -s -X POST "http://localhost:8090/api/usuarios/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"estudiante@salud.com","password":"123"}' \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

# 3. Consultar el historial propio (lee MongoDB usando el id del usuario)
curl "http://localhost:8090/api/consultas/historial" \
  -H "Authorization: Bearer $FASTAPI_TOKEN"
```

> [!NOTE]
> **¿Por qué hay dos sistemas de tokens?** Es intencional y didáctico:
> los servicios Java delegan la identidad en **Keycloak** (OAuth2/OIDC, lo
> recomendable en producción), mientras que FastAPI implementa su **propia**
> autenticación con JWT para mostrar el contraste. Por eso el Gateway deja pasar
> `/api/consultas/**` sin validar: es el propio servicio Python quien exige y
> verifica su token.

---

## 1️⃣6️⃣ Paso 7: Limpiar

```bash
# Borrar solo la aplicación (el clúster sigue vivo)
kubectl delete -f k8s/
kubectl delete configmap keycloak-realm-config

# Los PVC no se borran con lo anterior: hay que hacerlo explícitamente
# (Kubernetes los conserva a propósito para no perder datos por accidente)
kubectl delete pvc --all

# Apagar el clúster
minikube stop

# O eliminarlo por completo
minikube delete
```

---

## 🚑 Solución de problemas frecuentes

| Síntoma | Causa | Solución |
| :--- | :--- | :--- |
| `ImagePullBackOff` | Olvidaste `eval $(minikube docker-env)` antes de construir | Ejecútalo y reconstruye las 5 imágenes |
| Keycloak en `ContainerCreating` | Falta el ConfigMap del realm | Ver §11 |
| Todo responde `401 Unauthorized` | El `iss` del token no coincide con `ISSUER_URI` | Pide el token por `localhost:8080` con `port-forward` (§14) |
| Gateway responde `503 Service Unavailable` | El servicio aún no se registró en Eureka | Espera ~40 s y revisa el panel de Eureka |
| `503` justo después de reiniciar o escalar un servicio | El Gateway tiene en caché la instancia vieja (Eureka refresca cada ~30 s) | Espera y reintenta; es el comportamiento normal de Eureka, no un error |
| `CrashLoopBackOff` en un servicio Java | La base de datos aún no estaba lista | Suele resolverse solo; si no, `kubectl logs <pod>` |
| `Pending` en los Pods de bases de datos | El PVC no consigue disco | `kubectl get pvc` y `kubectl describe pvc <nombre>` |
| Pods reiniciándose sin parar (`OOMKilled`) | Minikube sin RAM suficiente | `minikube delete && minikube start --cpus=4 --memory=8192` |

**Comandos de rescate:**
```bash
kubectl get pods                        # estado general
kubectl describe pod <nombre-del-pod>   # eventos: imágenes, volúmenes, probes
kubectl logs <nombre-del-pod>           # salida de la aplicación
kubectl logs <nombre-del-pod> --previous  # logs del contenedor que YA murió
kubectl get events --sort-by=.metadata.creationTimestamp | tail -20
kubectl exec -it <nombre-del-pod> -- sh   # entrar al contenedor
```

---

## 📊 Docker Compose vs Kubernetes: el mismo sistema, dos mundos

Ya desplegaste este proyecto de las dos formas. Esta es la traducción mental:

| Docker Compose | Kubernetes | Nota |
| :--- | :--- | :--- |
| `services:` en `compose.yml` | `Deployment` + `Service` | En K8s se separan "qué corre" de "cómo se le llega" |
| `container_name` | `Service` (nombre DNS) | Igual: `postgres-salud` resuelve en ambos |
| `ports: "8090:8090"` | `NodePort` o `port-forward` | K8s no publica puertos por defecto |
| `environment:` | `ConfigMap` + `Secret` | La configuración se separa de la imagen |
| `volumes:` | `PersistentVolumeClaim` | K8s pide el disco de forma declarativa |
| `depends_on` + `healthcheck` | `readinessProbe` / `livenessProbe` | K8s reintenta indefinidamente |
| `restart: unless-stopped` | Nativo del Deployment | El self-healing viene de fábrica |
| `docker compose up -d` | `kubectl apply -f k8s/` | Ambos son declarativos |
| `docker compose logs -f x` | `kubectl logs -f <pod>` | |
| Escalar: manual | `kubectl scale --replicas=3` | Y con HPA, automático |

---

¡Felicidades! Pasaste de un Pod de Nginx a un sistema de 9 contenedores con
descubrimiento de servicios, autenticación centralizada, persistencia y
autorreparación corriendo sobre Kubernetes.
