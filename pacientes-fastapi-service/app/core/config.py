import os

class Settings:
    # App
    APP_NAME: str = "Pacientes FastAPI Service"
    PORT: int = int(os.getenv("PACIENTES_FASTAPI_PORT", "8083"))

    # Eureka
    EUREKA_SERVER: str = os.getenv("EUREKA_SERVER", "http://eureka-server:8761/eureka/")
    # Host con el que este servicio se anuncia en Eureka. Debe ser resoluble por
    # el Gateway: en Docker Compose es el nombre del contenedor y en Kubernetes
    # se inyecta la IP del Pod (Downward API). Sin esto, py-eureka-client
    # registra el hostname interno y el Gateway responde 503.
    INSTANCE_HOST: str = os.getenv("INSTANCE_HOST", "pacientes-fastapi-service")

    # MySQL
    MYSQL_ROOT_PASSWORD: str = os.getenv("MYSQL_ROOT_PASSWORD", "root")
    MYSQL_DATABASE: str = os.getenv("MYSQL_DATABASE", "usuarios_db")
    MYSQL_PORT: str = os.getenv("MYSQL_PORT", "3306")
    MYSQL_HOST: str = os.getenv("MYSQL_HOST", "mysql-salud")
    MYSQL_URI: str = f"mysql+aiomysql://root:{MYSQL_ROOT_PASSWORD}@{MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}"

    # MongoDB
    MONGO_USER: str = os.getenv("MONGO_USER", "admin")
    MONGO_PASSWORD: str = os.getenv("MONGO_PASSWORD", "admin123")
    MONGO_DB: str = os.getenv("MONGO_DB", "historial_medico_db")
    MONGO_HOST: str = os.getenv("MONGO_HOST", "mongo-salud")
    MONGO_PORT: str = os.getenv("MONGO_PORT", "27017")
    MONGO_URI: str = f"mongodb://{MONGO_USER}:{MONGO_PASSWORD}@{MONGO_HOST}:{MONGO_PORT}/{MONGO_DB}?authSource=admin"

    # Kafka. El puerto por defecto es el INTERNO del contenedor (9092): este
    # servicio se conecta desde dentro de la red de Docker. Desde tu máquina el
    # broker se anuncia en localhost:9094.
    KAFKA_BOOTSTRAP_SERVERS: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka-salud:9092")

    # Security (JWT Propio para este microservicio)
    SECRET_KEY: str = os.getenv("SECRET_KEY", "super-secret-key-fastapi-mysql")
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 # 1 día

settings = Settings()
