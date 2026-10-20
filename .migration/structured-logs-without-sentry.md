#### 🔴 Update log queries for the new log format and remove `SENTRY_DSN`

The server, worker and webhook containers now write Elastic Common Schema JSON through Spring Boot's own formatter.
Hephaestus no longer sends errors to Sentry, from the server or from the browser.

**Operators:**

1. Remove `SENTRY_DSN` from the environment of every container. The new release ignores it.
2. Update saved log queries, alerts and dashboards for these field changes:

   | Before                 | After                                      |
   | ---------------------- | ------------------------------------------ |
   | `timestamp`            | `@timestamp`                               |
   | `level`                | `log.level`                                |
   | `logger`               | `log.logger`                               |
   | `mdc.<name>`           | `context.<name>`, for example `context.traceId` |
   | `<event field>`        | `context.<event field>`, for example `context.event.name` |
   | `stacktrace`           | `error.stack_trace`, with each exception named by its class only |
   | `mdc.workspace_slug`   | Removed. Use `context.workspace.id` |
   | NATS failure `subject` | `context.integration.route` (provider and event) and `context.integration.delivery` |
   | Exception message text in a log message | The exception class |

   Each line also carries `service.name`, `service.version`, `service.environment`, `service.node.name` and `runtime.role`.
   Dotted names are nested JSON objects.
   Read them with a JSON tool, not by text position.
   A stack trace is at most 8192 characters and 32 frames for each exception.
3. Error reports that an earlier release sent to Sentry stay with that recipient under its own retention.
   Removing the integration does not delete them.
4. Keep production logging at `INFO`.
   `DEBUG` on framework, client or driver packages can write request and provider content.

To collect the logs, see [Collecting logs](https://docs.hephaestus.build/admin/observability#collecting-logs).
Collection is optional and sends nothing until you configure a destination.
Start it only after this release runs: lines that an earlier release wrote are not redacted after the fact.
