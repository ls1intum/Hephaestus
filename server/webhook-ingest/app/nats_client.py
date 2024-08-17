import ssl

from nats.aio.client import Client as NATS
from app.config import settings
from app.logger import logger

class NATSClient:
    def __init__(self):
        self.nc = NATS()

    async def connect(self):
        ssl_context = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
        ssl_context.check_hostname = False
        ssl_context.verify_mode = ssl.CERT_NONE

        await self.nc.connect(
            servers=settings.NATS_URL,
            token=settings.NATS_AUTH_TOKEN,
            tls=ssl_context,
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
