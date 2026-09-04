# ADR 0042: Hosts pull their own releases

Date: 2026-09-03

## Status

Accepted. Supersedes the SSH-push half of ADR 0005's deployment story; the release evidence and
image-lock decisions it builds on are unchanged.

## Context

Deployments pushed from CI over SSH through a jump host. That path put two long-lived private keys
in GitHub environment secrets, required an internet-reachable SSH entry point, and rendered roughly
fifteen production secrets onto a third-party runner's disk on every deploy. Anyone able to run a
workflow in the deploy environment, or to exfiltrate through a compromised action, held a shell on
production.

It also broke. The repository's transfer between organisations did not carry org-level variables and
secrets; they were recreated with a hostname that resolves nowhere, a user the host refuses to log
in as, and a jump-host key that does not authenticate. Staging could not be deployed at all. None of
those failures were detectable before a deploy attempt, because the credentials only exist inside CI.

The project already produces everything a host needs to deploy itself: a cosign-signed release lock
naming every image by digest, and `prepare-release-lock.ts`, which verifies that signature and
renders the lock. Self-hosters already upgrade this way by hand.

## Decision

A host polls a channel and applies the release it names. Nothing connects to the host.

**The channel is a commit.** Each environment has one file on the `deploy-state` branch naming a
release. The branch carries no code and triggers no workflow.

**Freshness comes from the commit graph.** A signature proves authorship, not currency: a valid old
channel, replayed, verifies. The reconciler accepts only a channel commit that descends from the last
one it accepted. Moving to a lower release additionally requires `allowRollback` on that new commit;
the flag never authorizes replay. This is the property TUF builds timestamp and snapshot roles for,
taken from a history the repository already keeps — which is also why the channel is not an OCI
artifact under a mutable tag, where no such order exists and where GHCR offers no tag immutability.

**Approval is bound by identity, not by claim.** Fulcio does record GitHub's `environment` claim, but
cosign exposes no way to verify it, so requiring "signed by a job in the Production environment" is
not something a verifier can express today. Instead the hosts pin the identity of `promote.yml`, and
a GitHub environment gates that workflow: a signature carrying that identity is proof the reviewers
approved. This holds only while `promote.yml` stays non-reusable — a `workflow_call` trigger would
let any caller mint the same identity.

**An environment may follow the default branch instead of a release.** A channel names either a
release or a commit. A release is the promoted artifact: its lock, provenance and signature are
fetched and verified, which is how production moves. A commit has no release to fetch, so the
channel carries the digests to run and the channel's own signature covers them; provenance comes
from GitHub's build attestations, checked per image, which make the same claim a release lock's
signature makes. Every image is still pinned by digest and still has to have been built by this
repository's workflow on a hosted runner, so nothing is weakened — and staging stops waiting for a
release to be cut before it reflects the branch. Only a commit already on the default branch may be
followed.

**A failed apply stops.** It does not roll back. Schema changes are forward-only here, so restoring
the previous images would leave old code on a migrated database. Host monitoring reads the failure
and staleness metrics instead.

**Feedback is observed, not reported.** Because nothing can push the hosts, the promotion workflow
polls the environment's public runtime configuration until it reports the promoted version. A deploy
that does not converge fails in Actions, and a host that stops reconciling raises a staleness alert
from its textfile metrics. Without that alert this design trades a loud failure for a silent one.

## Consequences

Both deploy keys, the jump host, and the deployment secrets leave CI; secrets live only on the host
that uses them. Promotion becomes one gated workflow run, and the git history of `deploy-state` is
the deployment audit log.

The reconciler is as privileged as the SSH user it replaces: write access to the Docker socket is
root-equivalent, and no systemd hardening changes that. What changes is that no remote party holds
it. Rootless Podman with Quadlet would close the remaining gap and is not attempted here.

An operator must disable the timer before hand-patching a host, or the next tick reverts the fix.

Preview environments are unaffected. They are ephemeral, per-pull-request, and managed by Coolify on
the staging host; the reconciler owns only the Compose projects it is configured for, so the two
manage disjoint resources.

Production adopts this after its outstanding version gap is closed by hand; the mechanism is proven
on staging first.
