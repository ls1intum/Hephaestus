# Preview deployment setup

The preview application runs one isolated stack per opted-in pull request. Anyone who can push to
this repository adds the `preview` label to their own pull request; the current head
deploys, and every push after that redeploys when its CI run finishes. A preview needs only that
commit's published images, never a passing test suite, so it exists even when the tests are red. Removing the label or closing the pull request removes
the stack and frees its slot. Converting it back to draft does not.

This directory is infrastructure for this repository's own pull requests. It is not part of a
self-hosted Hephaestus install — `docker/self-host/` is that. Contributors use previews through
[CI/CD → Preview Deployments](https://docs.hephaestus.build/contributor/ci-cd).

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
- The database is this preview's own, seeded from staging so it starts with real data. The clone is
  taken with `pg_dump` — staging's data volume is never mounted — and the preview policy runs before
  the application server is allowed to boot: every review trigger, agent binding and sweep schedule
  is disabled, workspace delivery is paused, and active integration connections are suspended.
  Open review jobs, batches, and scans are cancelled. Pending legacy artifact signals are suppressed
  with the existing settings-gate reason so enabling an owned review cannot replay them. Pending feedback sends stop without
  a claim that a provider copy changed.
  Released provider corrections keep their original reasons and stay unresolved with an infinite retry date.
  The clone clears send and projection leases without changing terminal dispatch content.

  The clone engages Silent Mode. The delivery guard refuses provider delivery writes while this mode remains engaged.
  Migrations or local recovery can create due sends after boot. Silent Mode refuses their outbound writes.
  Cleared provider credentials remain a second barrier.

  The instance identity (`login_provider`, `jwt_signing_key`, `issued_jwt`) is dropped so the preview signs its own tokens.
  The seed loader verifies that against the database and refuses to mark the preview seeded
  otherwise, so a policy that silently did not apply leaves the preview un-booted rather than live.

- Staging's provider credentials do not survive the clone either: the instance and workspace AI
  catalog keys are cleared and those connections disabled, every integration connection loses its
  credential bundle and the webhook signing secret its config carries, and the job tokens staging
  minted for its own review jobs are replaced. The policy reads the cloned schema before migrations
  run. It handles both the released review tables and the tables after the review cutover.
  The same verification covers all of them, so a preview that would hold something able to act as staging never boots.
- The application server reads staging's JetStream, so a preview sees the events a shared GitHub App
  delivers there. Its durable is named per deploy, so previews never compete for one consumer, and
  it expires 72h after the preview stops reading — a preview is deleted, not shut down, so it never
  removes its own.
- Agent execution, GitLab and repository checkout are off by default. The operator opt-in below
  permits fresh reviews in one owned test workspace. Inbound webhooks and recurring sync stay off.

No container here holds the Docker socket. A `:ro` socket mount restricts the file and not the API —
a container holding one can still create containers, and from there mount the host — so there is no
scoped way to hold it and nothing does. The seed loader reads staging over the network instead, as a
role that can only read.

Only the seed loader and the application server join staging's network, the first to reach its
database and the second its broker. The preview's own database and the SPA stay on Coolify's
per-preview network — the one it creates and attaches its proxy to. This file defines no network of its own:
Coolify runs every preview of this application under one Compose project named after the application
UUID, so a network defined here would be `<uuid>_backend` for all of them at once, private-looking
and shared. `staging-shared` is joined by name, which makes it a decision rather than a side effect.

PostgreSQL keeps a PR-specific volume. Nothing else in the stack keeps durable state. The application
server's checkout cache is under `/tmp` and disappears when its container is recreated.

The database and raw application API each publish one random TCP host port, bound to loopback by
default. The fresh-review opt-in permits the private worker gateway below instead. No webapp,
management, broker or additional port is published. No service in this stack mounts a Docker socket.

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
the stack gains a way out of its sandbox — a socket, a build stage, an unapproved host binding, an
unbounded memory limit, a network every preview would share, or a flipped integration switch.
Both the default and opted-in render are checked.
`scripts/check-preview-stack.ts` is the authoritative list; this paragraph is not.

## Host capacity

Memory and CPU ceilings are set per service in `compose.app.yaml`; budget roughly 3 GiB per slot.
The CPU ceilings deliberately oversubscribe a small host: a preview is idle almost all the time, and
a ceiling stops one stack taking the box rather than reserving capacity for it.

`PREVIEW_MAX_ACTIVE` caps concurrent previews and defaults to 3. It has to leave roughly 3 GiB of
memory per slot in whatever staging is not already using — check `free -g` on the host before raising
it, and move previews to their own destination rather than raising it much further. At the cap the
controller refuses the next preview and names the pull requests holding the slots. Deployments are
serialized through one Actions concurrency group, so previews never start at once.

## Fresh reviews in an owned test workspace

This is an explicit operator exception for one trusted pull request, using the normal preview URL,
login and cloned database. Reuse approved GitHub sign-in when its existing callback covers the
controlled preview namespace, as described below. GitLab workspace creation still requires an actual linked identity
at the configured GitLab origin. It does not enable reviews for cloned course workspaces or install a
worker in this Compose stack. First qualify a separate rootless Docker engine for the worker and
its sandboxed review containers. Never mount the host's rootful Docker socket into a preview.

Coolify's preview variables apply to the whole application. Set these preview-scoped variables only
for the approved trial:

| Variable                            | Value                                                          |
| ----------------------------------- | -------------------------------------------------------------- |
| `PREVIEW_FRESH_REVIEW_ENABLED`      | `true`                                                         |
| `PREVIEW_FRESH_REVIEW_PR`           | The approved pull request number                               |
| `PREVIEW_WORKER_BIND_HOST`          | `172.19.0.1`, the approved private host gateway                |
| `PREVIEW_WORKER_REGISTRATION_TOKEN` | A new private token shared only with this worker               |
| `PREVIEW_AGENT_IMAGE_REFERENCE`     | The qualified `agent-pi` image at its immutable SHA-256 digest |

The seed loader checks that the native preview hostname starts with the exact `pr<number>.` label,
including on an already-seeded database. A missing or different PR, invalid bind, absent token or
unpinned agent image refuses application startup. Other previews cannot start in this mode until
the operator restores the defaults. This guard is admission at startup; changing application
variables does not stop an already-running preview.

Use normal label teardown and recreation for a preview seeded before this policy change, or after
branch-only migration changes. The marker table's policy comment must attest the strengthened seed policy before an opt-in may
reuse it. The marker contains no row with a seeding timestamp. Do not clear checksums or edit the source database to reuse an old volume.

Read the two actual random bindings from the generated deployment. Connect the isolated worker to
this preview's PostgreSQL at `172.19.0.1:<postgres-port>` and to the raw application endpoint at
`ws://172.19.0.1:<appserver-port>/api/workers/connect`. Its token exchange endpoint is the same raw
origin at `/api/workers/exchange`. Use the preview database user and password, matching credential
encryption key/version, registration token and agent digest. Do not use the seed role, source database
or public edge for worker control. The worker's database transport must be TLS or use the existing
explicit insecure-remote opt-in for this same-host private bridge. The worker and its sandboxes must
use the same isolated engine and private checkout volume. This stack supplies neither a worker nor
rootful socket access.

Keep Silent Mode engaged and cloned connections suspended, model keys absent, bindings disabled,
delivery paused and schedules/triggers off. Through the normal UI, create a new owned GitLab test
workspace, connect its scoped token, configure its model and consent, and enable only its manual
review binding. Do not reactivate a cloned course workspace. The worker polls the preview database
without a workspace allowlist, so the seed policy is part of this boundary. GitLab is enabled for
manual setup and sync, but the blank external webhook URL prevents hook registration. Shared NATS
uses a separate preview durable. It is not an event-isolation boundary. Suspended cloned connections
do not acquire active subscriptions.

After the trial, stop the owned worker, restore `PREVIEW_FRESH_REVIEW_ENABLED=false`,
`PREVIEW_WORKER_BIND_HOST=127.0.0.1`, and clear the selector, registration token and agent image.
Recreate the preview to apply those defaults and remove its fixture through the normal teardown.
Keep the application's ordinary preview policy execution-off.

## Coolify configuration

Create one Docker Compose application for `hephaestus-build/Hephaestus` on branch `main` and point it at
`/docker/preview/compose.app.yaml`.

1. Keep normal automatic deployment off. Disable the repository webhook that targets Coolify's
   manual endpoint. Enable preview deployments so the Actions controller's signed webhook may create
   and update PR records. This application's public/manual source is not selected by the normal
   GitHub App webhook.
2. Do not select **Deploy preview** in Coolify. That UI action bypasses attestation checking and the
   admission limit.
3. Give the webapp the wildcard domain `https://pr<id>.<preview zone>`, and give the appserver
   **the same host with the `/api` path**, `https://pr<id>.<preview zone>/api`. Coolify routes that
   path to the application server and, with **Strip Prefix** left enabled (its default), removes the
   prefix before the container sees it — the shape production serves. Do not give the appserver a
   hostname of its own: the session and CSRF cookies are `__Host-` prefixed, so they bind to one
   host, and a page on a sibling hostname cannot read the CSRF cookie it must send back, which
   refuses sign-out and every other write. The web hostname must match the
   `COOLIFY_PREVIEW_URL_TEMPLATE` repository variable below — that variable is the single place the
   zone is written down.

   Two things do not follow the compose file and must be changed with it:

   - **Domains already stored for an existing preview.** Coolify keeps each preview's domains in its
     own record, so a preview created under the old sibling-hostname layout keeps sending `/api`
     requests to the webapp until its record is updated or the preview is torn down and re-created
     through the label.
   - **Configured GitHub sign-in.** Reuse one approved OAuth app, including staging's, when an
     existing callback has wildcard matching enabled and covers the controlled preview hosts.
     The root domain and port must match. The preview's `/api/login/oauth2/code/github` path must
     match the callback path or fall below it. This needs no app or callback registration per PR.
     Moving the proxy does not update that coverage. Verify it against
     [GitHub's redirect URL contract](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#redirect-urls).
     Use wildcard matching only for subdomains and paths the operator controls.

4. Set every value in `.env.example`. Compose rejects a blank required value before any container
   starts. Keep database, state-cookie, encryption, webhook and signing identity private to the
   preview. The broker and seed-role credentials must match staging. Approved GitHub OAuth client
   credentials may come from staging when the callback coverage above is already configured.
   PR application code can read its OAuth client secret. Sharing a client means trusting every
   eligible preview branch and its later pushes with that secret; wildcard matching does not
   isolate it. If that trust does not hold, use one preview-only client covering all controlled
   preview hosts. No client needs to be registered per PR.
   Do not copy the source instance's environment or other credentials.
5. Leave `SOURCE_COMMIT`, `COOLIFY_BRANCH`, and the `SERVICE_*` variables to Coolify. Define nothing
   on this application beyond `.env.example`; anything else does not belong here.
6. Disable **Connect to predefined network**. Coolify attaches its proxy to each generated preview
   network; the services do not need the shared `coolify` network. The one network this stack joins,
   staging's `shared-network`, it names itself.
7. Deploy onto the host running staging: the stack reaches staging's database and broker over its
   `shared-network`, which is local to that host. A seed source that is unreachable fails the
   preview rather than quietly starting it on an empty database.
8. Create the seeding role once, on staging's PostgreSQL, and put its password in
   `PREVIEW_SEED_SOURCE_PASSWORD`. `pg_read_all_data` is the whole grant — the role can read every
   table `pg_dump` needs and write nothing:

   ```sql
   CREATE ROLE preview_seed LOGIN PASSWORD '<generated>';
   ALTER ROLE preview_seed WITH NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 4;
   GRANT pg_read_all_data TO preview_seed;
   GRANT CONNECT ON DATABASE hephaestus TO preview_seed;
   ```

   Rotating it is one `ALTER ROLE … PASSWORD` and one Coolify variable. Between the two, seeding
   fails and previews stay down rather than coming up on an empty database.

## GitHub configuration

Create a repository label named exactly `preview`. It is the opt-in switch, so give it a description
that says so.

Set these repository variables:

| Variable                       | Purpose                                            |
| ------------------------------ | -------------------------------------------------- |
| `COOLIFY_URL`                  | Coolify HTTPS origin                               |
| `COOLIFY_APP_UUID`             | Preview application UUID                           |
| `COOLIFY_PREVIEW_URL_TEMPLATE` | Public web URL containing `{pr}`                   |
| `PREVIEW_MAX_ACTIVE`           | Concurrent preview limit (optional; defaults to 3) |

Set two repository secrets:

| Secret                           | Scope                                                                    |
| -------------------------------- | ------------------------------------------------------------------------ |
| `COOLIFY_PREVIEW_WEBHOOK_SECRET` | Must equal this application's Coolify **Manual Webhook Secret (GitHub)** |
| `COOLIFY_PREVIEW_READ_TOKEN`     | Coolify API token with the `read` ability                                |

The controller needs no Coolify mutation token: the HMAC-authenticated webhook queues deployments and
requests preview cleanup, and the read token only follows deployment status.

### Rotating them

Each rotation fails closed, so a half-finished one blocks previews rather than weakening them.

- **`COOLIFY_PREVIEW_WEBHOOK_SECRET`** — set the new value in Coolify's **Manual Webhook Secret
  (GitHub)** and in the repository secret. Between the two, deploys and teardowns are refused by
  Coolify's signature check; the nightly reconcile retries teardown once both agree.
- **`COOLIFY_PREVIEW_READ_TOKEN`** — issue the new `read` token first, update the secret, then revoke
  the old one. A deploy in flight reports "cannot read deployment status" and the next push retries.

Leaving these unset disables previews. Every preview workflow is guarded on them, so nothing runs and
nothing fails.

## Teardown

Closing a pull request, merging it, or dropping the `preview` label sends Coolify a signed close
event, and the GitHub deployment is then marked inactive so the
slot is free. Coolify queues that teardown and answers immediately, so the workflow reports that the
request was accepted, not that the containers are gone.

Nothing here checks the host. That is a deliberate scope choice: verifying teardown would mean a
standing credential on the deployment host. If leaks turn up, the nightly `Preview reconcile`
workflow is where that check belongs — it already walks every retained preview and re-sends the close
event.

Preview images are pulled per commit and are never removed by this system.
