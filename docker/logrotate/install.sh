#!/usr/bin/env bash
# Install the host-level Docker log-rotation drop-in for the Hephaestus
# production deployment. Must run as root on the host that owns
# /var/lib/docker/containers.
#
# Usage:
#   sudo ./install.sh           # install, then validate via dry-run
#   sudo ./install.sh --force   # install + force-rotate immediately
#   sudo ./install.sh --verify  # re-run dry-run validation only
#
# Idempotent: running twice produces the same state.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="${SCRIPT_DIR}/hephaestus-docker-logs"
DEST="/etc/logrotate.d/hephaestus-docker-logs"

log()  { printf '[logrotate-install] %s\n' "$*"; }
fail() { printf '[logrotate-install] ERROR: %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]]              || fail "must run as root (try: sudo $0)"
[[ -f "$SRC" ]]                || fail "source config not found at $SRC"
command -v logrotate >/dev/null || fail "logrotate is not installed on this host"
[[ -d /var/lib/docker/containers ]] || log "warning: /var/lib/docker/containers is absent — is Docker installed on this host?"

mode="install"
case "${1:-}" in
    --force)  mode="install-and-force" ;;
    --verify) mode="verify-only" ;;
    "")       mode="install" ;;
    *)        fail "unknown flag: ${1}. Accepted: --force, --verify" ;;
esac

if [[ "$mode" != "verify-only" ]]; then
    install -o root -g root -m 0644 "$SRC" "$DEST"
    log "installed: $DEST (mode 0644, owner root:root)"
fi

log "validating via logrotate -d (dry-run, no changes)..."
if ! logrotate -d "$DEST" >/tmp/hephaestus-logrotate.dryrun 2>&1; then
    cat /tmp/hephaestus-logrotate.dryrun >&2
    fail "logrotate config validation failed"
fi
log "config valid"

if [[ "$mode" == "install-and-force" ]]; then
    log "forcing immediate rotation (--force)..."
    logrotate -v -f "$DEST"
    log "rotation complete"
fi

cat <<'EOF'

[logrotate-install] next steps:
  * logrotate.timer (systemd) or /etc/cron.daily/logrotate (cron) already
    runs daily on standard distributions — the first real rotation will
    occur at the next daily tick (typically just after midnight).
  * To force a rotation for verification:
      sudo logrotate -f /etc/logrotate.d/hephaestus-docker-logs
  * To inspect rotated files afterwards:
      ls -la /var/lib/docker/containers/*/*-json.log*
  * To confirm no log line survives past 14 days (run after the system
    has been online for more than 14 days):
      find /var/lib/docker/containers -name '*-json.log-*' -mtime +14
    (expect zero output.)

[logrotate-install] compliance:
  Any change to docker/logrotate/hephaestus-docker-logs is a change to
  the retention cap claimed in the privacy statement and the DSMS
  package. Amend those documents together. See README.md in this dir.
EOF
