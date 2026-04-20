# syntax=docker/dockerfile:1.23@sha256:2780b5c3bab67f1f76c781860de469442999ed1a0d7992a5efdf2cffc0e3d769
#
# Hephaestus Docker-log rotator sidecar.
#
# Pairs with the per-container size cap set by the Docker `json-file` log
# driver in docker/compose.app.yaml and docker/compose.core.yaml. This image
# enforces the time-based (14-day) retention cap claimed in the privacy
# statement and the DSMS submission package:
#
#   webapp/public/legal/profiles/tumaet/privacy.md   §4.6
#   docs/admin/dsms/03-vt-dsms.md                    §13
#   docs/admin/dsms/04-toms.md                       §3.3 + §6
#   docs/admin/dsms/02-dsfa-prescreen.md             §4  + §5
#
# Any material change to the retention behaviour encoded here must be
# reflected in those documents before it is deployed.
#
# Design notes:
# - Alpine + BusyBox `crond` is the minimum viable long-running scheduler
#   (no systemd, no external binaries).
# - `tini` gives us correct PID-1 signal handling so `docker stop` is clean.
# - The entrypoint validates the config on every start (`logrotate -d`),
#   so a broken rotation rule fails the container instead of silently
#   letting logs accumulate past the cap.
# - Heartbeat file `/var/lib/hephaestus-logrotate/healthy` is touched on
#   every successful cron invocation; the compose healthcheck reads it.

FROM alpine:3.23@sha256:5b10f432ef3da1b8d4c7eb6c487f2f5a8f096bc91145e68878dd4a5019afde11

RUN apk add --no-cache logrotate dcron tini \
    # Remove Alpine's default daily logrotate cron entry — we install our
    # own crontab in the entrypoint.
    && rm -f /etc/periodic/daily/logrotate

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod 0755 /usr/local/bin/entrypoint.sh

# Minimal writable state dir (also the healthcheck target).
RUN mkdir -p /var/lib/hephaestus-logrotate

HEALTHCHECK --interval=60s --timeout=5s --retries=3 --start-period=30s \
    CMD pgrep crond >/dev/null \
        && [ -f /var/lib/hephaestus-logrotate/healthy ] \
        && [ $(( $(date +%s) - $(stat -c %Y /var/lib/hephaestus-logrotate/healthy) )) -lt 93600 ] \
        || exit 1

ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/entrypoint.sh"]
