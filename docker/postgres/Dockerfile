# ghcr.io/hephaestus-build/postgres — official PostgreSQL (Debian) + pg_partman, used in every
# environment (Alpine has no partman package). No shared_preload_libraries: maintenance runs as plain
# SQL from the app scheduler, so this stays stock Postgres + one extension. See ADR 0018 and ADR 0038.
ARG PG_MAJOR=18
FROM postgres:${PG_MAJOR}-bookworm
ARG PG_MAJOR=18
# Pinned for reproducibility (PGDG bookworm pool). Bump deliberately.
ARG PARTMAN_VERSION=5.5.0-1.pgdg12+1
# The tag floats, so docker-library rebuilds already carry most OS updates here; the upgrade closes
# the window between a Debian security publication and the next upstream rebuild. Same posture as
# webapp/Dockerfile and docker/agents/pi/Dockerfile so no image is the one that blocks a release.
#
# SOURCE_COMMIT is what makes that upgrade actually run: the builder imports the `cache-main`
# registry cache, and a layer with no per-build input would replay a stale package set instead.
ARG SOURCE_COMMIT
RUN set -eux; \
    echo "source commit: ${SOURCE_COMMIT:-<unset — local build>}"; \
    apt-get update; \
    apt-get upgrade -y; \
    apt-get install -y --no-install-recommends "postgresql-${PG_MAJOR}-partman=${PARTMAN_VERSION}"; \
    rm -rf /var/lib/apt/lists/*
