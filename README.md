# Sistema de Salud - Arquitectura de Microservicios 🏥

Este proyecto es un sistema de salud construido con una **Arquitectura de Microservicios** moderna, combinando tecnologías de la JVM (Java/Spring Boot) y Python (FastAPI). Está diseñado como un proyecto educativo para enseñar cómo orquestar múltiples servicios, bases de datos y sistemas de seguridad.

---

## 🏗️ Arquitectura del Sistema

El ecosistema está compuesto por **5 microservicios**, **3 bases de datos** y un **Identity Provider** (Keycloak). Todos los servicios se descubren de forma dinámica gracias a Eureka y todas las peticiones externas pasan por un único punto de entrada: el API Gateway.

### Los 5 Servicios
1. **📡 Eureka Server (`puerto 8761`)**: Registro y descubrimiento automático de servicios.
2. **🌐 API Gateway (`puerto 8090`)**: Enrutador central y validador primario de seguridad. Todas las peticiones al sistema deben entrar por aquí.
3. **🏥 Pacientes Service (`puerto 8081`)**: Desarrollado en Java (Spring Boot + Data JPA). Realiza el CRUD de pacientes usando **PostgreSQL**.
4. **📋 Historial Médico (`puerto 8082`)**: Desarrollado en Java (Spring Boot + MongoDB). Almacena los historiales clínicos flexibles en **MongoDB**.
5. **🐍 Usuarios FastAPI (`puerto 8083`)**: Desarrollado en Python. Gestiona el registro y login emitiendo tokens JWT propios, y almacena a los usuarios en **MySQL**. Además, permite consultar el historial médico en MongoDB.

### Bases de Datos y Seguridad
- **PostgreSQL**: Datos transaccionales estructurados (Pacientes).
- **MongoDB**: Documentos no relacionales flexibles (Historiales Médicos).
- **MySQL**: Credenciales y registros de usuarios (FastAPI).
- **Keycloak (`puerto 8080`)**: Servidor centralizado para emisión de tokens OAuth2/OIDC.

---

## 🚀 Cómo Correr el Proyecto

El proyecto está completamente contenerizado usando Docker Compose. Esto significa que con un solo comando puedes levantar las 3 bases de datos, Keycloak y la red interna.

### Requisitos Previos
- Docker y Docker Compose instalados.

### Paso a paso

1. **Clonar o descargar** este repositorio y abrir una terminal en la carpeta principal.
2. **Crear el archivo de variables de entorno** a partir de la plantilla y completar los valores vacíos (usuarios y contraseñas):
   ```bash
   cp env.template .env
   ```
3. **Construir y levantar todo el sistema** (3 bases de datos + Keycloak + los 5 microservicios):
   ```bash
   docker compose up --build -d
   ```
   *La primera vez tarda varios minutos: Gradle descarga las dependencias de Spring Boot dentro de cada imagen.*
4. **Verificar que todo esté corriendo**:
   ```bash
   docker compose ps
   ```
   - Abre `http://localhost:8761` para ver el panel de Eureka.
   - Deberías ver registrados `GATEWAY-SERVICE`, `PACIENTES-SERVICE`, `HISTORIAL-MEDICO-SERVICE` y `PACIENTES-FASTAPI-SERVICE`.
   - El panel de administración de Keycloak está en `http://localhost:8080`.

---

## 🧪 Cómo Probar el Flujo Completo

Todo entra por el Gateway (**puerto 8090**). El sistema tiene **dos esquemas de autenticación**, a propósito:

| Endpoints | Quién emite el token | Quién lo valida |
| :--- | :--- | :--- |
| `/api/pacientes/**`, `/api/historiales/**` | **Keycloak** (OAuth2 / OIDC) | El Gateway **y** cada microservicio |
| `/api/usuarios/**`, `/api/consultas/**` | **FastAPI** (JWT propio, HS256) | Solo el microservicio Python |

### A. Flujo con Keycloak (servicios Java)

**1. Obtener un token** (usuarios de prueba: `medico/medico123` o `admin/admin123`):
```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8080/realms/salud-system/protocol/openid-connect/token" \
  -d "client_id=microservicios-client" \
  -d "client_secret=microservicios-secret" \
  -d "username=medico" -d "password=medico123" \
  -d "grant_type=password" | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
```

