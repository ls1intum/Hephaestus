# WebHook Ingest

## Overview

A service to ingest GitHub webhooks and publish the data to NATS JetStream.

## Setup

### Prerequisites

- **Python 3.12**
- **Poetry** for dependency management
- **Docker** for containerization

### Installation

Install dependencies using Poetry:

```bash
pip install poetry
poetry install
```

## Running the Service

### Development

```bash
fastapi dev
```

### Production

```bash
fastapi run
```

## Docker Deployment

Build and run with Docker Compose:

```bash
docker-compose up --build
```

Service ports:
- **Webhook Service**: `4200`
- **NATS Server**: `4222`

## Environment Variables

- `NATS_URL`: NATS server URL
- `NATS_AUTH_TOKEN`: Authorization token for NATS server
- `WEBHOOK_SECRET`: HMAC secret for verifying GitHub webhooks
- `TLS_CERT_FILE`: Path to the TLS certificate file (used by NATS server)
- `TLS_KEY_FILE`: Path to the TLS key file (used by NATS server)

## Usage

Configure your GitHub webhooks to POST to:

```
https://<server>:4200/github
```

### Event Handling

Events are published to NATS with the subject:

```
github.<owner>.<repo>.<event_type>
```

## NATS Configuration with TLS



You're absolutely right. The NATS configuration with TLS and Let's Encrypt, along with the corresponding environment variables, is crucial for ensuring secure communication and should be highlighted in the README. Here’s an updated version:

---

# WebHook Ingest

## Overview

A service to ingest GitHub webhooks and publish the data to NATS JetStream.

## Setup

### Prerequisites

- **Python 3.12**
- **Poetry** for dependency management
- **Docker** for containerization

### Installation

Install dependencies using Poetry:

```bash
pip install poetry
poetry install
```

## Running the Service

### Development

```bash
fastapi dev
```

### Production

```bash
fastapi run
```

## Docker Deployment

Build and run with Docker Compose:

```bash
docker-compose up --build
```

Service ports:

- **Webhook Service**: `4200`
- **NATS Server**: `4222`

## Environment Variables

- `NATS_URL`: NATS server URL
- `NATS_AUTH_TOKEN`: Authorization token for NATS server
- `WEBHOOK_SECRET`: HMAC secret for verifying GitHub webhooks
- `TLS_CERT_FILE`: Path to the TLS certificate file (used by NATS server)
- `TLS_KEY_FILE`: Path to the TLS key file (used by NATS server)

## NATS Configuration with TLS

For secure communication in production, NATS can be configured with TLS using Let's Encrypt certificates.

### Steps to Create TLS Certificates

1. **Install Certbot** on your server:

```bash
sudo apt-get install certbot
```

2. **Obtain a Certificate**:

```bash
sudo certbot certonly --standalone -d <your.domain.com>
```

Replace `<your.domain.com>` with your actual domain name.

3. **Configure NATS** to use the certificate and key in the environment variables:

```bash
TLS_CERT_FILE=/etc/letsencrypt/live/<your.domain.com>/fullchain.pem
TLS_KEY_FILE=/etc/letsencrypt/live/<your.domain.com>/privkey.pem

NATS_URL=tls://<your.domain.com>
```

For more detailed instructions and options, refer to the [Certbot documentation](https://certbot.eff.org/).

### NATS Authorization Token

1. **Generate a Token**:
  
```bash
openssl rand -hex 48
```

2. **Set the Token** as an environment variable:

```bash
NATS_AUTH_TOKEN=<your_generated_token>
```

### Important Notes

- The service automatically sets up a NATS JetStream stream named `github` to store events.
- Ensure your firewall allows traffic on port 4222 (NATS) and ports 80/443 (Let's Encrypt challenge).
- TLS is essential so no sensitive data can be intercepted during communication (such as webhook payloads).
- Authentication tokens are crucial for securing the NATS server and ensuring only authorized clients can connect.
- The webhook ingest service connects to the NATS server like any other client using the specified URL and token.
- Allowing unauthenticated non-TLS connections from within the internal Docker network does not seem to be possible with the NATS server.