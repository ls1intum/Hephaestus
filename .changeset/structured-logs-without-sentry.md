---
"hephaestus": minor
---

Every runtime role now writes its logs as Elastic Common Schema JSON, with the running version, the deployment environment and the runtime role on each line. Log lines name exceptions by class and frames without their messages, never contain a sandbox transcript, and no longer repeat the database's message for a failed statement. Hephaestus no longer sends errors to Sentry. An optional example ships the service logs to a Loki that you run. **Operators:** remove `SENTRY_DSN`, and update log queries and dashboards for the new field names.
