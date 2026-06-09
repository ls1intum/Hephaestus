# ghcr.io/ls1intum/hephaestus/postgres — official PostgreSQL (Debian) + pg_partman, used in every
# environment (Alpine has no partman package). No shared_preload_libraries: maintenance runs as plain
# SQL from the app scheduler, so this stays stock Postgres + one extension. See ADR 0018.
ARG PG_MAJOR=17
FROM postgres:${PG_MAJOR}-bookworm
ARG PG_MAJOR=17
# Pinned for reproducibility (PGDG bookworm pool). Bump deliberately.
ARG PARTMAN_VERSION=5.4.3-1.pgdg12+1
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends "postgresql-${PG_MAJOR}-partman=${PARTMAN_VERSION}"; \
    rm -rf /var/lib/apt/lists/*
