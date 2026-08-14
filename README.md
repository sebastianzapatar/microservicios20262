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
2. **Construir y levantar la infraestructura**:
   ```bash
   docker-compose up --build -d
   ```
3. **Verificar que todo esté corriendo**:
   - Puedes abrir `http://localhost:8761` para ver el panel de Eureka.
   - Deberías ver registrados `GATEWAY-SERVICE`, `PACIENTES-SERVICE`, `HISTORIAL-MEDICO-SERVICE` y `PACIENTES-FASTAPI-SERVICE`.

---

## 🧪 Cómo Probar el Flujo Completo

Como todo pasa por el Gateway (8090), las pruebas se hacen apuntando a ese puerto. A continuación, el flujo para registrar un usuario, iniciar sesión y consultar el historial en Python:

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

---

## 📚 Material Complementario para Clases

- **`index.html`**: Presentación interactiva con diagramas de arquitectura, flujo de conexión y el stack tecnológico exacto utilizado. ¡Solo ábrelo en cualquier navegador!
- **`guia_clase.md`**: Un resumen de las dependencias exactas que los estudiantes deben instalar en `start.spring.io` o en su terminal con `uv` para replicar este proyecto desde cero.
- **`resumen_arquitectura.md`**: Detalle técnico del refactor y organización de código.