**2. Crear un paciente** (PostgreSQL):
```bash
curl -X POST "http://localhost:8090/api/pacientes" \
-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
-d '{"nombre":"Ana","apellido":"Gomez","email":"ana@salud.com","telefono":"3001234567",
     "fechaNacimiento":"1990-05-20","direccion":"Calle 10","tipoDocumento":"CC","numeroDocumento":"1001"}'
```

**3. Crear un historial médico** (MongoDB). Este endpoint valida que el paciente exista **llamando al otro microservicio** vía Eureka:
```bash
curl -X POST "http://localhost:8090/api/historiales" \
-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
-d '{"pacienteId":1,"diagnostico":"Gripe comun","tratamiento":"Reposo",
     "medico":"Dr. Perez","tipoConsulta":"General","notas":"Control en 5 dias"}'
```

**4. Ver la comunicación inter-servicio en acción** (Pacientes ➜ Eureka ➜ Historial):
```bash
curl "http://localhost:8090/api/pacientes/1/historial" -H "Authorization: Bearer $TOKEN"
```
La respuesta combina datos de **PostgreSQL** y de **MongoDB**, obtenidos por dos microservicios distintos.

### B. Flujo con el JWT propio de FastAPI

**1. Crear un nuevo usuario (FastAPI + MySQL)**
```bash
curl -X POST "http://localhost:8090/api/usuarios/registro" \
-H "Content-Type: application/json" \
-d '{"nombre":"Estudiante","email":"estudiante@salud.com","password":"123"}'
```

**2. Iniciar Sesión para obtener el JWT**
```bash
curl -X POST "http://localhost:8090/api/usuarios/login" \
-H "Content-Type: application/json" \
-d '{"email":"estudiante@salud.com","password":"123"}'
```
*(Copia el valor del `access_token` que devuelve este comando).*

**3. Consultar tu información (Requiere Token)**
```bash
curl -X GET "http://localhost:8090/api/consultas/historial" \
-H "Authorization: Bearer AQUI_TU_TOKEN_COPIADO"
```

### Apagar el sistema
```bash
docker compose down          # detiene los contenedores
docker compose down -v       # además borra los volúmenes (datos de las 3 bases)
```

---

## ☸️ Correr el proyecto con Kubernetes

El mismo sistema (3 bases de datos + Keycloak + los 5 microservicios) corre en
un clúster local de Minikube. La carpeta `k8s/` tiene los manifiestos, con su
propio [`k8s/README.md`](k8s/README.md) explicando cada decisión de diseño.

### 0. Requisitos previos

| Herramienta | Comprobación | Instalación |
| :--- | :--- | :--- |
| Docker **corriendo** | `docker info` | Docker Desktop abierto (no basta con tenerlo instalado) |
| kubectl | `kubectl version --client` | `brew install kubectl` · `winget install -e --id Kubernetes.kubectl` |
| Minikube | `minikube version` | `brew install minikube` · `winget install minikube` |

No hace falta instalar kustomize: viene dentro de `kubectl`.

> [!IMPORTANT]
> **Recursos del clúster.** El sistema levanta 9 contenedores, 5 de ellos JVMs.
> Minikube fija CPU y RAM **al crear** el clúster y no se pueden cambiar después
> con un simple `minikube start`. Si ya tienes un clúster con menos recursos,
> hay que recrearlo:
> ```bash
> minikube delete
> minikube start --cpus=4 --memory=8192
> ```
> Para saber qué tiene el tuyo realmente:
> ```bash
> docker inspect minikube --format '{{.HostConfig.NanoCpus}} {{.HostConfig.Memory}}'
> ```
> Ojo: `kubectl get node` **no** sirve para esto. El kubelet anuncia las CPUs de
> la máquina anfitriona, no el límite real del contenedor, así que el scheduler
> acepta todos los Pods sin avisar y luego todo va ahogado.

### 1. El camino rápido (tres comandos)

Desde la **raíz del proyecto**:

