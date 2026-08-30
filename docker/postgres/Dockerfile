# ghcr.io/ls1intum/hephaestus/postgres — official PostgreSQL (Debian) + pg_partman, used in every
# environment (Alpine has no partman package). No shared_preload_libraries: maintenance runs as plain
# SQL from the app scheduler, so this stays stock Postgres + one extension. See ADR 0018 and ADR 0038.
ARG PG_MAJOR=18
# The digest is the multi-platform OCI index for 18-bookworm; refresh it when bumping the tag.
FROM postgres:${PG_MAJOR}-bookworm@sha256:1c59e2c3c818eaa0f0628f695b36e7c9e362d6b219b36a54a32df645cbd7e1af
ARG PG_MAJOR=18
# Pinned for reproducibility (PGDG bookworm pool). Bump deliberately.
ARG PARTMAN_VERSION=5.5.0-1.pgdg12+1
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends "postgresql-${PG_MAJOR}-partman=${PARTMAN_VERSION}"; \
    rm -rf /var/lib/apt/lists/*
