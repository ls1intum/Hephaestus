# WebHook Ingest

## Overview

A service to ingest GitHub webhooks and publish the data to NATS JetStream.

## Setup

### Prerequisites

- **Python 3.9+**
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
- `SECRET`: HMAC secret for verifying GitHub webhooks

## Usage

Configure your GitHub webhooks to POST to:

```
http://<server>:4200/github
```

### Event Handling

Events are published to NATS with the subject:

```
github.<owner>.<repo>.<event_type>
```
