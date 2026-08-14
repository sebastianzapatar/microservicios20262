import asyncio
from fastapi import FastAPI
from contextlib import asynccontextmanager
import py_eureka_client.eureka_client as eureka_client

from app.core.config import settings
from app.db.mysql import engine, Base
from app.db.mongodb import connect_to_mongo, close_mongo_connection
from app.routers import auth, consultas


async def init_mysql(intentos: int = 15, espera: int = 4):
    """Crea las tablas en MySQL, reintentando mientras la base arranca.

    Docker Compose y Kubernetes levantan el contenedor de MySQL antes de que
    la base acepte conexiones, así que el primer intento suele fallar.
    """
    for intento in range(1, intentos + 1):
        try:
            async with engine.begin() as conn:
                await conn.run_sync(Base.metadata.create_all)
            print("Tablas de MySQL listas")
            return
        except Exception as e:
            print(f"MySQL no disponible (intento {intento}/{intentos}): {e}")
            if intento == intentos:
                raise
            await asyncio.sleep(espera)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Inicializar MySQL (Crear tablas si no existen)
    await init_mysql()

    # Inicializar MongoDB
    await connect_to_mongo()

    # Iniciar registro en Eureka (de forma asincrónica si es posible)
    # Py_eureka_client tiene un método asíncrono
    registrado = False
    try:
        await eureka_client.init_async(
            eureka_server=settings.EUREKA_SERVER,
            app_name="PACIENTES-FASTAPI-SERVICE",
            instance_host=settings.INSTANCE_HOST,
            instance_port=settings.PORT,
            health_check_url=f"http://{settings.INSTANCE_HOST}:{settings.PORT}/health",
        )
        registrado = True
        print(f"Registrado en Eureka como {settings.INSTANCE_HOST}:{settings.PORT}")
    except Exception as e:
        print(f"Error registrando en Eureka: {e}")

    yield

    # Limpieza en el shutdown: darse de baja de Eureka y cerrar Mongo
    if registrado:
        try:
            await eureka_client.stop_async()
        except Exception as e:
            print(f"Error al darse de baja de Eureka: {e}")
    await close_mongo_connection()


app = FastAPI(
    title=settings.APP_NAME,
    description="Servicio de Autenticación y Consulta de Historial Médico",
    lifespan=lifespan
)

app.include_router(auth.router)
app.include_router(consultas.router)

@app.get("/health")
async def health_check():
    return {"status": "up"}
