# Changelog

## 0.74.0

### Minor Changes

- You can now self-host Hephaestus on a single Linux server. `docker/self-host/` adds one
  supported Docker Compose stack — application server, webhook receiver, PostgreSQL and NATS
  behind a TLS reverse proxy — that reuses the maintainers' service definitions, so it has no
  second copy to fall out of date. Follow the new [install guide](https://ls1intum.github.io/Hephaestus/admin/install);
  GitHub App setup, manual webhook creation, and backup/restore each have a companion page.

  Existing deployments are unaffected: the reference Compose files are unchanged apart from two
  safe additions — the NATS JetStream limits become overridable (defaults unchanged), and a
  release-pin sanity check that rejected every valid pin is fixed.

- Administrators can now see a history of changes to a workspace's AI-settings controls — who changed a
  setting, when, and from what to what. It covers the practice-review policy, the agent configurations
  bound to practice detection and the mentor, and edits to those agent configurations. Each entry shows
  the field-level before/after, keeps the author — including changes made while impersonating another
  user — and never stores credentials such as API keys. A workspace administrator finds it under
  Administration → "Audit log" for their own workspace; an instance administrator gets a
  cross-workspace view under the instance-admin console. The history is append-only and retained for
  twelve months. The accompanying database migration adds one table and applies automatically, with no
  action required on upgrade.
- The core NATS port can now be exposed beyond localhost. It stays bound to `127.0.0.1` by default;
  set `NATS_BIND_HOST=0.0.0.0` (or a specific interface address) to let other hosts reach the bus — for
  example when a separate environment consumes events from this one's JetStream.

  **Operators:** only expose it on a trusted or firewalled network. The bus is unauthenticated, so a
  public bind puts its contents within reach of anyone who can route to the host.

- The application-server, application-worker and webhook-server containers now have explicit memory
  limits, so each JVM sizes its heap for its own container instead of the whole host. Co-located
  services no longer oversubscribe host memory and push the box into swap.

  **Operators:** defaults are webhook-server 1 GB, application-server 5 GB, application-worker 3 GB,
  overridable via `WEBHOOK_SERVER_MEM_LIMIT`, `APPLICATION_SERVER_MEM_LIMIT` and
  `APPLICATION_WORKER_MEM_LIMIT`. Keep the sum under the host's RAM; raise them on larger hosts.

- The instance-admin console now has a single "Audit log" with two tabs, "Access" and "Settings",
  instead of two separate pages, so there is one place to answer "who did this,
  and when". Both tabs share the same filter bar: filters accept several values at once (for example
  feature-flag _and_ role changes in one view) and the whole selection now lives in the address bar, so
  a filtered view can be pasted into a ticket or a chat and reopens exactly as it was — including links
  shared before a filter value was renamed, which now open the log unfiltered rather than an error page.

### Patch Changes

- The settings-change audit trail now covers workspace administration, not just AI configuration: a
  member's role being granted, changed or revoked, a member being hidden or unhidden, features being
  enabled or disabled, a practice being activated or deactivated, the workspace being paused or purged,
  the SCM access token being rotated, and public visibility being toggled are all recorded with who did
  it and the before/after. Credentials are never stored — a token rotation records only that a token was
  rotated, and when. Connecting or disconnecting an integration continues to be recorded on the
  connection's own history. The database migration widens an existing constraint and applies
  automatically, with no action required on upgrade.
- Stops writing Tomcat file access logs in production. They were written to a
  `/var/log/hephaestus` volume that wasn't shipped or aggregated anywhere, and the
  requirement for a writable directory there made containers fail to start on a
  fresh volume. Per-request logging is unchanged — the reverse proxy already
  records every request at the edge. This also removes the now-unused log volumes
  from the compose stacks.
- Practice-detection runs now record exactly how each result was produced — the model and prompt version
  that ran, and a fingerprint of the inputs the detector actually saw — and every piece of feedback the
  system prepares is now logged as either delivered or withheld with a reason. This makes the evaluation
  data gathered during the shadow-review phase trustworthy: a result can always be traced back to what
  generated it, and a developer's reaction to feedback can be read as a signal, because "seen and ignored"
  is now distinguishable from "held back by policy". The accompanying database migration adds two columns
  and tidies the set of recorded withholding reasons; it applies automatically, with no action required on
  upgrade.
- Fixes the release-image pin check rejecting every valid pinned digest, which stopped the
  application server from starting on a fresh deploy that enforces the digest pin.
- Fixes a workspace admin page that any member of the workspace could open by visiting its URL
  directly. The page's actions were already refused by the server, so it showed only errors rather
  than any data, but it should never have been reachable — every workspace admin page now redirects
  non-admins away. Two smaller fixes ride along: an administrator whose role is revoked mid-session
  no longer keeps the admin UI until they reload, and an instance administrator with no workspaces
  yet can once again reach the "Create Workspace" button.
- Every deployment is now clearly identifiable. Outside production the header shows
  an environment pill (Staging / Preview / Local) instead of a raw commit hash, and
  the footer gains a deployment strip — branch, commit (linked to the exact commit),
  and how long ago it was deployed. Production is unchanged: the header shows the
  release version linking to its notes, and the footer stays clean.
- Fixes login and other database operations intermittently failing. The build's
  10 MB off-heap direct-memory default sits just below the application server's
  steady I/O footprint, so once it filled, PostgreSQL could no longer allocate the
  buffer for its connection handshake and the connection pool drained. The server
  and worker now get 128 MB of direct memory.

  **Operators:** override the default with `APP_MAX_DIRECT_MEMORY` if you need to
  tune it (e.g. a larger value if a heavy backfill ever approaches the limit).

- Fixes the webhook-server crash-looping on startup. Two causes: its 1 GB memory
  limit left too little heap for the Spring classpath-scan spike (it OOMed before
  boot finished), and — because prod enables Tomcat file access logging to
  `/var/log/hephaestus`, which the core stack gives no writable location — the
  access-log valve failed and aborted the boot. The webhook now gets a 2 GB default
  limit (`WEBHOOK_SERVER_MEM_LIMIT`) and its file access log is disabled (requests
  are already logged by the reverse proxy and to stdout).
- Fixes the background worker crash-looping on startup when the Slack integration is
  enabled. A Slack sync runner was loading in the worker role without the scheduler
  it depends on, which only runs on the server role, so the worker failed to boot.
  The runner is now gated to the server role, matching the other integration sync
  runners.

## 0.73.2

### Patch Changes

- Fixes a release deploy that never started: the signature check on the pinned agent image rejected every
  valid release, so the application server stayed down.

---

Newest first. Entries are authored as [changesets](https://github.com/changesets/changesets) in each PR
and assembled on release — see the
[release management guide](https://ls1intum.github.io/Hephaestus/contributor/release-management).
Releases up to and including [v0.73.1](https://github.com/ls1intum/Hephaestus/releases/tag/v0.73.1)
predate this file; see [GitHub Releases](https://github.com/ls1intum/Hephaestus/releases) for their notes.
