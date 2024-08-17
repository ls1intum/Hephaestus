from nats.aio.client import Client as NATS
from .config import settings


class NATSClient:
    def __init__(self, nats_url: str, nats_auth_token: str):
        self.nc = NATS()
        self.nats_url = nats_url
        self.nats_auth_token = nats_auth_token

    async def connect(self):
        await self.nc.connect(servers=self.nats_url, token=self.nats_auth_token)
        self.js = self.nc.jetstream()

    async def publish(self, subject: str, message: bytes):
        ack = await self.js.publish(subject, message)
        print(ack)

    async def close(self):
        await self.nc.close()


nats_client = NATSClient(settings.NATS_URL, settings.NATS_AUTH_TOKEN)
