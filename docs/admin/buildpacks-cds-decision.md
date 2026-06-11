---
id: buildpacks-cds-decision
title: Server image build via Paketo Buildpacks + Application CDS
sidebar_position: 4
description: How and why the application-server image is built with Paketo Cloud Native Buildpacks + Application CDS, and the Java 25 + JEP 483 migration path.
---

The `application-server` image is built by `mvn spring-boot:build-image` (Paketo Cloud Native Buildpacks) with Application Class Data Sharing (CDS) enabled. The hand-rolled `Dockerfile` was removed. Spring Boot AOT processing is **off** (see below).

## Why

Production warm startup was ~13.3s/pod (post-#1281 baseline); across a 5-replica Coolify rolling-restart this is a perceivable p95 latency window. Spring's published CDS numbers on Spring MVC + Tomcat are ~1.5× faster startup ([Spring blog 2024-08-29](https://spring.io/blog/2024/08/29/spring-boot-cds-support-and-project-leyden-anticipation/)). Liquibase keeps its absolute share regardless; the realistic projection is ≥30% off the Spring portion of boot.

## What the build does

`builder-noble-java-tiny` applies the Spring Boot buildpack. With `BP_JVM_CDS_ENABLED=true` the launcher runs once at build time (the "CDS training run") to load the bean-graph classes, archives them to `/workspace/application.jsa`, and bakes `-XX:SharedArchiveFile=/workspace/application.jsa` into the launcher. At runtime the JVM mmaps the archive instead of class-loading from JARs.

The training run boots under the `cds-training` profile (`application-cds-training.yml`), which disables Liquibase + JDBC-metadata probing and pins the Hibernate dialect so context refresh succeeds without a reachable Postgres. Coolify's runtime `SPRING_PROFILES_ACTIVE=prod` overrides the buildpack-baked default (Paketo writes `env.launch/<KEY>.default`, which yields to the runtime env).

## Why not Spring AOT processing (`spring.aot.enabled=true`)

AOT processing evaluates `@Conditional` at **build time**, baking the build-time environment into the image ([reference](https://docs.spring.io/spring-boot/reference/packaging/aot.html)). The codebase has ~70 `@ConditionalOn*` sites (Sentry, Slack, PostHog, Resilience4j, etc.) that depend on env vars intentionally absent in CI — enabling AOT now would silently drop those beans from the production image. Revisit when the JDK move to Java 25 LTS lands (epic #1096); [JEP 483 AOT cache](https://openjdk.org/jeps/483) then supersedes CDS via `BP_JVM_AOTCACHE_ENABLED=true`.

## Why not GraalVM Native Image

8–12× CI build inflation, loss of JIT peak throughput on Hibernate workloads, no JFR/JVMTI/debugger, closed-world model breaks `@ConditionalOn*` runtime overrides, and three reflective deps (`com.slack.api:bolt`, kobylynskyi runtime, `liquibase-core:5.x`) lack published reachability metadata.

## Builder pinning

`pom.xml` pins `builder-noble-java-tiny` + `ubuntu-noble-run-tiny` + the `health-checker` buildpack by
sha256 digest. Refresh:

```
docker buildx imagetools inspect paketobuildpacks/builder-noble-java-tiny:latest --format '{{.Manifest.Digest}}'
docker buildx imagetools inspect paketobuildpacks/ubuntu-noble-run-tiny:latest    --format '{{.Manifest.Digest}}'
docker buildx imagetools inspect paketobuildpacks/health-checker:latest           --format '{{.Manifest.Digest}}'
```

Bump as part of release cycles; the digest is the source of truth.

## Container healthcheck on the distroless run image

`run-tiny` has no shell/wget, and `builder-noble-java-tiny` does not bundle a probe, so a container
`HEALTHCHECK` is impossible out of the box. Rather than drop it (the only container-level health signal
on Docker Compose — `service_healthy` gating + `docker compose ps` depend on it), `pom.xml` adds an
explicit `<buildpacks>` order. That **replaces** the builder's default order, so the `java` composite
must be re-listed (`urn:cnb:builder:paketo-buildpacks/java`) before appending
`docker://paketobuildpacks/health-checker`; `BP_HEALTH_CHECKER_ENABLED=true` opts it in. It contributes
the static, shell-free `thc` binary at `/workspace/health-check`, which the compose services invoke as an
exec-form `HEALTHCHECK` (`THC_PORT`/`THC_PATH` → actuator liveness/readiness). No `health-check` process
type is added, so the JVM-spawn-per-probe issue (health-checker#87) does not apply.

## git CLI in the runtime image

`GitDiffOperations` previously shelled out to `git`; it was ported to JGit in the prerequisite commit, eliminating the runtime `git` dependency. The Paketo run image is used unmodified.

## Rollback

Revert `.github/workflows/ci-docker-build.yml` (the `use-buildpacks: true` line) and re-add a `Dockerfile`. Coolify re-deploys the prior image SHA. Detection: Sentry release-tagged error spike, or Prometheus alert on `application_ready_time_seconds > 15` for three consecutive deploys.

## Operational checklist

- **Coolify graceful shutdown** — `application.yml` sets `SHUTDOWN_TIMEOUT:20s`. Coolify's default container stop-grace is 10s; bump it to ≥25s in the deploy substrate so SIGTERM has time to drain in-flight requests. The Paketo launcher `exec`s the JVM; signal forwarding is native, no `tini`.
- **JVM memory** — do NOT set `MaxRAMPercentage`, `-Xmx`, or `-Xss` in Coolify env. Paketo's memory calculator handles them. Override only `BPL_JVM_HEAD_ROOM` if needed.
- **SBOM** — Paketo emits Syft + SPDX + CycloneDX at `/layers/sbom/`. CI extracts via `pack sbom download` and uploads as a 90-day artifact.
- **CVE scan** — Trivy runs on every PR and uploads SARIF to GitHub Security. Until the baseline is clean, results are non-blocking; flip `--exit-code 1` in the workflow once HIGH+ is at zero.
- **CI build time** — expect +60–120s per build vs the prior Dockerfile baseline (CDS training run dominates).

## Sources

- [Spring Boot 4 — Class Data Sharing how-to](https://docs.spring.io/spring-boot/how-to/class-data-sharing.html)
- [Spring Boot 4 — Packaging OCI Images](https://docs.spring.io/spring-boot/maven-plugin/build-image.html)
- [Spring Boot 4 — Ahead-of-Time Processing](https://docs.spring.io/spring-boot/reference/packaging/aot.html)
- [OpenJDK JEP 483 — Ahead-of-Time Class Loading & Linking](https://openjdk.org/jeps/483)
- [paketo-buildpacks/spring-boot](https://github.com/paketo-buildpacks/spring-boot)
- [paketo-buildpacks/spring-boot#571 — BP_JVM_CDS_ENABLED deprecation track](https://github.com/paketo-buildpacks/spring-boot/issues/571)
- [Spring blog 2024-08-29 — CDS + Project Leyden](https://spring.io/blog/2024/08/29/spring-boot-cds-support-and-project-leyden-anticipation/)
