# ghcr.io/ls1intum/hephaestus/postgres — official PostgreSQL (Debian bookworm) + pg_partman.
# pg_partman has no Alpine package, so every environment (dev/preview/prod) uses THIS image.
# No shared_preload_libraries / background worker: partition maintenance runs as plain SQL
# (CALL partman.run_maintenance_proc()) from the app's ShedLock'd scheduler, so the image stays
# stock Postgres + one extension — nothing to misconfigure at startup. See ADR superseding 0017.
ARG PG_MAJOR=17
FROM postgres:${PG_MAJOR}-bookworm
ARG PG_MAJOR=17
# Pinned for reproducibility (PGDG bookworm pool). Bump deliberately.
ARG PARTMAN_VERSION=5.4.3-1.pgdg12+1
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends "postgresql-${PG_MAJOR}-partman=${PARTMAN_VERSION}"; \
    rm -rf /var/lib/apt/lists/*
