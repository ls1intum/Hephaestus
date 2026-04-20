#!/bin/sh
# Hephaestus Docker-log rotator entrypoint.
#
# Validates the logrotate config at boot, installs a daily crontab, seeds
# the healthcheck heartbeat, and execs crond in the foreground.
set -eu

CONFIG=/etc/logrotate.d/hephaestus-docker-logs
STATE_DIR=/var/lib/hephaestus-logrotate
STATE_FILE="${STATE_DIR}/state"
HEALTHY_FILE="${STATE_DIR}/healthy"

mkdir -p "${STATE_DIR}"

if [ ! -r "${CONFIG}" ]; then
    echo "[hephaestus-logrotate] fatal: ${CONFIG} not readable — check compose configs:" >&2
    exit 1
fi

# Validate the rule. `logrotate -d` is debug / dry-run — no side effects.
# If the config is malformed, abort and let the Docker restart policy surface it.
echo "[hephaestus-logrotate] validating config at ${CONFIG}"
logrotate -d -s "${STATE_FILE}" "${CONFIG}" >/dev/null

# Seed the healthcheck heartbeat so the first healthcheck after start_period
# has something to read.
touch "${HEALTHY_FILE}"

# Install the crontab. 02:17 UTC is deliberately off the hour to spread load
# away from the host's other daily cron ticks. BusyBox crond reads minute-
# level fields only, no seconds.
cat > /etc/crontabs/root <<CRON
17 2 * * * /usr/sbin/logrotate -s ${STATE_FILE} ${CONFIG} && touch ${HEALTHY_FILE}
CRON

echo "[hephaestus-logrotate] starting crond (foreground, log to stdout)"
exec crond -f -d 8 -L /dev/stdout
