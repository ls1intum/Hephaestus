# Hephaestus capacity baseline template

## Reproduction identity

| Field | Value |
| --- | --- |
| Date / operator | |
| Commit, release-lock path and digest | |
| Release-lock signature verification result | |
| Rendered Compose configuration digest | |
| Host provider / machine / region | |
| CPU model, vCPUs, RAM, storage | |
| Linux, Docker, Compose, k6 versions | |
| Application, webhook, Postgres, NATS image digests | |
| Database snapshot / row counts / JetStream state | |
| Test-provider model and latency distribution | |

## Results

| Host | Run | Scenario | Offered load | p50 / p95 / p99 | Error rate | CPU 60s max | Minimum available memory | Filesystem free | Drain time | Result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| | 1 | Webhook burst | | | | | | | | |
| | 2 | Webhook burst | | | | | | | | |
| | 3 | Webhook burst | | | | | | | | |
| | 1 | Detection + mentor | | | | | | | | |
| | 2 | Detection + mentor | | | | | | | | |
| | 3 | Detection + mentor | | | | | | | | |

## Ceiling and conclusion

- First failing step and failure mode:
- Minimum qualified host:
- Recommended qualified host and measured headroom:
- Limits outside the qualified envelope:
- Links to k6 JSON, container stats, metrics and logs:
