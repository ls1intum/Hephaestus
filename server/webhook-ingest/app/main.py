import hmac
import hashlib
from contextlib import asynccontextmanager

from app.config import settings
from app.nats_client import nats_client
from app.logger import logger
from fastapi import Body, FastAPI, HTTPException, Header, Request, status
from nats.js.api import StreamConfig, RetentionPolicy, DiscardPolicy, StorageType
from nats.js.errors import NotFoundError
from pydantic import BaseModel


STREAM_MAX_AGE_SECONDS = 180 * 24 * 60 * 60  # nats.py expects seconds, not ns
STREAM_MAX_MSGS = 2_000_000


@asynccontextmanager
async def lifespan(app: FastAPI):
    await nats_client.connect()

    # Check if stream exists before creating it
    for stream_name in ("github", "gitlab"):
        try:
            await nats_client.js.stream_info(stream_name)
            logger.info("Stream '%s' already exists", stream_name)
        except NotFoundError:
            logger.info("Creating '%s' stream", stream_name)
            # Production limits: retain for ~180 days and cap messages (seconds per nats.py).
            retention_cfg = StreamConfig(
                storage=StorageType.FILE,
                retention=RetentionPolicy.LIMITS,
                discard=DiscardPolicy.OLD,
                max_age=STREAM_MAX_AGE_SECONDS,
                max_msgs=STREAM_MAX_MSGS,
            )
            await nats_client.js.add_stream(
                name=stream_name,
                subjects=[f"{stream_name}.>"],
                config=retention_cfg,
            )
        except Exception as exc:
            logger.error("Failed to inspect/create stream '%s': %s", stream_name, exc)
            raise

    yield
    await nats_client.close()


app = FastAPI(lifespan=lifespan)


def verify_github_signature(
    signature_header: str | None, secret: str, body: bytes
) -> bool:
    """Verify GitHub webhook signature supporting sha256 and sha1."""

    if not signature_header or not secret:
        return False

    if signature_header.startswith("sha256="):
        algo = hashlib.sha256
        prefix = "sha256="
    elif signature_header.startswith("sha1="):
        algo = hashlib.sha1
        prefix = "sha1="
    else:
        return False

    mac = hmac.new(secret.encode(), body, algo)
    expected = prefix + mac.hexdigest()
    return hmac.compare_digest(signature_header, expected)


def build_gitlab_subject(payload: dict) -> str:
    event_name = (
        payload.get("object_kind") or payload.get("event_name") or "unknown"
    ).lower()

    # We will try to detect project-scoped details first, then group-scoped, then object_attributes,
    # and only if nothing can be determined, fall back to instance (?.?).

    namespace_segment: str | None = None
    project_segment: str | None = None

    def sanitize_parts(path: str) -> list[str]:
        # Replace dots to avoid extra NATS tokens; flatten with "~"
        return [str(part).replace(".", "~") for part in path.split("/") if part]

    # 1) Project-scoped: common GitLab payloads include a `project` dict or path_with_namespace
    project = payload.get("project") or {}
    path_with_namespace = payload.get("path_with_namespace") or project.get(
        "path_with_namespace"
    )
    if path_with_namespace:
        parts = sanitize_parts(path_with_namespace)
        if len(parts) >= 2:
            namespace_segment = "~".join(parts[:-1])
            project_segment = parts[-1]

    # 2) Group-scoped: common GitLab payloads include a `group` dict
    if not namespace_segment:
        group = payload.get("group") or {}
        group_path = (
            group.get("full_path") or group.get("path") or group.get("group_path")
        )
        if group_path:
            parts = sanitize_parts(group_path)
            if parts:
                namespace_segment = "~".join(parts)
                project_segment = "?"

    # 3) Parse from object_attributes
    if not namespace_segment:
        object_attributes = payload.get("object_attributes") or {}
        has_project = object_attributes.get("project_id") is not None
        url = object_attributes.get("url") or ""
        if "://" in url:
            # Example: https://gitlab.lrz.de/ga84xah/codereviewtest/-/merge_requests/1#note_4108500
            # Remove the protocol and domain and everything including /-/ and after
            path = url.split("://")[-1].split("/", 1)[-1]
            if "/-/" in path:
                path = path.split("/-/")[0]
            parts = sanitize_parts(path)

            if has_project and len(parts) > 1:
                namespace_segment = "~".join(parts[:-1])
                project_segment = parts[-1]
            elif parts:
                namespace_segment = "~".join(parts)
                project_segment = "?"

    # 4) Fallback to instance-level
    if not namespace_segment:
        namespace_segment = "?"
    if not project_segment:
        project_segment = "?"

    subject_parts = ["gitlab", namespace_segment, project_segment, event_name]

    return ".".join(subject_parts)


@app.post("/github")
async def github_webhook(
    request: Request,
    signature_sha1: str | None = Header(None, alias="X-Hub-Signature"),
    signature_sha256: str | None = Header(None, alias="X-Hub-Signature-256"),
    event_type: str = Header(
        None,
        alias="X-GitHub-Event",
        description="The type of event that triggered the webhook, such as 'push', 'pull_request', etc.",
    ),
    body=Body(...),
):
    body = await request.body()

    if not settings.WEBHOOK_SECRET:
        raise HTTPException(
            status_code=401, detail="GitHub webhook secret not configured"
        )

    signature = signature_sha256 or signature_sha1
    if not verify_github_signature(signature, settings.WEBHOOK_SECRET, body):
        raise HTTPException(status_code=401, detail="Invalid signature")

    # Ignore ping events
    if event_type == "ping":
        return {"status": "pong"}

    # Extract subject from the payload
    payload = await request.json()

    org = "?"
    repo = "?"
    if "repository" in payload:
        org = payload["repository"]["owner"]["login"]
        repo = payload["repository"]["name"]
    elif "organization" in payload:
        org = payload["organization"]["login"]

    org_sanitized = org.replace(".", "~")
    repo_sanitized = repo.replace(".", "~")

    subject = f"github.{org_sanitized}.{repo_sanitized}.{event_type}"

    # Publish the payload to NATS JetStream
    await nats_client.publish_with_retry(subject, body)

    return {"status": "ok"}


@app.post("/gitlab")
async def gitlab_webhook(
    request: Request,
    token: str = Header(
        None,
        alias="X-GitLab-Token",
        description="Secret token configured in GitLab, used for validating webhook authenticity",
    ),
    body=Body(...),
):
    body = await request.body()

    if not settings.WEBHOOK_SECRET:
        raise HTTPException(
            status_code=401, detail="GitLab webhook secret not configured"
        )

    # Constant-time compare to avoid timing attacks
    if not hmac.compare_digest(token or "", settings.WEBHOOK_SECRET):
        raise HTTPException(status_code=401, detail="Invalid token")

    payload = await request.json()

    subject = build_gitlab_subject(payload)

    await nats_client.publish_with_retry(subject, body)

    return {"status": "ok"}


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
