---
id: overview
sidebar_position: 1
title: Contributor Guide
---

Hephaestus is composed of multiple services maintained by the Applied Education Technologies team. This guide consolidates engineering standards, local setup instructions, and deployment responsibilities for contributors.

## Repository layout

```text
Hephaestus/
├── webapp/                    # React + Vite front-end
├── server/
│   ├── application-server/    # Spring Boot backend
│   ├── intelligence-service/  # FastAPI service for mentoring intelligence
│   └── webhook-ingest/        # NATS-based GitHub webhook adapter
├── docs/                      # Docusaurus site (this documentation)
└── docs_old/                  # Legacy Sphinx sources for reference
```

## Core principles

1. **Security-first** – OAuth scopes are scoped to least privilege; secrets are injected through infrastructure managed by TUM.
2. **Observability by default** – All services expose OpenTelemetry traces and ship structured logs to the central Loki stack.
3. **Accessibility in the UI** – WCAG 2.1 AA must be maintained across new features.
4. **Research-friendly** – Experiments sit behind feature flags and are documented in `docs/contributor/experiments`.

Continue with [Local development setup](./setup) to prepare your workstation.
