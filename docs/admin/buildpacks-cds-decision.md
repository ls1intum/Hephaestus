---
id: buildpacks-cds-decision
title: Server image build via Paketo Buildpacks + Application CDS
description: How and why the application-server image is built with Paketo Cloud Native Buildpacks + Application CDS, and the Java 25 + JEP 483 migration path.
---

The `application-server` image is built with `pack` (Paketo Cloud Native Buildpacks) from the
executable JAR that CI packaged and tested, with Application Class Data Sharing (CDS) enabled. The
recipe is `server/application/project.toml`; there is no `Dockerfile`. Spring Boot AOT processing is
**off** (see below).

## Why

Production warm startup was ~13.3s/pod (post-#1281 baseline); across a 5-replica Coolify rolling-restart this is a perceivable p95 latency window. Spring's published CDS numbers on Spring MVC + Tomcat are ~1.5× faster startup ([Spring blog 2024-08-29](https://spring.io/blog/2024/08/29/spring-boot-cds-support-and-project-leyden-anticipation/)). Liquibase keeps its absolute share regardless; the realistic projection is ≥30% off the Spring portion of boot.

## What the build does

`builder-noble-java-tiny` applies the Spring Boot buildpack. With `BP_JVM_CDS_ENABLED=true` the launcher runs once at build time (the "CDS training run") to load the bean-graph classes, archives them to `/workspace/application.jsa`, and bakes `-XX:SharedArchiveFile=/workspace/application.jsa` into the launcher. At runtime the JVM mmaps the archive instead of class-loading from JARs.

The training run boots under the `cds-training` profile (`application-cds-training.yml`), which disables Liquibase + JDBC-metadata probing and pins the Hibernate dialect so context refresh succeeds without a reachable Postgres. Coolify's runtime `SPRING_PROFILES_ACTIVE=prod` overrides the buildpack-baked default (Paketo writes `env.launch/<KEY>.default`, which yields to the runtime env).

## Why not Spring AOT processing (`spring.aot.enabled=true`)

AOT evaluates conditional bean registration at build time. Hephaestus selects integrations and runtime roles from
deployment configuration, so an image built in CI cannot safely fix those choices without omitting beans needed in
production. CDS preserves runtime configuration while improving startup.

## Why not GraalVM Native Image

8–12× CI build inflation, loss of JIT peak throughput on Hibernate workloads, no JFR/JVMTI/debugger, closed-world model breaks `@ConditionalOn*` runtime overrides, and three reflective deps (`com.slack.api:bolt`, kobylynskyi runtime, `liquibase-core:5.x`) lack published reachability metadata.

## Builder pinning

`server/application/project.toml` pins `builder-noble-java-tiny` and the `health-checker` buildpack
by sha256 digest; `.github/workflows/ci-build.yml` pins `ubuntu-noble-run-tiny` the same way, because
the project descriptor has no run-image key. Renovate follows each image's `latest` tag and proposes
the digest bumps, so a refresh is a reviewed pull request rather than a hand-edited digest.

## Container healthcheck on the distroless run image

On Docker Compose the container `HEALTHCHECK` is the only container-level health signal —
`service_healthy` gating and the `docker compose ps` column both depend on it. `run-tiny` has no
shell/wget and `builder-noble-java-tiny` bundles no probe, so `server/application/project.toml` names an explicit buildpack
group. A named group **replaces** the builder's default order, so the `java` composite must be listed
(`paketo-buildpacks/java`) before `docker://paketobuildpacks/health-checker`; `BP_HEALTH_CHECKER_ENABLED=true` opts it in. It contributes
the static, shell-free `thc` binary at `/workspace/health-check`, which the compose services invoke as an
exec-form `HEALTHCHECK` (`THC_PORT`/`THC_PATH` → actuator liveness/readiness). No `health-check` process
type is added, so the JVM-spawn-per-probe issue (health-checker#87) does not apply.

## git CLI in the runtime image

`GitDiffOperations` previously shelled out to `git`; it was ported to JGit in the prerequisite commit, eliminating the runtime `git` dependency. The Paketo run image is used unmodified.

## Rollback

Switch the `application-server-image` job in `.github/workflows/ci-build.yml` from `use-buildpacks: true` to a `docker-file` that packages the same JAR, and re-add that `Dockerfile`. Coolify re-deploys the prior image SHA. Detection: Sentry release-tagged error spike, or Prometheus alert on `application_ready_time_seconds > 15` for three consecutive deploys.

## Operational checklist

- **Coolify graceful shutdown** — `application.yml` sets `SHUTDOWN_TIMEOUT:20s`. Coolify's default container stop-grace is 10s; bump it to ≥25s in the deploy substrate so SIGTERM has time to drain in-flight requests. The Paketo launcher `exec`s the JVM; signal forwarding is native, no `tini`.
- **JVM memory** — do NOT set `MaxRAMPercentage`, `-Xmx`, or `-Xss` in Coolify env. Paketo's memory calculator handles them. Override only `BPL_JVM_HEAD_ROOM` if needed.
- **CI build time** — the image job downloads the packaged JAR and spends its time in the CDS training run and the export; nothing is compiled there.

## Sources

- [Spring Boot 4 — AOT cache and CDS how-to](https://docs.spring.io/spring-boot/how-to/aot-cache.html)
- [Paketo — build an image from a compiled artifact](https://paketo.io/docs/howto/java/)
- [pack build reference](https://buildpacks.io/docs/for-platform-operators/how-to/integrate-ci/pack/cli/pack_build/)
- [Project descriptor reference](https://buildpacks.io/docs/reference/config/project-descriptor/)
- [Spring Boot 4 — Packaging OCI Images](https://docs.spring.io/spring-boot/maven-plugin/build-image.html)
- [Spring Boot 4 — Ahead-of-Time Processing](https://docs.spring.io/spring-boot/reference/packaging/aot.html)
- [OpenJDK JEP 483 — Ahead-of-Time Class Loading & Linking](https://openjdk.org/jeps/483)
- [paketo-buildpacks/spring-boot](https://github.com/paketo-buildpacks/spring-boot)
- [paketo-buildpacks/spring-boot#571 — BP_JVM_CDS_ENABLED deprecation track](https://github.com/paketo-buildpacks/spring-boot/issues/571)
- [Spring blog 2024-08-29 — CDS + Project Leyden](https://spring.io/blog/2024/08/29/spring-boot-cds-support-and-project-leyden-anticipation/)
