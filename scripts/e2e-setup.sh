#!/usr/bin/env bash
#
# e2e-setup.sh — one command to make a running local stack ready for an authentic practice-detection
# review against a REAL GitLab/GitHub repo + a REAL LLM. Given a PAT and an LLM key, it does the rest
# through the dev-login API: connect the workspace, wire the LLM runtime, create the practices, and
# print the trigger. Idempotent — safe to re-run.
#
# Prereq: the app booted with the `local,e2e` profiles (dev-login + cookie-secure=false +
# gitlab-workspace-creation + dev-trigger — all on in the `e2e` profile; see application-e2e.yml) and
# Postgres reachable. See docs/contributor/e2e-testing.md.
#
# Usage:
#   scripts/e2e-setup.sh --gitlab-pat glpat-… --llm-key sk-… \
#     [--llm-base-url https://gpu.ase.cit.tum.de/api] [--model openai/gpt-oss-120b] \
#     [--server-url https://gitlab.lrz.de] [--account-login hephaestustest] [--repo ns/project]
# Every flag also reads an E2E_* env fallback (E2E_GITLAB_PAT, E2E_LLM_KEY, …).
set -euo pipefail

# ---- defaults / args -------------------------------------------------------
PROVIDER=gitlab
GITLAB_PAT="${E2E_GITLAB_PAT:-}"; GITHUB_PAT="${E2E_GITHUB_PAT:-}"
SERVER_URL="${E2E_SERVER_URL:-https://gitlab.lrz.de}"
LLM_KEY="${E2E_LLM_KEY:-}"; LLM_BASE_URL="${E2E_LLM_BASE_URL:-}"; MODEL="${E2E_MODEL:-}"
WS_SLUG="${E2E_WS_SLUG:-e2e}"; ACCOUNT_LOGIN="${E2E_ACCOUNT_LOGIN:-}"; ACCOUNT_TYPE="${E2E_ACCOUNT_TYPE:-ORG}"
USERNAME="${E2E_USERNAME:-e2e}"; REPO="${E2E_REPO:-}"
APP_URL="${E2E_APP_URL:-http://localhost:38080}"
DB_URL="${E2E_DB_URL:-postgresql://root:root@localhost:5432/hephaestus}"
while [ $# -gt 0 ]; do case "$1" in
  --provider) PROVIDER="$2"; shift 2;; --gitlab-pat) GITLAB_PAT="$2"; shift 2;; --github-pat) GITHUB_PAT="$2"; PROVIDER=github; shift 2;;
  --server-url) SERVER_URL="$2"; shift 2;; --llm-key) LLM_KEY="$2"; shift 2;; --llm-base-url) LLM_BASE_URL="$2"; shift 2;;
  --model) MODEL="$2"; shift 2;; --workspace-slug) WS_SLUG="$2"; shift 2;; --account-login) ACCOUNT_LOGIN="$2"; shift 2;;
  --account-type) ACCOUNT_TYPE="$2"; shift 2;; --username) USERNAME="$2"; shift 2;; --repo) REPO="$2"; shift 2;;
  --app-url) APP_URL="$2"; shift 2;; --db-url) DB_URL="$2"; shift 2;;
  *) echo "unknown flag: $1" >&2; exit 2;; esac; done

