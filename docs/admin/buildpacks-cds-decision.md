---
id: buildpacks-cds-decision
title: Server image build via Paketo Buildpacks + Application CDS
sidebar_position: 4
description: Why the application-server image is built with Paketo Cloud Native Buildpacks and Application CDS instead of a hand-rolled Dockerfile, what alternatives were rejected, and the path forward for Java 25 + JEP 483 AOT cache.
---

The `application-server` image is built with [Paketo Cloud Native Buildpacks](https://paketo.io/) invoked through `spring-boot:build-image`, with Application Class Data Sharing (CDS) enabled. The Spring Boot AOT processor is **not** run at runtime (`spring.aot.enabled=true` is unset). The hand-rolled `server/application-server/Dockerfile` was removed.

## Why

Production warm startup was ~13.3s per pod (post-#1281 baseline). Across the 5-replica Coolify rolling-restart cycle that produces a perceivable p95 latency spike window. Spring's own canonical numbers for Application CDS show ~1.5× faster startup on Spring MVC + Tomcat ([Spring Boot CDS support and Project Leyden anticipation, 2024-08-29](https://spring.io/blog/2024/08/29/spring-boot-cds-support-and-project-leyden-anticipation/)). On a Hibernate-heavy app the absolute saving is smaller as a fraction of total boot — Liquibase keeps its share — but ≥30% of the Spring portion is the realistic projection that fits the issue #1282 acceptance criterion.

## What the build does

`mvn spring-boot:build-image` invokes Paketo's `builder-jammy-tiny`, which:

1. Detects the Spring Boot JAR and applies the Spring Boot buildpack.
2. With `BP_JVM_CDS_ENABLED=true`, runs the launcher once during the build (the "CDS training run") to load every class in the bean factory's dependency closure, then archives the loaded class metadata to `/workspace/application.jsa`.
3. Layers the JDK, JAR, dependencies, and CDS archive as separate OCI layers so unchanged dependencies don't re-publish on every build.
4. Sets the launcher to pass `-XX:SharedArchiveFile=/workspace/application.jsa` on every container start.

At runtime the JVM mmaps the CDS archive instead of class-loading from JARs, cutting the class-resolution portion of startup.

## Why not Spring AOT processing (build-time bean factory)

Spring Boot 4 supports a stronger optimization: AOT-process the application at build time so the bean factory is pre-resolved (`spring.aot.enabled=true`). On a fresh Spring MVC sample this combines with CDS for ~2× startup speedup (vs. ~1.5× for CDS alone).

It is **not enabled here**. Per [Spring Boot reference — Ahead-of-Time Processing](https://docs.spring.io/spring-boot/reference/packaging/aot.html), AOT processing evaluates `@Conditional` (including `@ConditionalOnProperty`) at **build time**, baking the build-time environment's properties into the produced image. The codebase has ~70 `@ConditionalOn*` sites — Sentry, Slack, PostHog, Resilience4j gates, Liquibase, etc. — many of which depend on env vars that are intentionally absent in CI. Enabling AOT processing now would silently drop those beans from the production image.

The plan is to migrate the optional-integration gates to boolean profile-style properties set in `application-prod.yml` (so the AOT processor sees them) once the JDK move to Java 25 LTS lands (epic #1096). Java 25 + [JEP 483 — Ahead-of-Time Class Loading & Linking](https://openjdk.org/jeps/483) supersedes CDS with a true bean-graph AOT cache; flipping `BP_JVM_CDS_ENABLED=true` → `BP_JVM_AOTCACHE_ENABLED=true` and adding `-Dspring.aot.enabled=true` to the launcher is the migration path.

## Why not GraalVM Native Image

GraalVM Native produces a single binary with sub-second startup, but pays for it elsewhere:

- 8–12× CI build inflation per image.
- Loss of JIT peak throughput on long-running workloads — measurably worse on a Hibernate-heavy server.
- No JFR / JVMTI / debugger / breakpoints.
- Closed-world model breaks runtime override of `@ConditionalOn*` — the 72-site surface in this codebase doesn't fit cleanly.
- Three load-bearing reflective dependencies (`com.slack.api:bolt`, `io.github.kobylynskyi:graphql-java-codegen` runtime, `liquibase-core:5.x`) lack published reachability metadata.

For a 5-replica HotSpot deployment, the CI cost outweighs the saving.

## Why not `pack` CLI directly

`spring-boot:build-image` already wires `${project.version}` and Maven reactor state; `pack` is one moving part more for no benefit.

## Builder pinning

`pom.xml` references `paketobuildpacks/builder-noble-java-tiny:latest` and `paketobuildpacks/run-noble-java-tiny:latest`. The Ubuntu Noble line is the active Spring Boot 4 default; Jammy is on its way out. Digest-pinning the builder and run image in CI is a follow-up — currently the Maven plugin pulls `latest` on every build, which is reproducible-within-a-day but not across weeks.

## git CLI in the runtime image

The previous Dockerfile installed `git` because `GitDiffOperations.runGit()` shelled out to it. That shell-out was ported to JGit in the prerequisite commit, eliminating the runtime `git` dependency entirely. The Paketo run image is used unmodified — no apt-top-up layer, no cosign signing fragility, no extra CVE surface.

## Rollback

Revert the workflow change in `.github/workflows/ci-docker-build.yml` (the `use-buildpacks: true` line) and re-add a `Dockerfile`. Coolify re-deploys the prior image SHA. Detection: Sentry release-tagged error spike, or Prometheus alert on `application_ready_time_seconds > 15` for 3 consecutive deploys.

## Operational checklist

- **Coolify graceful shutdown** — `application.yml` sets `SHUTDOWN_TIMEOUT:20s`. Coolify's default container stop-grace is 10s; bump it to 30s in the deploy substrate so SIGTERM has time to drain in-flight requests. The Paketo launcher `exec`s the JVM so signal forwarding is native; no `tini` needed.
- **JVM memory** — do NOT set `MaxRAMPercentage`, `-Xmx`, or `-Xss` in Coolify env. Paketo's memory calculator computes them from container memory minus reserved headroom. Override only `BPL_JVM_HEAD_ROOM` if needed.
- **SBOM** — Paketo emits Syft + SPDX + CycloneDX SBOMs at `/layers/sbom/` inside the image. CI does not yet extract them; `pack sbom download <image>` is the path when this becomes a release-gate requirement.
- **CI build time** — expect +60-120s per build vs the prior Dockerfile baseline (CDS training run dominates). The Maven cache action shaves dependency resolution.

## Sources

- [Spring Boot 4 — Class Data Sharing how-to](https://docs.spring.io/spring-boot/how-to/class-data-sharing.html)
- [Spring Boot 4 — Packaging OCI Images](https://docs.spring.io/spring-boot/maven-plugin/build-image.html)
- [Spring Boot 4 — Ahead-of-Time Processing](https://docs.spring.io/spring-boot/reference/packaging/aot.html)
- [Spring Boot 4 — AOT Cache](https://docs.spring.io/spring-boot/reference/packaging/aot-cache.html)
- [OpenJDK JEP 483 — Ahead-of-Time Class Loading & Linking](https://openjdk.org/jeps/483)
- [paketo-buildpacks/spring-boot](https://github.com/paketo-buildpacks/spring-boot)
- [paketo-buildpacks/spring-boot#571 — BP_JVM_CDS_ENABLED deprecation track](https://github.com/paketo-buildpacks/spring-boot/issues/571)
- [Spring blog 2024-08-29 — CDS + Project Leyden anticipation](https://spring.io/blog/2024/08/29/spring-boot-cds-support-and-project-leyden-anticipation/)
