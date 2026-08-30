---
"hephaestus": minor
---

Operators can now ingest application logs as structured JSON, correlate agent-job logs by job or workspace, and scrape application metrics from the Prometheus actuator endpoint. Bearer, GitLab, GitHub, and token-query-string credentials are masked before console output.

**Operators:** the server now writes one JSON object per log line instead of plain text, by default and in every profile. Anything that parses the previous plain-text format — log shippers, alerts, grep-based tooling — must be updated to read JSON; see `MIGRATION.md`.