PAT="$([ "$PROVIDER" = github ] && echo "$GITHUB_PAT" || echo "$GITLAB_PAT")"
KIND="$([ "$PROVIDER" = github ] && echo GITHUB || echo GITLAB)"
say() { printf '\033[36m▸ %s\033[0m\n' "$*"; }; die() { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

# ---- 0. preflight ----------------------------------------------------------
for c in curl jq psql; do command -v "$c" >/dev/null || die "missing dependency: $c"; done
[ -n "$PAT" ] || die "a PAT is required (--gitlab-pat / --github-pat or E2E_GITLAB_PAT)"
[ -n "$LLM_KEY" ] || die "an LLM key is required (--llm-key or E2E_LLM_KEY)"
curl -fsS -m5 "$APP_URL/identity-providers" >/dev/null || die "app not reachable at $APP_URL (run the app with the 'local' profile first)"

# ---- 1. dev-login → bearer JWT (cookie value is the JWT; Bearer is CSRF-exempt) --
login() { curl -fsS -i -m10 -X POST "$APP_URL/auth/dev-login" -H 'content-type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"admin\":true}" | grep -i 'set-cookie: HEPHAESTUS_AT' | sed -E 's/.*HEPHAESTUS_AT=([^;]+).*/\1/' | tr -d '\r'; }
JWT="$(login)"; [ -n "$JWT" ] || die "dev-login failed (is dev-login-enabled on? need the 'local' profile)"
api() { local m="$1" p="$2"; shift 2; curl -fsS -m30 -X "$m" "$APP_URL$p" -H "authorization: Bearer $JWT" "$@"; }
ACCOUNT_ID="$(api GET /user | jq -r '.id')"; say "dev-login OK (account id $ACCOUNT_ID, app-admin)"

# ---- 2. resolve the REAL SCM user behind the PAT ---------------------------
if [ "$PROVIDER" = github ]; then
  SCM_JSON="$(curl -fsS -m10 -H "authorization: Bearer $PAT" https://api.github.com/user)"
  SCM_ID="$(echo "$SCM_JSON" | jq -r '.id')"; SCM_LOGIN="$(echo "$SCM_JSON" | jq -r '.login')"
  PROVIDER_ORIGIN="https://github.com"
else
  SCM_JSON="$(curl -fsS -m10 -H "private-token: $PAT" "$SERVER_URL/api/v4/user")"
  SCM_ID="$(echo "$SCM_JSON" | jq -r '.id')"; SCM_LOGIN="$(echo "$SCM_JSON" | jq -r '.username')"
  PROVIDER_ORIGIN="$SERVER_URL"
fi
[ -n "$SCM_ID" ] && [ "$SCM_ID" != null ] || die "could not resolve SCM user from the PAT"
# These flow into raw SQL below; validate their shape so a hostile/odd login can't break (or inject) it.
[[ "$SCM_ID" =~ ^[0-9]+$ ]] || die "unexpected non-numeric SCM id '$SCM_ID'"
[[ "$SCM_LOGIN" =~ ^[A-Za-z0-9._-]+$ ]] || die "unexpected SCM login '$SCM_LOGIN' (refusing to build SQL)"
[ -n "$ACCOUNT_LOGIN" ] || ACCOUNT_LOGIN="$SCM_LOGIN"
say "resolved SCM user: $SCM_LOGIN (id $SCM_ID) on $PROVIDER_ORIGIN"

# ---- 3. seed the SCM identity for the dev account (so it can OWN/navigate the workspace) ----
q() { psql "$DB_URL" -tAqc "$1"; }   # one statement, value-only output
PROVIDER_ID="$(q "INSERT INTO git_provider (type, server_url, created_at) VALUES ('$KIND','$PROVIDER_ORIGIN',now()) ON CONFLICT (type, server_url) DO UPDATE SET server_url=EXCLUDED.server_url RETURNING id")"
USER_ID="$(q "INSERT INTO \"user\" (native_id, provider_id, login, type, avatar_url, html_url, created_at, updated_at) VALUES ($SCM_ID,$PROVIDER_ID,'$SCM_LOGIN','USER','','$PROVIDER_ORIGIN/$SCM_LOGIN',now(),now()) ON CONFLICT (provider_id, native_id) DO UPDATE SET login=EXCLUDED.login RETURNING id")"
# Target the account-scoped unique index so a same-account re-run is idempotent, while a *cross-account*
# collision on (git_provider_id, subject) — a genuine misconfig — still surfaces loudly via the other
# unique index (matching identity_link's uq_identity_link_active_per_provider, added in 1780825201546).
q "INSERT INTO identity_link (account_id, git_provider_id, subject, linked_at, linked_via, external_actor_id, username_at_signup) VALUES ($ACCOUNT_ID,$PROVIDER_ID,'$SCM_ID',now(),'OAUTH_LOGIN',$USER_ID,'$SCM_LOGIN') ON CONFLICT (account_id, git_provider_id, COALESCE(team_id, '')) WHERE disabled_at IS NULL DO NOTHING" >/dev/null
say "seeded SCM identity (user id $USER_ID, linked to account $ACCOUNT_ID)"
JWT="$(login)"; [ -n "$JWT" ] || die "re-login failed after seeding the SCM identity"  # so the JWT's preferred_username resolves to the linked SCM user

# ---- 4. create (or reuse) the workspace + connection -----------------------
WS_ID="$(api GET /workspaces | jq -r --arg s "$WS_SLUG" '.[] | select(.workspaceSlug==$s or .slug==$s) | .id' | head -1)"
if [ -z "$WS_ID" ]; then
  say "creating workspace '$WS_SLUG' connected to $ACCOUNT_LOGIN ($KIND)…"
  api POST /workspaces -H 'content-type: application/json' -d "$(jq -nc \
    --arg s "$WS_SLUG" --arg n "E2E Practice Detection" --arg a "$ACCOUNT_LOGIN" --arg t "$ACCOUNT_TYPE" \
    --arg k "$KIND" --arg p "$PAT" --arg u "$SERVER_URL" \
    '{workspaceSlug:$s,displayName:$n,accountLogin:$a,accountType:$t,kind:$k,personalAccessToken:$p,serverUrl:$u}')" >/dev/null
  WS_ID="$(api GET /workspaces | jq -r --arg s "$WS_SLUG" '.[] | select(.workspaceSlug==$s or .slug==$s) | .id' | head -1)"
fi
[ -n "$WS_ID" ] || die "workspace create/lookup failed"
psql "$DB_URL" -tA >/dev/null <<SQL
INSERT INTO workspace_membership (workspace_id, user_id, role, league_points, hidden, created_at)
  VALUES ($WS_ID, $USER_ID, 'ADMIN', 0, false, now()) ON CONFLICT DO NOTHING;
SQL
say "workspace ready: id $WS_ID, slug '$WS_SLUG' (dev account is ADMIN member)"
# API-created workspaces default practices OFF — enable it (+ auto/manual triggers) so detection runs.
api PATCH "/workspaces/$WS_SLUG/features" -H 'content-type: application/json' \
  -d '{"practicesEnabled":true,"practiceReviewAutoTriggerEnabled":true,"practiceReviewManualTriggerEnabled":true}' >/dev/null
say "practice detection enabled on the workspace"

# ---- 5. LLM runtime + bind to practice detection --------------------------
if [ -n "$LLM_BASE_URL" ]; then CRED=API_KEY; LP=OPENAI; NET=true; MODEL="${MODEL:-openai/gpt-oss-120b}"
else CRED=PROXY; LP=ANTHROPIC; NET=false; MODEL="${MODEL:-claude-sonnet-4-5}"; fi
CFG_ID="$(api GET "/workspaces/$WS_SLUG/agent-configs" | jq -r '.[] | select(.name=="e2e-llm") | .id' | head -1)"
if [ -z "$CFG_ID" ]; then
  CFG_ID="$(api POST "/workspaces/$WS_SLUG/agent-configs" -H 'content-type: application/json' -d "$(jq -nc \
    --arg m "$MODEL" --arg k "$LLM_KEY" --arg b "$LLM_BASE_URL" --arg p "$LP" --arg c "$CRED" --argjson net "$NET" \
    '{name:"e2e-llm",enabled:true,modelName:$m,llmApiKey:$k,llmBaseUrl:($b|select(.!="")),llmProvider:$p,credentialMode:$c,timeoutSeconds:1200,maxConcurrentJobs:1,allowInternet:$net}')" | jq -r '.id')"
fi
api PUT "/workspaces/$WS_SLUG/ai-settings/practice-config" -H 'content-type: application/json' -d "{\"configId\":$CFG_ID}" >/dev/null
say "LLM runtime 'e2e-llm' (id $CFG_ID, $LP/$CRED, $MODEL) bound to practice detection"

# ---- 6. the practices ------------------------------------------------------
practice() { local slug="$1" name="$2" trig="$3" crit="$4"
  api POST "/workspaces/$WS_SLUG/practices" -H 'content-type: application/json' -d "$(jq -nc \
    --arg s "$slug" --arg n "$name" --argjson t "$trig" --arg cr "$crit" \
    '{slug:$s,name:$n,triggerEvents:$t,criteria:$cr}')" >/dev/null 2>&1 || true; }
practice submit-reviewable-work "Submit reviewable work" '["PullRequestCreated","PullRequestReady","PullRequestSynchronized"]' \
  "The MR is appropriately scoped, has a clear description, passes CI, and is not a draft dump. Flag oversized/unfocused MRs and missing descriptions."
practice act-on-feedback "Act on feedback" '["ReviewSubmitted","PullRequestSynchronized"]' \
  "After reviewers leave comments, the author addresses them with follow-up commits or replies rather than ignoring or force-resolving them."
practice plan-and-scope-issues "Plan & scope issues" '["PullRequestCreated"]' \
  "The MR references a well-defined, properly scoped issue and stays within that scope. Flag MRs with no linked issue or scope creep."
say "practices created: Submit reviewable work · Act on feedback · Plan & scope issues"

# ---- 7. monitor a repo (optional) + print the loop ------------------------
if [ -n "$REPO" ]; then api POST "/workspaces/$WS_SLUG/repositories?nameWithOwner=$REPO" >/dev/null 2>&1 || true; say "monitoring repo: $REPO"; fi
# PRs/MRs are stored in `issue` (discriminator issue_type='PULL_REQUEST'), populated by the async sync.
PR_ID="$(psql "$DB_URL" -tAc "SELECT id FROM issue WHERE issue_type='PULL_REQUEST' ORDER BY id DESC LIMIT 1" 2>/dev/null || true)"
echo
printf '\033[32m✓ E2E ready.\033[0m  UI: http://localhost:4200 → "Dev sign in" (user: %s) → workspace "%s"\n' "$USERNAME" "$WS_SLUG"
if [ -n "$PR_ID" ]; then
  printf '  Trigger a real review:\n    curl -X POST "%s/api/dev/trigger-review?prId=%s&workspaceId=%s"\n' "$APP_URL" "$PR_ID" "$WS_ID"
else
  printf '  No PR synced yet — push/open an MR on the connected repo (webhook), or sync, then:\n    curl -X POST "%s/api/dev/trigger-review?prId=<id>&workspaceId=%s"\n' "$APP_URL" "$WS_ID"
fi
