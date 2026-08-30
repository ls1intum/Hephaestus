ARG PG_MAJOR=18
FROM postgres:${PG_MAJOR}-bookworm
ARG PG_MAJOR=18
ARG PARTMAN_VERSION=5.5.0-1.pgdg12+1
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends "postgresql-${PG_MAJOR}-partman=${PARTMAN_VERSION}"; \
    rm -rf /var/lib/apt/lists/*
