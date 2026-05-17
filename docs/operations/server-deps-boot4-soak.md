# 48-hour staging soak: server-deps Boot 4 upgrade

**Tracks**: epic #1096
**ADR**: `docs/decisions/0001-server-deps-boot4-upgrade.md`
**Soak host**: `staging.hephaestus.aet.cit.tum.de`
**Soak window**: starts on merge of the upgrade PR; success at +48h with no rollback signal.

## Pre-flight (before merging)

1. CI green on the PR: full unit, integration, and architecture suites pass against the new BOMs.
2. Pre-upgrade p95 latency baseline captured for the endpoints we care about:
   - `GET /workspaces/{slug}` — public read
   - `GET /api/users/me` — JWT-authenticated read
   - `POST /internal/llm/v1/messages` — LLM-proxy chain (job token)
   - `POST /actuator/health` — actuator readiness
3. `mvn liquibase:update-sql` against a prod-clone DB shows no pending changesets after the
   Liquibase 5 history-schema check.

## Soak procedure

1. Merge PR to `main`. Capture the merge SHA.
2. Deploy to `staging.hephaestus.aet.cit.tum.de`.
3. Watch startup log for:
   - `Sentry SDK enabled` — confirms `sentry-spring-boot-4-starter` autoconfig fired
     (the artifact rename was the single biggest external risk in the upgrade).
   - Zero `WARN` / `ERROR` lines for deprecated APIs.
   - Hibernate 7 dialect resolution OK against PostgreSQL.
   - Spring Modulith verification logged for the 11 declared modules.
4. Trigger a synthetic exception (e.g. via a dev-only `/actuator/dev/throw` route, or by
   pointing a request at a known-failing endpoint) and confirm Sentry receives it.
5. Soak for 48h with current pre-refactor workload.
6. At +48h, capture p95 latency for the same endpoints. Compare to baseline.

## Pass criteria

- Zero startup errors over the window.
- p95 latency within ±15% of pre-upgrade baseline per endpoint.
- Sentry inbox shows the synthetic exception event.
- No new ERROR-level log lines vs the pre-upgrade baseline (excluding the synthetic exception).
- Modulith violations counted by `ApplicationModulesVerificationTest` match the committed baseline
  (no drift between local CI and staging classpath).

## Fail / rollback

1. Tag the merge SHA and any subsequent commits with `boot4-rollback-needed`.
2. `git revert <merge sha>` on `main`.
3. Local sanity: `mvn -f server/application-server/pom.xml verify` exits 0 on the revert commit.
4. Push the revert; redeploy staging from the reverted SHA; confirm latency recovers.
5. File a follow-up issue with the specific failure mode + reproducer.

## Telemetry to capture

- p95 latency per endpoint (Grafana board on the management port).
- Heap usage delta vs pre-upgrade.
- GC pause distribution (Boot 4 changed several defaults).
- Hikari pool utilisation (Hibernate 7 lazy-loading regressions surface here).
- Sentry event count vs baseline (no synthetic-exception miss expected).
