FROM ghcr.io/astral-sh/uv:python3.11-bookworm-slim

WORKDIR /app

# Instalar dependencias del sistema requeridas para compilar algunos paquetes si es necesario
RUN apt-get update && apt-get install -y gcc default-libmysqlclient-dev pkg-config && rm -rf /var/lib/apt/lists/*

# Copiamos primero pyproject.toml para aprovechar la caché de Docker:
# mientras no cambien las dependencias, esta capa no se reconstruye.
COPY pyproject.toml .

# Sincronizamos las dependencias usando uv
RUN uv sync --no-dev

# Copiamos el código de la aplicación
COPY app ./app

EXPOSE 8083

# Ejecutar el servicio usando uv run (--no-sync: las dependencias ya están instaladas)
CMD ["uv", "run", "--no-sync", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8083"]
