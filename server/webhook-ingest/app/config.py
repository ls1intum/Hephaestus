import logging
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    NATS_URL: str = "localhost"
    SECRET: str = ""
    
    class Config:
        env_file = ".env"

settings = Settings()

logging.basicConfig(level=logging.DEBUG, filename='app.log', filemode='a', format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)
logger.info(f"Settings: {settings.dict()}")