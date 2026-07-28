# ADR 0006: Server-side OpenAI-compatible model catalog and LLM proxy

**Status:** Accepted (revised 2026-07-22, #1368)
**Date:** 2026-05-20
**Authors:** Server foundations epic (#1097)

## Context

Practice-review and mentor agents run inside sandboxed containers. A sandbox must be able to call an
LLM without receiving the upstream credential. At the same time, instance administrators need one
place to govern shared models and prices, while a workspace administrator may optionally connect a
workspace-funded endpoint.

“Provider” was previously overloaded to mean a vendor, an HTTP API shape, an authentication scheme,
and a pricing source. That does not fit OpenAI-compatible gateways: the same Chat Completions or
Responses API can be hosted by OpenAI, Azure OpenAI v1, or a self-hosted service with different
endpoint and authentication details.

Provider responses report token usage, not a trustworthy dollar cost. OpenAI's
[Responses API](https://developers.openai.com/api/reference/resources/responses/methods/create) reports
input, cached-input, output, and reasoning-token details, while its
[Chat Completions API](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)
reports the corresponding prompt/completion usage. Reasoning tokens are included in output tokens,
so pricing them again would double-charge. Published prices are separate from the response contract
and may differ for another compatible host.

Compatibility is an API property, not a vendor taxonomy. For example, vLLM's
[OpenAI-compatible server](https://github.com/vllm-project/vllm/blob/main/docs/serving/online_serving/openai_compatible_server.md)
implements both Chat Completions and Responses while documenting endpoint-specific differences. That
is why the catalog stores the exact wire API and opaque upstream model ID instead of a growing
provider enum.

## Decision

### One catalog, two exact wire APIs

The catalog supports only:

- OpenAI Chat Completions (`POST /chat/completions`), and
- OpenAI Responses (`POST /responses`).

Vendor names are form presets, not persisted runtime types. Azure OpenAI is represented only as a
create-time Azure v1 preset: its base URL and API-key authentication still persist as the same generic
OpenAI-compatible connection. This follows Microsoft's
[v1 API guidance](https://learn.microsoft.com/azure/ai-foundry/openai/api-version-lifecycle), which
uses the standard OpenAI client and `/openai/v1/` base URL without a per-request `api-version`.

An instance connection or workspace connection owns an immutable base URL, wire API, authentication
mode, and generated slug. Authentication is deliberately limited to `BEARER` and `API_KEY`; arbitrary
header names and value prefixes are not configuration. Rotating or clearing the encrypted key,
renaming, enabling, and disabling remain lifecycle operations. Changing routing identity means
creating a new connection and rebinding.

A model owns an immutable upstream model identifier plus the small capability envelope required by
the agent runtime. Models and connections start inactive. A model can be activated only when its
connection is active and its price is explicitly `PRICED` or `NO_CHARGE`. An instance model is either
public or granted to named workspaces; workspace-owned models remain tenant-scoped.

### Server-side credential and request enforcement

The JVM that hosts the sandbox capability also hosts the LLM proxy. The sandbox receives only a
short-lived, job/turn-scoped bearer token. The proxy resolves the encrypted upstream key from the
live catalog on every call, so rotation and revocation take effect without placing a secret in a job
snapshot, container environment, ledger, or log.

The proxy is not a general-purpose forwarder. It accepts only the exact POST path selected by the
token's catalog binding, rejects query strings and unsupported hosted tools, replaces the request
body’s model with the authorized immutable upstream model, strips inbound credentials, and derives
the one permitted upstream authentication header from the stored authentication mode. It also removes
`service_tier`: OpenAI documents that this field selects processing with different pricing and
performance, which would invalidate the catalog's one declared price tuple. This prevents a sandbox
from using a valid token to select a different model, price tier, or provider endpoint.

All provider base URLs pass the shared egress policy at configuration time and connect time. The
policy requires public HTTPS (except the explicit local-development loopback switch), rejects
userinfo/query/fragment components and non-public address ranges, and optionally applies an
instance-admin hostname allowlist. This implements the allowlist-first guidance in the
[OWASP SSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html).

### Admission and accounting

Jobs revalidate the live model, connection, tenant/grant, protocol, and price while claiming the
queue row, before a sandbox starts. Mentor turns perform the same admission before creating a thread
or message. The complete applicable price tuple is frozen at that point; completion never looks up a
new price or trusts a runner-reported model name.

The usage ledger is written in the same database transaction as the terminal job attempt or mentor
message. Its deterministic identity includes source type, source id, and attempt number, so a real
job retry records a separate spend without making a database retry bill twice. Unexpected accounting
failure rolls back the terminal write instead of silently losing cost. Budget alerts run after
commit. Prices are operator-declared from the applicable contract or hosting cost; Hephaestus does
not scrape a vendor pricing page or treat an absent cost as zero. `NO_CHARGE` is an explicit audited
declaration.

## Consequences

- Upstream secrets remain outside sandboxes and immutable snapshots.
- Instance admins have one shared-model and price-governance flow; workspace admins see only models
  they can use and, when enabled, their own connections.
- Disabling a connection/model or removing a grant blocks the next admission and the next proxy call.
- Repricing cannot change the recorded cost of work already admitted.
- The schema does not contain Azure API versions, vendor adapters, arbitrary auth headers,
  model-level protocol overrides, or unused modality/cache-control knobs.
- A provider that has no known price cannot be activated accidentally; historical unverifiable usage
  remains visible rather than being reported as free.

## Rejected alternatives

- **Keys in worker/sandbox configuration:** violates the sandbox trust boundary.
- **A catch-all provider proxy:** turns a scoped LLM credential into a general upstream capability.
- **Completion-time price lookup:** makes historical cost change when an administrator reprices.
- **Provider-supplied dollar cost:** it is not part of the OpenAI usage response and is not portable
  across compatible endpoints.
- **Azure-specific protocol and `api-version` columns:** obsolete for the Azure v1 surface and adds a
  second runtime path without a distinct agent requirement.
- **An accounting outbox or hard reservation system:** unnecessary for the same-PostgreSQL terminal
  transactions and outside the issue's eventually-consistent budget semantics.
- **Delegating accounting to an external gateway:** gateways such as LiteLLM expose
  [key/user budgets](https://docs.litellm.ai/docs/proxy/users), but that would add another stateful
  control plane and would not atomically join Hephaestus job/mentor outcomes to their workspace and
  retry attempt. The application-owned ledger remains authoritative even when the configured endpoint
  happens to be a gateway.
