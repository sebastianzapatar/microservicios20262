FROM ghcr.io/astral-sh/uv:python3.11-bookworm-slim

WORKDIR /app

# Instalar dependencias del sistema requeridas para compilar algunos paquetes si es necesario
RUN apt-get update && apt-get install -y gcc default-libmysqlclient-dev pkg-config && rm -rf /var/lib/apt/lists/*

# Copiamos pyproject.toml
COPY pyproject.toml .

# Sincronizamos las dependencias usando uv
RUN uv sync --no-dev

# Copiamos todo el directorio de la aplicación
COPY . .

EXPOSE 8083

# Ejecutar el servicio usando uv run
CMD ["uv", "run", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8083"]