```bash
./scripts/k8s-deploy.sh        # arranca Minikube, construye las 5 imágenes y despliega
./scripts/k8s-port-forward.sh  # en otra terminal: abre Keycloak, Gateway y Eureka
./scripts/k8s-test.sh          # en una tercera: prueba el flujo completo de punta a punta
```

La primera vez, `k8s-deploy.sh` tarda bastante: cada servicio Java descarga
Gradle y todas las dependencias de Spring Boot dentro de su imagen. Las
siguientes veces es mucho más rápido gracias a la caché de Docker.

Para borrarlo todo:

```bash
./scripts/k8s-clean.sh         # borra el namespace 'salud' (incluidos los volúmenes)
./scripts/k8s-clean.sh --todo  # además destruye el clúster de Minikube
```

### 2. El camino manual (lo que hacen esos scripts)

**Paso 1 — Arrancar el clúster**
```bash
minikube start --cpus=4 --memory=8192
```

**Paso 2 — Construir las imágenes DENTRO de Minikube**

Los manifiestos usan `imagePullPolicy: Never`: el clúster no descarga nada de
internet, usa solo las imágenes que ya existen en su propio nodo. Por eso hay
que apuntar el cliente Docker al daemon de Minikube **antes** de construir.

```bash
eval $(minikube docker-env)          # ⚠️ EL PASO QUE MÁS SE OLVIDA

docker build -t eureka-server:latest ./eureka-server
docker build -t gateway-service:latest ./gateway-service
docker build -t pacientes-service:latest ./pacientes-service
docker build -t historial-medico-service:latest ./historial-medico-service
docker build -t pacientes-fastapi-service:latest ./pacientes-fastapi-service
```

> [!WARNING]
> `eval $(minikube docker-env)` **solo afecta a la terminal donde lo ejecutas**.
> Si abres una pestaña nueva hay que repetirlo. En PowerShell el comando es:
> `& minikube -p minikube docker-env --shell powershell | Invoke-Expression`
>
> `./scripts/k8s-build.sh` hace este paso entero y no puede olvidársete, porque
> ejecuta el `eval` dentro del propio script.

**Paso 3 — Desplegar**
```bash
kubectl apply -k .    # ← desde la RAÍZ del proyecto, con -k (no `-f k8s/`)
```

> [!NOTE]
> Se usa `apply -k .` (kustomize, incluido en kubectl) y no `apply -f k8s/`.
> Kustomize genera el ConfigMap del realm de Keycloak desde
> `keycloak/realm-export.json` —el mismo archivo que usa Docker Compose— y mete
> los 25 objetos en el namespace `salud`. Ya no hay que crear ningún ConfigMap
> a mano.

**Paso 4 — Trabajar dentro del namespace `salud`**
```bash
kubectl config set-context --current --namespace=salud
kubectl get pods -w
```
Sin esto, `kubectl get pods` mira el namespace `default` y parece que no se ha
desplegado nada.

Los Pods pasan por `Init:0/1` (el `initContainer` espera a su base de datos),
`Running 0/1` (la JVM arrancando) y por fin `Running 1/1`. Todo eso es normal.

**Paso 5 — Abrir los puertos hacia tu máquina**

Cada redirección ocupa su propia terminal:
```bash
kubectl port-forward svc/keycloak-salud 8080:8080    # emite los tokens
kubectl port-forward svc/gateway-service 8090:8090   # puerta de entrada
kubectl port-forward svc/eureka-server 8761:8761     # panel de Eureka (opcional)
```

> [!IMPORTANT]
> **¿Por qué `port-forward` y no los NodePort?** Keycloak firma el claim `iss`
> de cada token con la URL pública `localhost:8080`, y los microservicios
> comparan ese valor contra el `ISSUER_URI` del ConfigMap. Si pides el token por
> cualquier otra URL, el `iss` no coincide y **todo responde 401**.

**Paso 6 — Probar**

Los mismos `curl` de la sección "🧪 Cómo Probar el Flujo Completo" funcionan tal
cual, porque el Gateway y Keycloak están en los mismos puertos que con Docker
Compose. O ejecuta `./scripts/k8s-test.sh`, que los hace todos y se detiene con
un mensaje claro en el primer fallo.

