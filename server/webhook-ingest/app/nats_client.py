from nats.aio.client import Client as NATS
from app.config import settings
from app.logger import logger


class NATSClient:
    def __init__(self, nats_url: str, nats_auth_token: str):
        self.nc = NATS()
        self.nats_url = nats_url
        self.nats_auth_token = nats_auth_token

    async def connect(self):
        await self.nc.connect(
            servers=[self.nats_url], 
            token=self.nats_auth_token, 
            verbose=True, 
            pedantic=True,
            max_reconnect_attempts=-1,
            allow_reconnect=True,
            reconnect_time_wait=2,
        )
        self.js = self.nc.jetstream()
        logger.info(f"Connected to NATS at {self.nats_url}")

    async def publish(self, subject: str, message: bytes):
        ack = await self.js.publish(subject, message)
        logger.info(f"Published message to {subject}: {ack}")

    async def close(self):
        await self.nc.close()
        logger.info("Closed connection to NATS")


nats_client = NATSClient(settings.NATS_URL, settings.NATS_AUTH_TOKEN)
