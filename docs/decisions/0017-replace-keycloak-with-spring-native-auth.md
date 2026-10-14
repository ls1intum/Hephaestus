# ADR 0017: Native accounts and federated sign-in

**Status:** Accepted
**Date:** 2026-10-14
**Authors:** Hephaestus maintainers

## Context

The application needs an account principal without a separate Keycloak deployment.
A provider mirror identifies source activity, not a sign-in principal.

## Decision

Spring Security federates sign-in through configured GitHub or GitLab login providers.
Hephaestus issues its own cookie sessions and keeps refresh lineage in the token registry.
Provider identity lookup uses exact provider, native subject and team keys, never email or a mutable login.
Slack and Outline remain link-only providers.

The account owns choices and native authority.
Membership joins accounts to workspaces, as [ADR 0019](0019-workspace-membership-keyed-on-account.md) specifies.
SCM users remain provider mirrors for activity and attribution.
Unclaimed roster records have no sign-in credential.
A verified sign-in can claim one without creating a second person.

Recent sign-in checks protect privileged actions.
An instance-admin view keeps the acting administrator and records the viewed account and exact provider actor separately.
It grants no developer authority and proves no private feedback delivery.
The [auth glossary](../auth-glossary.md) owns token and identity details.

## Consequences

No Keycloak service is required in supported deployment.
Account export and erasure use the native privacy coordinator.
Provider identities and source-person restrictions retain exact authority state during migration and roster sync.
MFA remains the configured provider's responsibility.

## Revisit trigger

Revisit when a shipped login provider cannot preserve exact native identity or required session guarantees.