**Paso 7 — Apagar**
```bash
kubectl delete namespace salud       # borra los 25 objetos y los volúmenes
minikube stop                        # apaga el clúster (conserva las imágenes)
```

### 3. Problemas frecuentes

| Síntoma | Causa | Solución |
| :--- | :--- | :--- |
| `Unable to access jarfile .../gradle-wrapper.jar` al construir | Falta el `gradle-wrapper.jar` del servicio | Genéralo: `cd <servicio> && gradle wrapper --gradle-version 9.7.0` |
| `ErrImageNeverPull` / `ErrImageNeverPullPolicy` | Construiste las imágenes en tu Docker, no en el de Minikube | `eval $(minikube docker-env)` y reconstruye, o usa `./scripts/k8s-build.sh` |
| `kubectl get pods` no muestra nada | Estás en el namespace `default` | `kubectl config set-context --current --namespace=salud` |
| `provided port is already allocated` al aplicar | Otro Service ya usa el NodePort 30080/30090/30876 | `kubectl get svc -A` para ver quién, y bórralo o cámbiale el puerto |
| `port-forward` avisa de que 8090 o 8761 están ocupados | El stack de Docker Compose de este mismo proyecto sigue levantado | `docker compose down`, o redirige a otros puertos locales: `kubectl port-forward svc/gateway-service 18090:8090` y luego `GATEWAY=http://localhost:18090 ./scripts/k8s-test.sh`. **Keycloak sí debe quedarse en 8080**, o el claim `iss` no coincide y todo da 401 |
| `error: unable to recognize "k8s/kustomization.yaml"` | Usaste `apply -f k8s/` | El comando es `kubectl apply -k .` desde la raíz |
| Todo responde `401 Unauthorized` | El `iss` del token no coincide con el `ISSUER_URI` | Pide el token por `localhost:8080` usando `port-forward` |
| `503` en `/api/...` justo tras reiniciar o escalar | El Gateway tiene en caché la instancia vieja (Eureka refresca cada ~30 s) | Espera y reintenta; es el comportamiento normal de Eureka |
| Un Pod lleva mucho en `Init:0/1` | Su base de datos aún no acepta conexiones | `kubectl logs <pod> -c esperar-postgres` (o `esperar-mongo`, `esperar-mysql`, `esperar-eureka`) |
| Pods reiniciándose sin parar (`OOMKilled`) | Minikube sin RAM suficiente | Recrear el clúster con `--memory=8192` |

**Comandos de diagnóstico:**
```bash
kubectl get pods                          # estado general
kubectl describe pod <pod>                # eventos: imágenes, volúmenes, probes
kubectl logs <pod>                        # salida de la aplicación
kubectl logs <pod> --previous             # logs del contenedor que YA murió
kubectl logs <pod> -c esperar-postgres    # log del initContainer
```

El paso a paso completo, con las explicaciones de cada objeto de Kubernetes,
está en **`tutorial_kubernetes.md`** (Parte B).

---

## 📚 Material Complementario para Clases

- **`index.html`**: Presentación interactiva con diagramas de arquitectura, flujo de conexión y el stack tecnológico exacto utilizado. ¡Solo ábrelo en cualquier navegador!
- **`kubernetes.html`**: Presentación de Kubernetes (teoría + demo del despliegue de este proyecto). Enlazada desde `index.html`.
- **`tutorial_kubernetes.md`**: Tutorial práctico. Parte A: fundamentos con Minikube. Parte B: desplegar este sistema completo en el clúster.
- **`guia_clase.md`**: Las dependencias exactas que los estudiantes deben instalar en `start.spring.io` o con `uv` para replicar este proyecto desde cero, más las trampas de Spring Boot 4.
- **`k8s_presentacion.md`**: Guion en texto de la presentación de Kubernetes.
- **`k8s/README.md`**: Qué hay en cada manifiesto y por qué (`Recreate`, `initContainers`, `startupProbe`, kustomize), más la tabla de equivalencias Docker Compose ⇄ Kubernetes.
- **`resumen_arquitectura.md`**: Detalle técnico del refactor y organización de código.
