from nats.aio.client import Client as NATS
from app.config import settings
from app.logger import logger

class NATSClient:
    def __init__(self):
        self.nc = NATS()

    async def connect(self):
        async def error_cb(e):
            logger.error(f'There was an error: {e}')

        async def disconnected_cb():
            logger.info('NATS got disconnected!')
        
        async def reconnected_cb():
            logger.info(f'NATS got reconnected to {self.nc.connected_url.netloc}')

        async def closed_cb():
            logger.info('NATS connection is closed')

        await self.nc.connect(
            servers=settings.NATS_URL,
            token=settings.NATS_AUTH_TOKEN,
            max_reconnect_attempts=-1,
            allow_reconnect=True,
            reconnect_time_wait=2,
            error_cb=error_cb,
            disconnected_cb=disconnected_cb,
            reconnected_cb=reconnected_cb,
            closed_cb=closed_cb,
        )
        self.js = self.nc.jetstream()
        logger.info(f"Connected to NATS at {self.nc.connected_url.netloc}")

    async def publish(self, subject: str, message: bytes):
        ack = await self.js.publish(subject, message)
        logger.info(f"Published message to {subject}: {ack}")

    async def close(self):
        await self.nc.close()


nats_client = NATSClient()
