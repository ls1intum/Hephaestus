# 48h staging soak: server-deps Boot 4 upgrade

**Tracks**: epic #1096
**ADR**: `docs/decisions/0001-server-deps-boot4-upgrade.md`
**Soak host**: `staging.hephaestus.aet.cit.tum.de`

## Pre-flight

1. CI green on the PR (unit + integration + architecture).
2. `mvn -f server/application-server/pom.xml -pl . liquibase:update-sql` against a prod-clone
   DB shows zero pending changesets.
3. Capture p95 latency baseline (Grafana → API latency dashboard) for:
   - `GET /workspaces/{slug}`
   - `GET /api/users/me`
   - `POST /internal/llm/v1/messages`
   - `GET /actuator/health`

## Procedure

1. Merge the upgrade PR; record the merge SHA.
2. Deploy to `staging.hephaestus.aet.cit.tum.de`.
3. Watch startup logs (`/tmp/app-server-v6.log` or the Coolify log stream) for:
   - `Sentry SDK enabled` — confirms the renamed `sentry-spring-boot-4-starter` autoconfig fired.
   - Hibernate dialect resolution OK against PostgreSQL.
   - Modulith bootstrap completes without throwing.
4. Watch end-to-end traffic for 48h.
5. Compare p95 latency to the baseline for the same endpoints.

## Pass criteria

- Zero startup ERROR lines.
- p95 latency within ±15% of pre-upgrade baseline per endpoint.
- Sentry inbox shows real exceptions captured during the window.
- `ApplicationModulesVerificationTest` baseline hash file matches between local CI and staging.

## Fail / rollback

1. `git revert <merge sha>` on `main`.
2. `mvn -f server/application-server/pom.xml verify` on the revert commit.
3. Push the revert; redeploy staging from the reverted SHA.
4. File a follow-up issue with the failure mode + reproducer.
