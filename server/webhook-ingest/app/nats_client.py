from nats.aio.client import Client as NATS
from app.config import settings
from app.logger import logger

class NATSClient:
    def __init__(self):
        self.nc = NATS()

    async def connect(self):
        await self.nc.connect(
            servers=settings.NATS_URL,
            token=settings.NATS_AUTH_TOKEN,
            verbose=True,
            pedantic=True,
            max_reconnect_attempts=-1,
            allow_reconnect=True,
            reconnect_time_wait=2,
        )
        self.js = self.nc.jetstream()
        logger.info(f"Connected to NATS at {self.nc.connected_url.netloc}")

    async def publish(self, subject: str, message: bytes):
        ack = await self.js.publish(subject, message)
        logger.info(f"Published message to {subject}: {ack}")

    async def close(self):
        await self.nc.close()
        logger.info("Closed connection to NATS")


nats_client = NATSClient()
