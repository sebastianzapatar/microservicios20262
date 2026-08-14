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

## ☸️ Desplegar en Kubernetes

La carpeta `k8s/` contiene los manifiestos para llevar el mismo sistema a un clúster de Minikube.
El paso a paso completo está en **`tutorial_kubernetes.md`** (Parte B).

```bash
minikube start --cpus=4 --memory=8192
eval $(minikube docker-env)          # ⚠️ imprescindible antes de construir
# ... construir las 5 imágenes ...
kubectl create configmap keycloak-realm-config --from-file=realm-export.json=keycloak/realm-export.json
kubectl apply -f k8s/
```

---

## 📚 Material Complementario para Clases

- **`index.html`**: Presentación interactiva con diagramas de arquitectura, flujo de conexión y el stack tecnológico exacto utilizado. ¡Solo ábrelo en cualquier navegador!
- **`kubernetes.html`**: Presentación de Kubernetes (teoría + demo del despliegue de este proyecto). Enlazada desde `index.html`.
- **`tutorial_kubernetes.md`**: Tutorial práctico. Parte A: fundamentos con Minikube. Parte B: desplegar este sistema completo en el clúster.
- **`guia_clase.md`**: Las dependencias exactas que los estudiantes deben instalar en `start.spring.io` o con `uv` para replicar este proyecto desde cero, más las trampas de Spring Boot 4.
- **`k8s_presentacion.md`**: Guion en texto de la presentación de Kubernetes.
- **`resumen_arquitectura.md`**: Detalle técnico del refactor y organización de código.
