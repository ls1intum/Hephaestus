#!/usr/bin/env bash
# Measures application_ready_time_seconds across N boots of a running image.
#
# Usage:
#   scripts/measure-startup.sh <image-ref> [N]
#
# Example:
#   scripts/measure-startup.sh ghcr.io/ls1intum/hephaestus/application-server:v0.68.0 10
#
# Prints one number per boot to stdout plus a summary (min, p50, p95, max) to stderr.
# Used to produce before/after measurements for #1282 (Application CDS) — capture the output of
# this script against the prior production digest and the new digest, attach to the PR.
#
# Requires: docker, jq, postgres image for the test DB. Reads the `application_ready_time_seconds`
# Prometheus metric from /actuator/prometheus — this is the canonical Spring Boot
# whole-app-readiness gauge, populated by ApplicationStartedEvent (post-ApplicationReadyEvent).
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
# Wait for Postgres to be ready
until docker exec "$PG_NAME" pg_isready -U root -d hephaestus >/dev/null 2>&1; do sleep 1; done

readings=()
for i in $(seq 1 "$N"); do
    name="${APP_NAME_PREFIX}-${i}"
    docker run -d --name "$name" --network "$NETWORK" \
        -e SPRING_PROFILES_ACTIVE=prod \
        -e DATABASE_URL=postgresql://"$PG_NAME":5432/hephaestus \
        -e DATABASE_USERNAME=root -e DATABASE_PASSWORD=root \
        -e MANAGEMENT_SERVER_PORT=8081 \
        -p 0:8080 -p 0:8081 \
        "$IMAGE_REF" >/dev/null
    mgmt_port=$(docker port "$name" 8081/tcp | head -1 | awk -F: '{print $NF}')
    # Wait for readiness (up to 120s); curl exits 0 only on 200 OK
    deadline=$(( $(date +%s) + 120 ))
    until curl -sf "http://localhost:${mgmt_port}/actuator/health/readiness" >/dev/null 2>&1; do
        if [[ $(date +%s) -gt $deadline ]]; then
            echo "Boot ${i} timed out after 120s" >&2
            docker logs "$name" >&2
            exit 2
        fi
        sleep 0.5
    done
    ready=$(curl -s "http://localhost:${mgmt_port}/actuator/prometheus" \
        | awk '/^application_ready_time_seconds /{print $2; exit}')
    if [[ -z "$ready" ]]; then
        echo "Boot ${i}: no application_ready_time_seconds metric" >&2
        exit 3
    fi
    echo "$ready"
    readings+=("$ready")
    docker rm -f "$name" >/dev/null
done

# Summary to stderr
printf '%s\n' "${readings[@]}" | sort -n | awk -v n="$N" 'BEGIN{idx=0}{a[idx++]=$1}END{
    p50_idx=int(n*0.5); p95_idx=int(n*0.95);
    if (p95_idx >= n) p95_idx = n-1;
    printf "min=%.3f p50=%.3f p95=%.3f max=%.3f n=%d\n", a[0], a[p50_idx], a[p95_idx], a[n-1], n
}' >&2
