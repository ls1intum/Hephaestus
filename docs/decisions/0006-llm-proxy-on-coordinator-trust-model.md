# ADR 0006: LLM proxy stays on the coordinator (BYO trust model)

**Status:** Accepted
**Date:** 2026-05-20
**Authors:** Server foundations epic (#1097)

## Context

Sandboxed agent containers running inside Hephaestus call out to LLM providers
(Anthropic, OpenAI). The credentials needed (API keys, OAuth tokens) MUST NOT leak
into the sandboxed code — that would let any user-submitted code exfiltrate the
keys.

The existing `LlmProxyController` is the implementation: agent containers connect
to a host-local proxy, the proxy injects the real API key, the upstream call goes
out. The question is which JVM hosts the proxy when the worker is split out:

- **Coordinator-hosted (server JVM)** — workers send LLM traffic over HTTPS back to
  the server, which holds the credentials. Matches the modern BYO-runner pattern
  (Buildkite Vault OIDC, Anthropic Claude Code git proxy, Temporal payload codec,
  Fly macaroons).
- **Worker-hosted (worker JVM)** — workers proxy locally; LLM credentials live on
  the worker.

An earlier principal-engineer recommendation said "move to worker" — that
recommendation was wrong for the BYO model and we'd have shipped a credential
isolation breach.

## Decision drivers

- Future BYO-runner pattern: course operators may run their own worker pods. We
  cannot trust those pods with our LLM API key.
- Industry research is decisive: every modern execution platform that handles
  untrusted code keeps secrets on the coordinator, not the runner.
- Today's single-JVM topology can use either model — we want option value for the
  future, with the safe default today.

## Considered options

1. **Capability flag (`hephaestus.sandbox.llm-proxy.enabled`); default true on
   server; off-by-default on worker** — preserves both modes for future
   deployment-time choice.
2. **Hardcode on coordinator** — simpler, but loses the future managed-mode
   worker hosting option (where we control the worker and can localhost the proxy
   for lower latency).
3. **Hardcode on worker** — would commit to the wrong default for the BYO case.

## Decision

Option 1. Both `LlmProxyController` and `LlmProxySecurityConfig` carry
`@ConditionalOnProperty(hephaestus.sandbox.llm-proxy.enabled, matchIfMissing=true)`.
Default true everywhere, including worker — meaning today's single-JVM deploys keep
working unchanged.

When the worker is split:

- **BYO trust model (default)**: server pod keeps `llm-proxy.enabled=true`; worker
  pod sets it to false; agent containers route LLM calls back to the server URL.
  Server holds credentials.
- **Managed-mode (future opt-in)**: server pod sets `llm-proxy.enabled=false`;
  worker pod keeps it true; agent containers hit localhost worker proxy. Worker
  holds credentials (acceptable when we operate both server and worker).

## Consequences

- Credential-isolation breach risk closed: no unconditional `@RestController`
  serving `/internal/llm/**`.
- Future BYO-runner epic (managed + BYO modes) gets the right default for free.
- An out-of-band ArchUnit rule (next-epic) can enforce that `LlmProxyController`
  lives in the worker package and never gets cross-referenced from a non-worker
  module.
- LLM call latency in the BYO case includes a server round-trip — accepted
  trade-off for credential safety. Worker → server roundtrip is HTTPS over the
  cluster network; sub-millisecond in typical deployments.

## Revisit trigger

A specific scaling need that requires worker-local LLM proxy (sub-millisecond
hot-path latency on the LLM call) AND the operator owns both worker and server (no
BYO trust concern); or the BYO-runner auth model introduces per-runner key
provisioning that makes worker-local credentials safe to ship.
