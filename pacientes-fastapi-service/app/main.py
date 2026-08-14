import asyncio
from fastapi import FastAPI
from contextlib import asynccontextmanager
import py_eureka_client.eureka_client as eureka_client

from app.core.config import settings
from app.db.mysql import engine, Base
from app.db.mongodb import connect_to_mongo, close_mongo_connection
from app.routers import auth, consultas

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Inicializar MySQL (Crear tablas si no existen)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    
    # Inicializar MongoDB
    await connect_to_mongo()
    
    # Iniciar registro en Eureka (de forma asincrónica si es posible)
    # Py_eureka_client tiene un método asíncrono
    try:
        await eureka_client.init_async(
            eureka_server=settings.EUREKA_SERVER,
            app_name="PACIENTES-FASTAPI-SERVICE",
            instance_port=settings.PORT
        )
        print("Registrado en Eureka Server")
    except Exception as e:
        print(f"Error registrando en Eureka: {e}")

    yield
    
    # Limpieza en el shutdown
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
