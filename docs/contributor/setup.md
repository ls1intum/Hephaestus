---
id: setup
sidebar_position: 2
title: Local Development Setup
---

## Prerequisites

Ensure the following toolchain is installed:

- **Java JDK 21** for the Spring Boot application server.
- **Node.js LTS (≥ 22.10) & npm (≥ 10.8)** for the React client.
- **Python 3.13 & Poetry 2.0** for the intelligence service.
- **Docker Desktop** to orchestrate PostgreSQL, Keycloak, and NATS.
- **NATS CLI (optional)** to inspect subjects during development.

## Workspace preparation

1. Clone the repository and open `project.code-workspace` in VS Code.
2. Install the recommended extensions (`@recommended` filter in the marketplace) to enable Java, Python, Tailwind CSS, and ESLint integrations.
3. Copy `.env.example` files to `.env` in each service that requires secrets. Never commit local overrides.

## Backend services

```bash
cd server/application-server
./mvnw spring-boot:run
```

- Runs under the `local` Maven profile by default.
- Overrides belong into `application-local.yml`, which is gitignored.

## Web client

```bash
cd webapp
npm install
npm run dev
```

Vite runs on port `4200`. Configure your API base URL via `webapp/.env.local` when pointing to a custom server instance.

## Intelligence service

```bash
cd server/intelligence-service
poetry install
poetry run fastapi dev
```

Expose secrets (e.g., OpenAI API keys) via the local `.env` file. FastAPI serves the OpenAPI UI at [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs).

## Useful make targets

Each service exposes convenience commands under `scripts/` (e.g., seeding demo data). Consult the README in the respective directory for the latest list.
