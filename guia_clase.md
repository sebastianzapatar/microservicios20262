# Guía de Clase: Creación de Microservicios desde Cero

Esta guía está diseñada para que tus estudiantes puedan recrear fácilmente los entornos de cada microservicio, ya sea inicializando el proyecto de Python con `uv` o generando los proyectos de Java con Spring Initializr.

---

## 1. Microservicio de Python (Usuarios y Consultas)
Con el nuevo gestor `uv`, ya no es necesario escribir manualmente un archivo `requirements.txt` ni lidiar con el `pyproject.toml`. 

Los estudiantes solo deben abrir su terminal y ejecutar estos pasos:

```bash
# 1. Inicializar el proyecto (crea la estructura básica y el pyproject.toml)
uv init pacientes-fastapi-service

# 2. Entrar a la carpeta
cd pacientes-fastapi-service

# 3. Instalar todas las dependencias necesarias de un solo golpe
uv add fastapi uvicorn "motor==3.7.1" pydantic aiomysql pyjwt "passlib[bcrypt]" "bcrypt==4.0.1" py-eureka-client sqlalchemy cryptography email-validator
```

Al hacer esto, `uv` automáticamente descarga las librerías a una velocidad increíble y actualiza el archivo de configuración por debajo.

> [!WARNING]
> **Dos versiones que hay que fijar sí o sí** (si no, el servicio no arranca):
> - `motor==3.7.1` — las versiones antiguas de `motor` (3.3.x) fallan con
>   `ImportError: cannot import name '_QUERY_OPTIONS'` cuando `uv` instala un
>   `pymongo` reciente.
> - `bcrypt==4.0.1` — `passlib 1.7.4` lee `bcrypt.__about__`, que desapareció
>   en `bcrypt >= 4.1`.
>
> Además, si el proyecto es una **aplicación** y no una librería, añade esto al
> `pyproject.toml` para que `uv sync` no intente empaquetarlo:
> ```toml
> [tool.uv]
> package = false
> ```

---

## 2. Microservicios en Java (Spring Initializr)
Para los servicios en Java, diles a los estudiantes que ingresen a **[start.spring.io](https://start.spring.io/)**, seleccionen **Gradle - Groovy**, **Java 25** (LTS) y **Spring Boot 4.1.x** (este proyecto usa Spring Boot `4.1.0` con Spring Cloud `2025.1.2`). Luego, deben agregar las siguientes dependencias exactas según el servicio que estén creando:

> [!NOTE]
> Los cuatro `build.gradle` fijan la versión con un **toolchain**, así que Gradle
> usa Java 25 aunque el estudiante tenga otro JDK por defecto:
> ```groovy
> java {
>     toolchain {
>         languageVersion = JavaLanguageVersion.of(25)
>     }
> }
> ```
> Si Gradle se queja de que no encuentra el toolchain, hay que instalar un JDK 25
> (`brew install openjdk@25` en Mac, o descargarlo de [Adoptium](https://adoptium.net/)).

### 📡 Eureka Server
En el buscador de "Dependencies" agregar únicamente:
- **Eureka Server** (`spring-cloud-starter-netflix-eureka-server`)
- **Spring Boot Actuator** (`spring-boot-starter-actuator`) *[para los health checks de Docker y Kubernetes]*

### 🌐 API Gateway
En el buscador de "Dependencies" agregar:
- **Gateway** — ⚠️ en Spring Cloud 2025.x el artefacto se llama `spring-cloud-starter-gateway-server-webflux` (antes era `spring-cloud-starter-gateway`)
- **Eureka Discovery Client** (`spring-cloud-starter-netflix-eureka-client`)
- **OAuth2 Resource Server** (`spring-boot-starter-oauth2-resource-server`)
- **Spring Boot Actuator** (`spring-boot-starter-actuator`)

### 🏥 Pacientes Service (PostgreSQL)
En el buscador de "Dependencies" agregar:
- **Spring Web** (`spring-boot-starter-web`)
- **Spring Data JPA** (`spring-boot-starter-data-jpa`)
- **PostgreSQL Driver** (`postgresql`)
- **Eureka Discovery Client** (`spring-cloud-starter-netflix-eureka-client`)
- **Validation** (`spring-boot-starter-validation`)
- **Lombok** (`lombok`)
- **Spring Boot Actuator** (`spring-boot-starter-actuator`)
- **OAuth2 Resource Server** (`spring-boot-starter-oauth2-resource-server`) *[Opcional si quieres que el microservicio valide tokens independientemente]*

### 📋 Historial Médico Service (MongoDB)
En el buscador de "Dependencies" agregar:
- **Spring Web** (`spring-boot-starter-web`)
- **Spring Data MongoDB** (`spring-boot-starter-data-mongodb`)
- **Eureka Discovery Client** (`spring-cloud-starter-netflix-eureka-client`)
- **Validation** (`spring-boot-starter-validation`)
- **Lombok** (`lombok`)
- **Spring Boot Actuator** (`spring-boot-starter-actuator`)
- **OAuth2 Resource Server** (`spring-boot-starter-oauth2-resource-server`) *[Opcional si quieres que el microservicio valide tokens independientemente]*

> [!TIP]
> **Consejo para la clase:** Recuérdales que la dependencia **"Eureka Discovery Client"** es la clave que permite que tanto el Gateway como los demás servicios se encuentren dinámicamente sin hardcodear IPs o puertos.

---

## 3. Tres trampas de Spring Boot 4 que cuestan una clase entera

### a) La propiedad de MongoDB cambió de nombre
En Spring Boot 4, `spring.data.mongodb.uri` **fue eliminada**. Lo peligroso es que
Spring **no lanza ningún error**: simplemente la ignora y se conecta a
`localhost:27017`, así que el servicio arranca "bien" y falla en la primera consulta.

```yaml
# ❌ Spring Boot 3 (ignorada en silencio por Spring Boot 4)
spring:
  data:
    mongodb:
      uri: mongodb://...

# ✅ Spring Boot 4
spring:
  mongodb:
    uri: mongodb://...
```
Como variable de entorno: `SPRING_MONGODB_URI` (no `SPRING_DATA_MONGODB_URI`).

### b) `./gradlew build` genera DOS jars
El plugin de Spring Boot produce el jar ejecutable **y** un `-plain.jar`. Si el
`Dockerfile` hace `COPY build/libs/*.jar app.jar`, la construcción falla porque
no se pueden copiar dos archivos a un destino único. La solución es pedir solo
el jar ejecutable:

```dockerfile
RUN ./gradlew bootJar --no-daemon
```

### c) El "issuer" de Keycloak debe coincidir exactamente
El token trae un claim `iss` con la URL **pública** de Keycloak
(`http://localhost:8080/realms/...`), pero desde dentro de un contenedor esa URL
no resuelve. Por eso hay que separar las dos URLs:

```bash
# Qué se valida (URL pública, la que ve el estudiante)
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://localhost:8080/realms/salud-system
# De dónde se bajan las llaves públicas (URL interna de la red Docker/K8s)
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://keycloak-salud:8080/realms/salud-system/protocol/openid-connect/certs
```
Si solo se usa `issuer-uri`, todo responde **401** sin explicación aparente.
