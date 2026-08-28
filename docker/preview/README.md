# Preview deployment setup

The preview application runs one isolated stack per opted-in pull request. Anyone who can push to
this repository adds the `preview` label to their own pull request; the current head
deploys, and every push after that redeploys. A preview waits only for CI to publish that commit's
images, never for the test suite, so it exists even when the tests are red. Removing the label, closing the pull request, or converting it back to draft removes
the stack and frees its slot.

This directory is infrastructure for this repository's own pull requests. It is not part of a
self-hosted Hephaestus install — `docker/self-host/` is that. Contributors use previews through
[CI/CD → Preview Deployments](https://ls1intum.github.io/Hephaestus/contributor/ci-cd).

## Security boundary

Pull-request code is untrusted even when it comes from a branch in this repository. These constraints
hold, each enforced before Coolify is asked to deploy:

- Coolify receives a signed synthetic GitHub webhook, HMAC-verified against this application's
  manual webhook secret. Its stock PR handler queues the supplied full commit SHA; the public
  `/api/v1/deploy` endpoint is not used because it queues mutable `HEAD`. Coolify selects the
  application by the webhook's base ref, and the controller always sends the branch that application
  is configured for, so a pull request stacked on another branch still routes to it.
- Every service runs the image CI published for that exact commit — the same artifacts staging and
  production run, buildpack-built application server included. Each digest must carry a GitHub build
  attestation naming this repository's `reusable-docker-build.yml` as signer, checked before Coolify
  is asked to pull it. Nothing is built on the deployment host, so a preview cannot pass on a
  lookalike of the shipped image.
- The stack has a fresh PostgreSQL database and private NATS server. It has no staging network,
  staging database copy, staging encryption key, integration credentials, or Docker socket.
- Agent execution, repository checkout, inbound webhooks, recurring sync, and external integrations
  are disabled. The stack has normal outbound internet access, but no staging credential, Docker
  network membership, or host control socket.

The application server runs without Linux capabilities and with `no-new-privileges`. PostgreSQL and
NATS data live in PR-specific volumes. Coolify's proxy reaches the webapp and API on the frontend
network; the database and NATS broker also use an internal backend network.

Deployment authority is deliberately the same as push authority;
[ADR 0035](../../docs/decisions/0035-pull-request-previews-are-label-gated.md) records why, and what
was declined. Operationally that means the gates decide what deploys, not a person: fork exclusion, a
trusted author association, and refusal for any head that introduces changes to `.github/workflows/**`, `.github/actions/**`, or this
directory. Any base branch in this repository is eligible, so a stacked layer previews the whole
stack up to that layer; only forks are excluded.

Coolify re-reads this directory's Compose file from the commit it is deploying, not from `main`, so a
pull request's own copy of it defines that pull request's stack. Two rules hold that line and neither
works alone: the controller refuses to deploy a head that introduces changes anywhere under
`docker/preview/` — compared against the default branch, so a stacked layer cannot inherit an edit
from the layer below — and `ci-compose-validate.yml` renders the file on every pull request and fails if
the stack gains a way out of its sandbox — a socket, a build stage, a published port, an external
network, an unbounded memory limit, a non-internal backend, or a flipped integration switch.
`scripts/check-preview-stack.ts` is the authoritative list; this paragraph is not.

## Host capacity

Each stack is capped at about 3 GiB (2 GiB application server, 512 MiB PostgreSQL, 256 MiB each for
NATS and the webapp). Its CPU ceilings total 2.0 across the four services, so several previews
oversubscribe a small host on paper. That is intended: a preview is idle almost all the time, and a
ceiling stops one stack from taking the box rather than reserving capacity for it.

`PREVIEW_MAX_ACTIVE` caps concurrent previews and defaults to 3. It has to leave roughly 3 GiB of
memory per slot in whatever staging is not already using — check `free -g` on the host before raising
it, and move previews to their own destination rather than raising it much further. At the cap the
controller refuses the next preview and names the pull requests holding the slots. Deployments are
serialized through one Actions concurrency group, so previews never start at once.

## Coolify configuration

Create one Docker Compose application for `ls1intum/Hephaestus` on branch `main` and point it at
`/docker/preview/compose.app.yaml`.

1. Keep normal automatic deployment off. Disable the repository webhook that targets Coolify's
   manual endpoint. Enable preview deployments so the Actions controller's signed webhook may create
   and update PR records. This application's public/manual source is not selected by the normal
   GitHub App webhook.
2. Do not select **Deploy preview** in Coolify. That UI action bypasses attestation checking and the
   admission limit.
3. Assign the webapp and appserver sibling wildcard domains, shaped `pr<id>.<preview zone>` and
   `pr<id>.api.<preview zone>`. The web hostname must match the `COOLIFY_PREVIEW_URL_TEMPLATE`
   repository variable below — that variable is the single place the zone is written down.
4. Set the values in `.env.example` — all of them. The server validates its production
   configuration before it starts, so a missing `WEBHOOK_SECRET` or `HEPHAESTUS_AUTH_STATE_COOKIE_KEY`
   makes every preview restart-loop rather than fail loudly. Generate new database, state-cookie, and encryption secrets for
   this preview application. A GitHub OAuth app is optional, but if configured it must be
   preview-only.
5. Leave `SOURCE_COMMIT`, `COOLIFY_BRANCH`, and the `SERVICE_*` variables to Coolify. Define nothing
   on this application beyond `.env.example`; anything else does not belong here.
6. Disable **Connect to predefined network**. Coolify attaches its proxy to each generated preview
   network; the services do not need the shared `coolify` network.

## GitHub configuration

Create a repository label named exactly `preview`. It is the opt-in switch, so give it a description
that says so.

Set these repository variables:

| Variable | Purpose |
|---|---|
| `COOLIFY_URL` | Coolify HTTPS origin |
| `COOLIFY_APP_UUID` | Preview application UUID |
| `COOLIFY_PREVIEW_URL_TEMPLATE` | Public web URL containing `{pr}` |
| `PREVIEW_MAX_ACTIVE` | Concurrent preview limit (optional; defaults to 3) |
| `PREVIEW_HOST` | SSH hostname used only for cleanup verification |
| `PREVIEW_HOST_KEY` | Exactly one line, `<host> ssh-ed25519 <key>`, from `ssh-keyscan -t ed25519 <host>`. The host field must equal `PREVIEW_HOST`; other key types are rejected |
| `PREVIEW_SSH_USER` | Forced-command cleanup account |

Set three repository secrets:

| Secret | Scope |
|---|---|
| `COOLIFY_PREVIEW_WEBHOOK_SECRET` | Must equal this application's Coolify **Manual Webhook Secret (GitHub)** |
| `COOLIFY_PREVIEW_READ_TOKEN` | Coolify API token with the `read` ability |
| `PREVIEW_SSH_PRIVATE_KEY` | Key restricted by `authorized_keys` to the host cleanup command |

The controller needs no Coolify mutation token: the HMAC-authenticated webhook queues deployments and
requests preview cleanup, and the read token only follows deployment status.

### Rotating them

Each rotation fails closed, so a half-finished one blocks previews rather than weakening them.

- **`COOLIFY_PREVIEW_WEBHOOK_SECRET`** — set the new value in Coolify's **Manual Webhook Secret
  (GitHub)** and in the repository secret. Between the two, deploys and teardowns are refused by
  Coolify's signature check; the nightly reconcile retries teardown once both agree.
- **`COOLIFY_PREVIEW_READ_TOKEN`** — issue the new `read` token first, update the secret, then revoke
  the old one. A deploy in flight reports "cannot read deployment status" and the next push retries.
- **`PREVIEW_SSH_PRIVATE_KEY`** — add the new public key to `authorized_keys` with the same forced
  command, update the secret, then remove the old entry. Teardown verification fails closed while the
  key is wrong, which holds the preview's admission slot until it is fixed.

Leaving these unset disables previews. Every preview workflow is guarded on them, so nothing runs and
nothing fails.

## Host cleanup contract

Coolify acknowledges preview deletion before remote Docker cleanup is proven — its webhook handler
queues the teardown and answers in the same breath — so the workflow verifies the host itself. It
waits for Coolify's own teardown to finish, forces removal of whatever remains, and then checks that
nothing matching is left. A Coolify 2xx, or a missing preview record, is not proof.

Build the cleanup binary with the source hash baked in, so the nightly
*Preview / Verify host cleanup binary* job can tell whether the installed copy still matches this
repository:

```bash
bun build --compile --target=bun-linux-x64 scripts/preview-host-cleanup.ts \
  --define CLEANUP_BUILD_ID="'$(shasum -a 256 scripts/preview-host-cleanup.ts | cut -c1-16)'" \
  --outfile hephaestus-preview-cleanup
```

Use `--target=bun-linux-arm64` on an ARM host and set the `PREVIEW_HOST_ARCH` repository variable to
`arm64`; the binary reports its own architecture, so a mismatch is named rather than left as a silent
failure to run.

Install it as `/usr/local/sbin/hephaestus-preview-cleanup`, root-owned, behind a forced SSH command.
It accepts `list`, `version`, `prune`, and `cleanup <pr>`. Use an Ed25519 key whose `authorized_keys` entry
starts with `restrict,command="/usr/local/sbin/hephaestus-preview-cleanup"`. Rebuild and reinstall it
whenever `scripts/preview-host-cleanup.ts` changes; the nightly job turns red the next morning if you
forget. Previews keep working meanwhile — cleanup still runs, it just runs an older rule set — so that
job going red is a next-morning task, not a page.

Write `/etc/hephaestus-preview-cleanup.conf`, root-owned and neither group- nor world-writable — the
binary refuses to run otherwise, and rejects any key it does not recognise:

```ini
COOLIFY_APP_UUID=<same value as the COOLIFY_APP_UUID repository variable>
COOLIFY_APPLICATION_ID=<the numeric id in the application's Coolify URL>
# Optional. How many five-second rounds to let Coolify finish its own teardown before forcing
# removal. Defaults to 12; 0 forces immediately, 60 is the maximum.
COOLIFY_CLEANUP_GRACE_ATTEMPTS=12
```

Both identifiers name the same application and both are required: the UUID scopes preview volume and
network names, and the numeric id is the `coolify.applicationId` container label. Cleanup
removes only resources carrying that label and the deterministic PR suffix, then verifies that no
matching container, volume, or network remains.

The nightly reconcile runs `prune`, which reclaims dangling image layers. It deliberately does not
remove unreferenced *tagged* images: preview and staging pull the same repositories on one daemon, and
an image staging keeps only as a rollback target has no container referencing it either. Superseded
preview tags therefore accumulate — they share most layers, so growth is slow, but watch disk on the
host and remove old tags by hand when it matters. A failed check leaves the workflow red so the scheduled
reconcile run retries.

After host verification, GitHub keeps one inactive cleanup tombstone so the nightly second pass can
repeat Coolify cleanup before retiring the record; admission ignores only tombstones carrying that
verified state. The nightly reconcile handles at most 100 candidates per run and warns when it
truncates — that warning means several nights of backlog, not a single failure.
