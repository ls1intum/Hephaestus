import hmac
import hashlib
from contextlib import asynccontextmanager
from fastapi import Body, FastAPI, HTTPException, Header, Request, status
from pydantic import BaseModel
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

    org = "?"
    repo = "?"
    if "repository" in payload:
        org = payload["repository"]["owner"]["login"]
        repo = payload["repository"]["name"]
    elif "organization" in payload:
        org = payload["organization"]["login"]
    
    org_sanitized = org.replace('.', '~')
    repo_sanitized = repo.replace('.', '~')

    subject = f"github.{org_sanitized}.{repo_sanitized}.{event_type}"

    # Publish the payload to NATS JetStream
    await nats_client.publish_with_retry(subject, body)

    return { "status": "ok" }


class HealthCheck(BaseModel):
    """Response model to validate and return when performing a health check."""

    status: str = "OK"


@app.get(
    "/health",
    tags=["healthcheck"],
    summary="Perform a Health Check",
    response_description="Return HTTP Status Code 200 (OK)",
    status_code=status.HTTP_200_OK,
    response_model=HealthCheck,
)
def get_health() -> HealthCheck:
    """
    ## Perform a Health Check
    Endpoint to perform a healthcheck on. This endpoint can primarily be used Docker
    to ensure a robust container orchestration and management is in place. Other
    services which rely on proper functioning of the API service will not deploy if this
    endpoint returns any other HTTP status code except 200 (OK).
    Returns:
        HealthCheck: Returns a JSON response with the health status
    """
    return HealthCheck(status="OK")