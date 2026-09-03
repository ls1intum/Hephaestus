# Compose capacity qualification

These [Grafana k6](https://grafana.com/docs/k6/latest/) scenarios qualify the supported single-host
Compose topology. They spend LLM budget and create durable rows; run them only on an isolated host.

Webhook traffic uses a [constant arrival rate](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/arrival-rate-vu-allocation/).
Pass/fail uses [k6 thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/). Sizing requires
both service objectives and saturation, following Google's SLO guidance
([SRE Workbook](https://sre.google/workbook/implementing-slos/)).

## Safety and prerequisites

- Deploy the exact release from `docker/self-host/compose.yaml` on a dedicated Linux host. Run k6
  elsewhere.
- Populate a representative workspace before testing. `ARTIFACT_IDS` must contain distinct, reviewable
  pull requests so each request performs work rather than hitting deduplication/cooldown.
- Bind practice review and mentor to a deterministic OpenAI-compatible test provider. Record its
  response latency separately: provider time is not host capacity.
- Use a short-lived workspace-admin token. Never commit it or the webhook secret.
- Stop background sync and other tenants, then reset PostgreSQL and JetStream to the same snapshot
  before every candidate. Run three times; publish the median and retain every raw result.

## Run

Use the digest-pinned k6 image:

```bash
vp run test:load:syntax
```

```bash
export BASE_URL=https://hephaestus-load.example.test
export WEBHOOK_SECRET=...
export K6_IMAGE=grafana/k6:1.2.3@sha256:4f82892217f3110cb233e2b2622bcc97fabc70f14bd241fbfbfe7305105c68aa
mkdir -p load-results
docker run --rm -i \
  -e BASE_URL -e WEBHOOK_SECRET -e WEBHOOK_RATE=100 -e DURATION=2m \
  -v "$PWD/load-tests:/tests:ro" -v "$PWD/load-results:/results" \
  "$K6_IMAGE" \
  run --summary-export=/results/webhook-summary.json /tests/webhook-burst.js
```

The mixed scenario runs long-lived mentor HTTP responses while distinct practice reviews execute. It
requires every review request and job to complete:

```bash
export API_BASE_URL=https://hephaestus-load.example.test/api
export AUTH_TOKEN=... WORKSPACE_SLUG=capacity-test
export ARTIFACT_IDS=101,102,103,104,105
docker run --rm -i \
  -e API_BASE_URL -e AUTH_TOKEN -e WORKSPACE_SLUG -e ARTIFACT_IDS \
  -e MENTOR_VUS=5 -e REVIEW_VUS=2 -e REVIEW_REQUESTS=5 -e DURATION=10m \
  -v "$PWD/load-tests:/tests:ro" -v "$PWD/load-results:/results" \
  "$K6_IMAGE" \
  run --summary-export=/results/mixed-summary.json /tests/detection-mentor.js
```

The pinned k6 client buffers each `text/event-stream` response. This scenario measures concurrent
full-response completion, not time to first event or per-event latency.

In separate terminals collect container and host samples. Docker defines the container fields and
their semantics in [`docker stats`](https://docs.docker.com/reference/cli/docker/container/stats/);
in particular, its Linux CLI memory figure subtracts cache, so retain the raw output rather than
copying only a peak percentage:

```bash
(cd docker/self-host && \
  docker compose --env-file .env --env-file release-lock.env stats --format json \
    application-server webhook-server postgres nats-server) \
  > load-results/container-stats.jsonl
```

```bash
vmstat 1 > load-results/host-vmstat.txt
```

Stop both collectors when k6 exits. Capture `df -h` before and after the run. Retain `docker compose
ps`, `docker info`, the release lock, k6 summaries, provider latency, PostgreSQL size, JetStream state,
and application metrics/logs. The increase in `webhook.publish{outcome="success"}` must equal accepted
webhook iterations while the failure counter remains unchanged. Reject a run with a reconciliation
mismatch, dropped iterations, provider throttling, a container restart/OOM, or swap activity.

## Qualification matrix and sizing rule

Run both scenarios at the **minimum** and **recommended** host shapes, then increase webhook rate and
mentor/review concurrency independently until the first threshold or saturation failure. Do not add
the maxima together: that invents a workload never tested.

The documented size qualifies only when all three repeated runs meet thresholds, the busiest rolling
60-second host-CPU window remains below 80%, host available memory stays above 15% in every sample,
the data filesystem has at least 20% free before and after, no swap/OOM/restart occurs, and the review
queue drains within five minutes after load stops. Publish the first failing step as the ceiling.

Copy `baseline-template.md` for every release. Record every repetition and link its raw artifacts.
