# Hephaestus Docker-log rotator sidecar

Enforces a deterministic **14-day** time-based retention cap on Docker container logs for the Hephaestus deployment. Required for the Art. 5(1)(c) + Art. 32(1)(b) GDPR retention claims made in the privacy statement and the DSMS submission package.

Delivered as a Compose-native sidecar: no host install step, no manual VM action. The deploy pipeline (`ls1intum/.github`'s `deploy-docker-compose.yml`, invoked by `.github/workflows/deploy-prod.yml` and `deploy-staging.yml`) ships `docker/compose.app.yaml` to the VM and runs `docker compose up --pull=always -d`. The `logrotate` service comes along for the ride.

## Why a dedicated rotator is needed

The Docker `json-file` log driver rotates **only by size**, not by age. In `docker/compose.app.yaml` and `docker/compose.core.yaml` every service is pinned to `max-size=50m` × `max-file=5` (application services) or `max-size=10m` × `max-file=3` (core infrastructure) — that is a per-container disk-usage safety net. But a quiet container could keep log lines for weeks if traffic is below the size threshold, so a size-only configuration cannot support a bounded-retention claim.

`logrotate` supplies the missing age cap. The two layers are independent: Docker bounds disk; this sidecar bounds age.

## What it is

- Image: `ghcr.io/ls1intum/hephaestus/logrotate`, built from `docker/logrotate/Dockerfile`, published on every merge to `main` by `.github/workflows/ci-docker-build.yml` → `reusable-docker-build.yml` (linux/amd64 + linux/arm64).
- Base: `alpine:3.20` + `logrotate` + `dcron` (BusyBox cron) + `tini` (PID-1 signal handling).
- Entrypoint (`entrypoint.sh`): validates the config with `logrotate -d` at every start (a broken rule fails the container — Docker's `restart: unless-stopped` surfaces it), installs a single crontab line, and execs `crond` in the foreground.
- Healthcheck: reads a heartbeat file written by every cron run; fails the container if the heartbeat is older than 26 h (one daily tick + a 2 h grace) or if `crond` is not running.

## How it is wired

`docker/compose.app.yaml` defines the service:

```yaml
logrotate:
  image: "ghcr.io/ls1intum/hephaestus/logrotate:${IMAGE_TAG}"
  restart: unless-stopped
  volumes:
    - /var/lib/docker/containers:/var/lib/docker/containers:rw
    - logrotate-state:/var/lib/hephaestus-logrotate
  configs:
    - source: logrotate-hephaestus-docker-logs
      target: /etc/logrotate.d/hephaestus-docker-logs
      mode: 0644
  security_opt: [no-new-privileges:true]
  cap_drop:    [ALL]
  cap_add:     [DAC_OVERRIDE, CHOWN, FOWNER]
  network_mode: none
```

The rotation rule is inlined in the same compose file as a top-level `configs:` entry — same pattern already used for `nginx-default-config` and `maintenance-page` in `compose.proxy.yaml`. The inlined rule is the single source of truth. Changing it is a material change to the retention cap.

## What the rule does

```
/var/lib/docker/containers/*/*-json.log {
    daily           # once per day
    rotate 14       # keep 14 daily snapshots — oldest auto-deleted → hard 14-day cap
    missingok       # do not fail if a container is absent
    notifempty      # skip empty logs
    compress        # gzip archived logs
    delaycompress   # keep yesterday's log uncompressed for easy inspection
    copytruncate    # required: Docker keeps the log file open
    dateext         # dated suffixes: <id>-json.log-20260419
    dateformat -%Y%m%d
}
```

- **`copytruncate`** — the Docker daemon holds the json-file log open and has no re-open signal. The standard rename-then-create pattern would leave Docker writing to a deleted inode. The brief copy-then-truncate race is acceptable for access-log data.
- **`dateext`** — disambiguates these rotation suffixes from the numeric `.1`, `.2`, … suffixes produced by the json-file driver's own size-based rotation. The glob `*-json.log` matches only the active file; already-rotated segments are left untouched.
- **No cron tick inside the container = no rotation.** The entrypoint installs `17 2 * * * logrotate … && touch healthy` as the BusyBox crontab. The first rotation happens at the next 02:17 UTC; the heartbeat file `/var/lib/hephaestus-logrotate/healthy` is seeded at boot so the healthcheck has something to probe during the initial window.

## Security posture

- **Non-default capability drop.** `cap_drop: [ALL]`; the sidecar re-adds only `DAC_OVERRIDE`, `CHOWN`, and `FOWNER` — the minimum required for `copytruncate` on root-owned container logs.
- **`no-new-privileges:true`.** Forbids privilege escalation inside the container even through SUID binaries.
- **`network_mode: none`.** The sidecar is disconnected from every Docker network. It has no legitimate reason to reach other services or the internet.
- **No host binds other than `/var/lib/docker/containers`.** No `/var/run/docker.sock`, no broader host filesystem access.
- **`restart: unless-stopped`.** If the config is ever malformed, the validation step at boot exits non-zero and the container restart loop surfaces it in `docker ps`.

## Verify after deploy

On the VM after `docker compose up`:

```bash
# Sidecar is healthy:
docker compose ps logrotate
# → STATUS should include "healthy"

# Config validated at boot:
docker compose logs logrotate | grep validating
# → "[hephaestus-logrotate] validating config at /etc/logrotate.d/hephaestus-docker-logs"

# After 02:17 UTC (or after a forced tick), dated archives appear:
docker compose exec logrotate ls /var/lib/hephaestus-logrotate/
docker compose exec -u 0 logrotate \
    logrotate -f -s /var/lib/hephaestus-logrotate/state \
    /etc/logrotate.d/hephaestus-docker-logs
ls /var/lib/docker/containers/*/*-json.log-*

# After 14+ days of operation, no archive exceeds the cap:
find /var/lib/docker/containers -name '*-json.log-*' -mtime +14
# (expect zero output)
```

## Disk-usage ceiling

Per application service, steady state (after 14 days):

```
active log cap          = max-size × max-file    = 50 MB × 5   = 250 MB
daily archives (14×)    ≈ one daily rotation     = 14 × ~5 MB  =  70 MB
                                                                 -------
                                                                 ≈ 320 MB
```

Per core-infra service: `~10 MB × 3 + 14 × ~2 MB ≈ 60 MB`.

## Compliance alignment

This sidecar is the enforcement mechanism cited in:

- `webapp/public/legal/profiles/tumaet/privacy.md` §4.6 (server-log retention)
- `docs/admin/dsms/03-vt-dsms.md` §13 (retention table)
- `docs/admin/dsms/04-toms.md` §3.3 + §6 (TOMs availability + deletion)
- `docs/admin/dsms/02-dsfa-prescreen.md` §4 + §5 (residual-risk mitigation)

Any change to the `configs.logrotate-hephaestus-docker-logs` block in `docker/compose.app.yaml`, the `Dockerfile`, or the `entrypoint.sh` is a change to the retention cap claimed in those documents. Amend them together. Material drift must be reflected in the VVT re-submission.
