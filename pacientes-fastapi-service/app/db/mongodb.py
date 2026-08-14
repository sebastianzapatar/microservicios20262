from motor.motor_asyncio import AsyncIOMotorClient
from app.core.config import settings

class MongoDBManager:
    client: AsyncIOMotorClient = None
    db = None

mongo_manager = MongoDBManager()

async def connect_to_mongo():
    mongo_manager.client = AsyncIOMotorClient(settings.MONGO_URI)
    mongo_manager.db = mongo_manager.client[settings.MONGO_DB]

async def close_mongo_connection():
    if mongo_manager.client:
        mongo_manager.client.close()

def get_mongo_db():
    return mongo_manager.db
