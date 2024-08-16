import nats
from .config import settings


class NATSClient:
    def __init__(self, nats_url: str):
        self.nats_url = nats_url

    async def connect(self):
        self.nc = await nats.connect(self.nats_url)
        self.js = self.nc.jetstream()

    async def publish(self, subject: str, message: bytes):
        ack = await self.js.publish(subject, message)
        print(ack)

    async def close(self):
        await self.nc.close()


nats_client = NATSClient(settings.NATS_URL)
