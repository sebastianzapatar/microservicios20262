# Walkthrough: Refactorización de FastAPI, MySQL y Migración a `uv`

He completado todos los cambios solicitados para mejorar la arquitectura, aplicar buenas prácticas, soportar la base de datos MySQL para usuarios y modernizar el proyecto en Python utilizando `uv`.

## Resumen de Cambios

### 1. Infraestructura y Gateway
- Se agregó un contenedor `mysql:8.0` en [compose.yml](file:///Users/sebastianzapata/Documents/implementacion20262/microservicios/compose.yml).
- Se modificó el archivo `env.template` para incluir las credenciales por defecto de MySQL.
- En el **Spring Cloud Gateway**, se añadió una ruta para el microservicio de FastAPI y se actualizaron los [SecurityConfig.java](file:///Users/sebastianzapata/Documents/implementacion20262/microservicios/gateway-service/src/main/java/com/salud/gateway/config/SecurityConfig.java) para permitir acceso libre (`permitAll`) a los endpoints de login y registro de FastAPI.

### 2. Reestructuración y Migración de FastAPI a `uv`
El proyecto `pacientes-fastapi-service` pasó de tener todo en `main.py` a una estructura modular robusta y ahora es gestionado por **uv**, el empaquetador ultrarrápido escrito en Rust.
- **`pyproject.toml`**: Reemplazó por completo a `requirements.txt`.
- **`Dockerfile`**: Utiliza la imagen `ghcr.io/astral-sh/uv:python3.11-bookworm-slim` y ejecuta `uv sync` y `uv run` para levantar Uvicorn. Esto disminuye significativamente los tiempos de build en Docker.

Estructura modular resultante:
```
app/
├── core/
│   ├── config.py     # Variables de entorno y configuración
│   └── security.py   # Utilidades de JWT y hashing de contraseñas (passlib, bcrypt)
├── db/
│   ├── mysql.py      # Conexión asíncrona a MySQL con SQLAlchemy
│   └── mongodb.py    # Conexión a MongoDB con Motor
├── models/
│   ├── user.py       # Modelo SQLAlchemy de Usuario y esquemas Pydantic
│   └── history.py    # Esquemas Pydantic para el historial
├── routers/
│   ├── auth.py       # Endpoints /api/usuarios/registro y /login
│   └── consultas.py  # Endpoint protegido /api/consultas/historial
└── main.py           # Inicialización (Eureka, DBs, Routers)
```

**Flujo de Seguridad Propio:**
- FastAPI gestiona su propia tabla `usuarios` en MySQL.
- El registro (`POST /api/usuarios/registro`) guarda la contraseña encriptada.
- El login (`POST /api/usuarios/login`) genera un JWT propio.
- Para consultar el historial (`GET /api/consultas/historial`), FastAPI verifica su propio JWT y usa el `user_id` extraído para buscar el historial en MongoDB.

### 3. Presentación (index.html)
Se actualizó [index.html](file:///Users/sebastianzapata/Documents/implementacion20262/microservicios/index.html) para reflejar la nueva arquitectura y facilitar la enseñanza a tus estudiantes:
- **Etiquetas de Librerías:** Cada uno de los 5 servicios cuenta ahora con insignias visuales (badges) que muestran explícitamente sus dependencias clave (ej: `fastapi`, `data-jpa`, `postgresql`, `motor`, `uv`, etc.).
- Ahora indica **5 Servicios** y **3 Bases de Datos**.

## Cómo probar esto

Levanta toda la infraestructura (incluyendo la nueva base de datos y la recarga del gateway):
```bash
cd /Users/sebastianzapata/Documents/implementacion20262/microservicios/
docker-compose down
docker-compose up --build -d
```

### Probar desde el Gateway (Puerto 8090)

1. **Crear usuario en MySQL:**
```bash
curl -X POST "http://localhost:8090/api/usuarios/registro" \
-H "Content-Type: application/json" \
-d '{"nombre":"Juan","email":"juan@email.com","password":"123"}'
```

2. **Hacer Login (Obtener Token):**
```bash
curl -X POST "http://localhost:8090/api/usuarios/login" \
-H "Content-Type: application/json" \
-d '{"email":"juan@email.com","password":"123"}'
```

3. **Consultar su propio historial:**
(Copia el `access_token` del paso 2 y pégalo donde dice `AQUI_TU_TOKEN`)
```bash
curl -X GET "http://localhost:8090/api/consultas/historial" \
-H "Authorization: Bearer AQUI_TU_TOKEN"
```
