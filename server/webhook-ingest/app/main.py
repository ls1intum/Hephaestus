import hmac
import hashlib
from contextlib import asynccontextmanager
from fastapi import Body, FastAPI, HTTPException, Header, Request
from nats.js.api import StreamConfig
from app.config import settings
from app.nats_client import nats_client


@asynccontextmanager
async def lifespan(app: FastAPI):
    await nats_client.connect()
    await nats_client.js.add_stream(name="github", subjects=["github.>"], config=StreamConfig(storage="file"))
    yield
    await nats_client.close()


app = FastAPI(lifespan=lifespan)


def verify_github_signature(signature, secret, body):
    mac = hmac.new(secret.encode(), body, hashlib.sha1)
    expected_signature = "sha1=" + mac.hexdigest()
    return hmac.compare_digest(signature, expected_signature)


@app.post("/github")
async def github_webhook(
    request: Request, 
    signature: str = Header(
        None, 
        alias="X-Hub-Signature", 
        description="GitHub's HMAC hex digest of the payload, used for verifying the webhook's authenticity"
    ), 
    event_type: str = Header(
        None, 
        alias="X-Github-Event",
        description="The type of event that triggered the webhook, such as 'push', 'pull_request', etc.",
    ),
    body = Body(...),
):    
    body = await request.body()
    
    if not verify_github_signature(signature, settings.WEBHOOK_SECRET, body):
        raise HTTPException(status_code=401, detail="Invalid signature")
    
    # Ignore ping events
    if event_type == "ping":
        return { "status": "pong" }
    
    # Extract subject from the payload
    payload = await request.json()
    owner = payload["repository"]["owner"]["login"]
    repo = payload["repository"]["name"]
    subject = f"github.{owner}.{repo}.{event_type}"

    # Publish the payload to NATS JetStream
    await nats_client.js.publish(subject, body)

    return { "status": "ok" }
