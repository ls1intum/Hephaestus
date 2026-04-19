# Host-level Docker log rotation

Gives Hephaestus container access logs a deterministic **14-day** time-based retention cap. Required for the Art. 5(1)(c) + Art. 32(1)(b) GDPR retention claims in the privacy statement and the DSMS package.

## Why a host-level config is needed

The Docker `json-file` log driver rotates **only by size**, not by age. In `docker/compose.app.yaml` and `docker/compose.core.yaml` every service is pinned to `max-size=50m` × `max-file=5` (app services) or `max-size=10m` × `max-file=3` (core infrastructure) — a per-container size safety net. But a quiet container could retain old log lines for weeks or months if its traffic is below the rotation threshold. That makes any "retention cap" claim time-unbounded, which is not acceptable under a size-only configuration.

`logrotate` fills the gap. Docker supplies the size cap; `logrotate` supplies the age cap. Together they bound both disk usage and log-line age.

## Install (once per host, as root)

```bash
cd /path/to/Hephaestus/docker/logrotate
sudo ./install.sh
```

The install script:

1. Copies `hephaestus-docker-logs` to `/etc/logrotate.d/hephaestus-docker-logs` (mode `0644`, owner `root:root`).
2. Validates via `logrotate -d` (dry-run, no side effects).
3. Tells you how to force an immediate rotation for verification.

Idempotent: re-running produces the same state.

Optional: run with `--force` to rotate immediately after install, or `--verify` to re-run validation without reinstalling.

```bash
sudo ./install.sh --force
sudo ./install.sh --verify
```

## What the config does

```
/var/lib/docker/containers/*/*-json.log {
    daily           # once per day
    rotate 14       # keep 14 daily snapshots — oldest auto-deleted
    missingok       # do not fail if a container is absent
    notifempty      # skip empty logs
    compress        # gzip archived logs
    delaycompress   # keep yesterday's log uncompressed for easy inspection
    copytruncate    # required: Docker keeps the log file open
    dateext         # filenames: <id>-json.log-20260419
    dateformat -%Y%m%d
}
```

- **`copytruncate`** is required because the Docker daemon holds the json-file log open and does not support a re-open signal. The standard rename-then-create pattern would leave Docker writing to a deleted inode. The brief copy-then-truncate race is acceptable for access-log data.
- **`dateext`** is required so this file's rotation suffixes (`-20260419`) do not collide with the numeric `.1`, `.2`, … suffixes produced by the json-file driver's own size-based rotation.
- The glob `*-json.log` matches only the active log file; segments already rotated by Docker are left untouched.

## Verify after install

```bash
# Immediately after install, one active log per container:
ls -la /var/lib/docker/containers/*/*-json.log

# After one daily tick (or after `sudo logrotate -f ...`), one active
# plus one dated archive per container:
ls -la /var/lib/docker/containers/*/*-json.log*

# After 14+ days online, no archives older than 14 days:
find /var/lib/docker/containers -name '*-json.log-*' -mtime +14
# (expect zero output)
```

## Operator notes

- `logrotate.timer` (systemd) or `/etc/cron.daily/logrotate` (cron) already runs daily on standard distributions. No additional timer is needed.
- Disk usage per container after steady state: ≤ (50 MB active) + (4 × 50 MB internal Docker rotation) + (14 × ≈5 MB compressed dated archives) ≈ 320 MB per app service.
- To inspect yesterday's log of a specific service:
  ```bash
  cat /var/lib/docker/containers/<id>/<id>-json.log-$(date -d yesterday +%Y%m%d)
  ```

## Compliance alignment

This configuration is the host-level enforcement cited in:

- `webapp/public/legal/profiles/tumaet/privacy.md` §4.6 (server-log retention)
- `docs/admin/dsms/03-vt-dsms.md` §13 (retention table)
- `docs/admin/dsms/04-toms.md` §3.3 + §6 (TOMs availability + deletion)
- `docs/admin/dsms/02-dsfa-prescreen.md` §4 + §5 (residual-risk mitigation)

Any change to `hephaestus-docker-logs` is a change to the retention cap claimed in those documents. Amend both together. Material drift must be reflected in the VVT re-submission.
