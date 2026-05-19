#!/usr/bin/env bash
# Measure application_ready_time_seconds across N boots of a running image.
#
# Usage:
#   scripts/measure-startup.sh <image-ref> [N]
#
# Prints one number per boot to stdout plus min/p50/p95/max to stderr. Reads the canonical
# Spring Boot whole-app-readiness gauge from /actuator/prometheus.
# https://docs.spring.io/spring-boot/reference/actuator/metrics.html

set -euo pipefail

IMAGE_REF="${1:?usage: $0 <image-ref> [N]}"
N="${2:-10}"

NETWORK="hephaestus-startup-bench-$$"
PG_NAME="pg-$$"
APP_NAME_PREFIX="app-$$"
trap 'docker rm -f "$PG_NAME" >/dev/null 2>&1 || true; for i in $(seq 1 "$N"); do docker rm -f "${APP_NAME_PREFIX}-${i}" >/dev/null 2>&1 || true; done; docker network rm "$NETWORK" >/dev/null 2>&1 || true' EXIT

docker network create "$NETWORK" >/dev/null
docker run -d --name "$PG_NAME" --network "$NETWORK" \
    -e POSTGRES_DB=hephaestus -e POSTGRES_USER=root -e POSTGRES_PASSWORD=root \
    postgres:16 >/dev/null
until docker exec "$PG_NAME" pg_isready -U root -d hephaestus >/dev/null 2>&1; do sleep 1; done

readings=()
for i in $(seq 1 "$N"); do
    name="${APP_NAME_PREFIX}-${i}"
    docker run -d --name "$name" --network "$NETWORK" \
        -e SPRING_PROFILES_ACTIVE=prod \
        -e DATABASE_URL=postgresql://"$PG_NAME":5432/hephaestus \
        -e DATABASE_USERNAME=root -e DATABASE_PASSWORD=root \
        -p 0:8080 \
        "$IMAGE_REF" >/dev/null
    app_port=$(docker port "$name" 8080/tcp | head -1 | awk -F: '{print $NF}')
    deadline=$(( $(date +%s) + 120 ))
    until curl -sf "http://localhost:${app_port}/actuator/health/readiness" >/dev/null 2>&1; do
        if [[ $(date +%s) -gt $deadline ]]; then
            echo "Boot ${i} timed out after 120s" >&2
            docker logs "$name" >&2
            exit 2
        fi
        sleep 0.5
    done
    # Match `application_ready_time_seconds` with or without label set, e.g.
    # `application_ready_time_seconds 4.123` or `application_ready_time_seconds{main_application_class="..."} 4.123`.
    ready=$(curl -s "http://localhost:${app_port}/actuator/prometheus" \
        | awk '/^application_ready_time_seconds([ {])/{print $NF; exit}')
    if [[ -z "$ready" ]]; then
        echo "Boot ${i}: no application_ready_time_seconds metric" >&2
        exit 3
    fi
    echo "$ready"
    readings+=("$ready")
    docker rm -f "$name" >/dev/null
done

# Nearest-rank percentile: rank = ceil(p * n), 1-indexed into sorted readings.
printf '%s\n' "${readings[@]}" | sort -n | awk -v n="$N" '
    BEGIN { idx = 1 }
    { a[idx++] = $1 }
    function pct(p,   r) { r = int(p * n + 0.999999); if (r < 1) r = 1; if (r > n) r = n; return a[r] }
    END {
        printf "min=%.3f p50=%.3f p95=%.3f max=%.3f n=%d\n", a[1], pct(0.50), pct(0.95), a[n], n
    }
' >&2
