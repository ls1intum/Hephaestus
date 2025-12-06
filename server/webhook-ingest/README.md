# Webhook Ingest

A FastAPI service that accepts GitHub and GitLab webhooks and publishes the
payload to NATS JetStream.

## Configuration

Provide the following environment variables when running the service:

- `NATS_URL` – NATS server URL, for example `nats://nats-server:4222`
- `WEBHOOK_SECRET` – HMAC secret configured on your GitHub and GitLab webhooks (`openssl rand -base64 32`)

### Local development

```bash
pip install poetry
poetry install
poetry run fastapi dev app/main.py
```

### Docker Compose

A `compose.yaml` file is provided for local testing together with a NATS
instance:

```bash
docker compose up --build
```

The compose file exposes the webhook service on port `4200` and the embedded
NATS server on port `4222`.

Set `WEBHOOK_SECRET` in your shell or a `.env` file before starting the compose
stack so the service can validate incoming requests.

## Webhook endpoints

- GitHub: `POST /github`
- GitLab: `POST /gitlab`

The service verifies the GitHub webhook signature via `X-Hub-Signature` and the
GitLab secret token via `X-Gitlab-Token`. Requests without the correct secret are
rejected with `401 Unauthorized`.

## NATS subjects

Events are published to JetStream using the following subjects:

- GitHub: `github.<owner>.<repo>.<event_type>`
- GitLab: `gitlab.<namespace>.<project>.<event_type>`

For GitLab events the full group path (including subgroups) is flattened into a
single `<namespace>` segment using `~` as separator. Any missing information is
replaced with `?`.
