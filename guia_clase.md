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
uv add fastapi uvicorn motor pydantic aiomysql pyjwt "passlib[bcrypt]" py-eureka-client sqlalchemy cryptography email-validator
```

Al hacer esto, `uv` automáticamente descarga las librerías a una velocidad increíble y actualiza el archivo de configuración por debajo.

---

## 2. Microservicios en Java (Spring Initializr)
Para los servicios en Java, diles a los estudiantes que ingresen a **[start.spring.io](https://start.spring.io/)**, seleccionen **Gradle - Groovy**, Java 17 o 21, y Spring Boot 3.x. Luego, deben agregar las siguientes dependencias exactas según el servicio que estén creando:

### 📡 Eureka Server
En el buscador de "Dependencies" agregar únicamente:
- **Eureka Server** (`spring-cloud-starter-netflix-eureka-server`)

### 🌐 API Gateway
En el buscador de "Dependencies" agregar:
- **Gateway** (`spring-cloud-starter-gateway`)
- **Eureka Discovery Client** (`spring-cloud-starter-netflix-eureka-client`)
- **OAuth2 Resource Server** (`spring-boot-starter-oauth2-resource-server`)

### 🏥 Pacientes Service (PostgreSQL)
En el buscador de "Dependencies" agregar:
- **Spring Web** (`spring-boot-starter-web`)
- **Spring Data JPA** (`spring-boot-starter-data-jpa`)
- **PostgreSQL Driver** (`postgresql`)
- **Eureka Discovery Client** (`spring-cloud-starter-netflix-eureka-client`)
- **OAuth2 Resource Server** (`spring-boot-starter-oauth2-resource-server`) *[Opcional si quieres que el microservicio valide tokens independientemente]*

### 📋 Historial Médico Service (MongoDB)
En el buscador de "Dependencies" agregar:
- **Spring Web** (`spring-boot-starter-web`)
- **Spring Data MongoDB** (`spring-boot-starter-data-mongodb`)
- **Eureka Discovery Client** (`spring-cloud-starter-netflix-eureka-client`)
- **OAuth2 Resource Server** (`spring-boot-starter-oauth2-resource-server`) *[Opcional si quieres que el microservicio valide tokens independientemente]*

> [!TIP]
> **Consejo para la clase:** Recuérdales que la dependencia **"Eureka Discovery Client"** es la clave que permite que tanto el Gateway como los demás servicios se encuentren dinámicamente sin hardcodear IPs o puertos.
