#!/usr/bin/env bash
#
# e2e-setup.sh — one command to make a running local stack ready for an authentic practice-detection
# review against a REAL GitLab/GitHub repo + a REAL LLM. Given credentials through environment
# variables, it does the rest
# through the dev-login API: connect the workspace, wire the LLM runtime, create the practices, and
# print the trigger. Idempotent — safe to re-run.
#
# Prereq: the app booted with the `local,e2e` profiles (dev-login + cookie-secure=false +
# gitlab-workspace-creation + dev-trigger — all on in the `e2e` profile; see application-e2e.yml) and
# Postgres reachable. See docs/contributor/e2e-testing.md.
#
# Usage (credentials are env-only so they do not leak through process arguments):
#   E2E_GITLAB_PAT=... E2E_LLM_KEY=... E2E_LLM_PRICING_MODE=PRICED \
#     E2E_LLM_INPUT_USD=... E2E_LLM_OUTPUT_USD=... scripts/e2e-setup.sh \
#     [--llm-base-url https://llm.example/v1] [--model example-model] \
#     [--server-url https://gitlab.example.com] [--account-login test-user] [--repo ns/project]
# Non-secret flags also read their matching E2E_* environment variable.
set -euo pipefail

# ---- defaults / args -------------------------------------------------------
PROVIDER=gitlab
GITLAB_PAT="${E2E_GITLAB_PAT:-}"; GITHUB_PAT="${E2E_GITHUB_PAT:-}"
SERVER_URL="${E2E_SERVER_URL:-https://gitlab.lrz.de}"
LLM_KEY="${E2E_LLM_KEY:-}"; LLM_BASE_URL="${E2E_LLM_BASE_URL:-}"; MODEL="${E2E_MODEL:-}"
LLM_PROTOCOL="${E2E_LLM_PROTOCOL:-openai-completions}"; LLM_AUTH_MODE="${E2E_LLM_AUTH_MODE:-BEARER}"
LLM_PRICING_MODE="${E2E_LLM_PRICING_MODE:-}"; LLM_INPUT_USD="${E2E_LLM_INPUT_USD:-}"
LLM_OUTPUT_USD="${E2E_LLM_OUTPUT_USD:-}"; LLM_PRICE_NOTE="${E2E_LLM_PRICE_NOTE:-}"
WS_SLUG="${E2E_WS_SLUG:-e2e}"; ACCOUNT_LOGIN="${E2E_ACCOUNT_LOGIN:-}"; ACCOUNT_TYPE="${E2E_ACCOUNT_TYPE:-ORG}"
USERNAME="${E2E_USERNAME:-e2e}"; REPO="${E2E_REPO:-}"; PR_ID="${E2E_PR_ID:-}"
APP_URL="${E2E_APP_URL:-http://localhost:38080}"
DB_URL="${E2E_DB_URL:-postgresql://root:root@localhost:5432/hephaestus}"
while [ $# -gt 0 ]; do case "$1" in
  --provider) PROVIDER="$2"; shift 2;;
  --server-url) SERVER_URL="$2"; shift 2;; --llm-base-url) LLM_BASE_URL="$2"; shift 2;;
  --model) MODEL="$2"; shift 2;; --llm-protocol) LLM_PROTOCOL="$2"; shift 2;;
  --llm-auth-mode) LLM_AUTH_MODE="$2"; shift 2;; --llm-pricing-mode) LLM_PRICING_MODE="$2"; shift 2;;
  --llm-input-usd) LLM_INPUT_USD="$2"; shift 2;; --llm-output-usd) LLM_OUTPUT_USD="$2"; shift 2;;
  --workspace-slug) WS_SLUG="$2"; shift 2;; --account-login) ACCOUNT_LOGIN="$2"; shift 2;;
  --account-type) ACCOUNT_TYPE="$2"; shift 2;; --username) USERNAME="$2"; shift 2;; --repo) REPO="$2"; shift 2;;
  --pr-id) PR_ID="$2"; shift 2;;
  --app-url) APP_URL="$2"; shift 2;;
  *) echo "unknown flag: $1" >&2; exit 2;; esac; done

PAT="$([ "$PROVIDER" = github ] && echo "$GITHUB_PAT" || echo "$GITLAB_PAT")"
KIND="$([ "$PROVIDER" = github ] && echo GITHUB || echo GITLAB)"
say() { printf '\033[36m▸ %s\033[0m\n' "$*"; }; die() { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

for c in curl jq psql python3; do command -v "$c" >/dev/null || die "missing dependency: $c"; done

# Keep bearer tokens and the database password out of process arguments. The directory disappears
# on every exit; files are readable only by this user.
SECRET_DIR="$(mktemp -d)"; chmod 700 "$SECRET_DIR"; trap 'rm -rf "$SECRET_DIR"' EXIT
AUTH_HEADER_FILE="$SECRET_DIR/app-auth-header"; SCM_HEADER_FILE="$SECRET_DIR/scm-auth-header"
PG_SERVICE_FILE="$SECRET_DIR/pg_service.conf"
E2E_PRIVATE_DB_URL="$DB_URL" python3 >"$PG_SERVICE_FILE" <<'PY'
import os
from urllib.parse import parse_qs, unquote, urlparse

url = urlparse(os.environ["E2E_PRIVATE_DB_URL"])
if url.scheme not in {"postgres", "postgresql"} or not url.hostname or not url.path.strip("/"):
    raise SystemExit("E2E_DB_URL must be a postgresql:// URL")

def service_value(value: str) -> str:
    if "\n" in value or "\r" in value:
        raise SystemExit("E2E_DB_URL contains an invalid newline")
    return value

values = {
    "host": url.hostname,
    "port": str(url.port or 5432),
    "dbname": unquote(url.path.lstrip("/")),
    "user": unquote(url.username or ""),
    "password": unquote(url.password or ""),
}
query = parse_qs(url.query)
if query.get("sslmode"):
    values["sslmode"] = query["sslmode"][-1]
print("[e2e]")
for key, value in values.items():
    if value:
        print(f"{key}={service_value(value)}")
PY
chmod 600 "$PG_SERVICE_FILE"
psql_e2e() { PGSERVICEFILE="$PG_SERVICE_FILE" psql 'service=e2e' "$@"; }

# ---- 0. preflight ----------------------------------------------------------
[ -n "$PAT" ] || die "a PAT is required via E2E_GITLAB_PAT or E2E_GITHUB_PAT"
[ -n "$LLM_KEY" ] || die "an LLM key is required via E2E_LLM_KEY"
[ -n "$LLM_BASE_URL" ] || die "an OpenAI-compatible base URL is required via E2E_LLM_BASE_URL or --llm-base-url"
[ -n "$MODEL" ] || die "an upstream model id is required via E2E_MODEL or --model"
[[ "$LLM_PROTOCOL" =~ ^openai-(completions|responses)$ ]] || die "E2E_LLM_PROTOCOL must be openai-completions or openai-responses"
[[ "$LLM_AUTH_MODE" =~ ^(BEARER|API_KEY)$ ]] || die "E2E_LLM_AUTH_MODE must be BEARER or API_KEY"
if [ -n "$PR_ID" ]; then [[ "$PR_ID" =~ ^[0-9]+$ ]] || die "E2E_PR_ID must be numeric"; fi
[[ "$LLM_PRICING_MODE" =~ ^(PRICED|NO_CHARGE)$ ]] || die "set E2E_LLM_PRICING_MODE to PRICED or NO_CHARGE; the E2E setup never guesses cost"
if [ "$LLM_PRICING_MODE" = PRICED ]; then
  [[ "$LLM_INPUT_USD" =~ ^[0-9]+([.][0-9]+)?$ ]] || die "E2E_LLM_INPUT_USD is required for PRICED"
  [[ "$LLM_OUTPUT_USD" =~ ^[0-9]+([.][0-9]+)?$ ]] || die "E2E_LLM_OUTPUT_USD is required for PRICED"
else
  [ -n "$LLM_PRICE_NOTE" ] || die "E2E_LLM_PRICE_NOTE is required for NO_CHARGE"
fi
case "$APP_URL" in
  http://localhost:*|http://127.0.0.1:*|http://\[::1\]:*) ;;
  *) die "E2E_APP_URL must be a loopback URL; never expose the passwordless e2e profile publicly" ;;
esac
curl -fsS -m5 "$APP_URL/identity-providers" >/dev/null || die "app not reachable at $APP_URL (run the app with the 'local' profile first)"

# ---- 1. dev-login → bearer JWT (cookie value is the JWT; Bearer is CSRF-exempt) --
login() { curl -fsS -i -m10 -X POST "$APP_URL/auth/dev-login" -H 'content-type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"admin\":true}" | grep -Ei 'set-cookie: (__Host-)?HEPHAESTUS_AT=' | sed -E 's/.*(__Host-)?HEPHAESTUS_AT=([^;]+).*/\2/' | tr -d '\r'; }
set_jwt() { JWT="$1"; printf 'authorization: Bearer %s\n' "$JWT" >"$AUTH_HEADER_FILE"; chmod 600 "$AUTH_HEADER_FILE"; }
set_jwt "$(login)"; [ -n "$JWT" ] || die "dev-login failed (is dev-login-enabled on? need the 'local' profile)"
api() { local m="$1" p="$2"; shift 2; curl -fsS -m30 -X "$m" "$APP_URL$p" -H @"$AUTH_HEADER_FILE" "$@"; }
ACCOUNT_ID="$(api GET /user | jq -r '.id')"; say "dev-login OK (app-admin)"

# ---- 2. resolve the REAL SCM user behind the PAT ---------------------------
if [ "$PROVIDER" = github ]; then
  printf 'authorization: Bearer %s\n' "$PAT" >"$SCM_HEADER_FILE"; chmod 600 "$SCM_HEADER_FILE"
  SCM_JSON="$(curl -fsS -m10 -H @"$SCM_HEADER_FILE" https://api.github.com/user)"
  SCM_ID="$(echo "$SCM_JSON" | jq -r '.id')"; SCM_LOGIN="$(echo "$SCM_JSON" | jq -r '.login')"
  PROVIDER_ORIGIN="https://github.com"
else
  printf 'private-token: %s\n' "$PAT" >"$SCM_HEADER_FILE"; chmod 600 "$SCM_HEADER_FILE"
  SCM_JSON="$(curl -fsS -m10 -H @"$SCM_HEADER_FILE" "$SERVER_URL/api/v4/user")"
  SCM_ID="$(echo "$SCM_JSON" | jq -r '.id')"; SCM_LOGIN="$(echo "$SCM_JSON" | jq -r '.username')"
  PROVIDER_ORIGIN="$SERVER_URL"
fi
if [ -z "$SCM_ID" ] || [ "$SCM_ID" = null ]; then die "could not resolve SCM user from the PAT"; fi
# These flow into raw SQL below; validate their shape so a hostile/odd login can't break (or inject) it.
[[ "$SCM_ID" =~ ^[0-9]+$ ]] || die "the SCM returned an invalid account id"
[[ "$SCM_LOGIN" =~ ^[A-Za-z0-9._-]+$ ]] || die "the SCM returned an unsupported login (refusing to build SQL)"
[ -n "$ACCOUNT_LOGIN" ] || ACCOUNT_LOGIN="$SCM_LOGIN"
say "resolved the SCM user behind the PAT"

# ---- 3. seed the SCM identity for the dev account (so it can OWN/navigate the workspace) ----
q() { psql_e2e -v ON_ERROR_STOP=1 -tAqc "$1"; }   # one statement, fail closed, value-only output
PROVIDER_ID="$(q "INSERT INTO identity_provider (type, server_url, created_at) VALUES ('$KIND','$PROVIDER_ORIGIN',now()) ON CONFLICT (type, server_url) DO UPDATE SET server_url=EXCLUDED.server_url RETURNING id")"
USER_ID="$(q "INSERT INTO \"user\" (native_id, provider_id, login, type, avatar_url, html_url, created_at, updated_at) VALUES ($SCM_ID,$PROVIDER_ID,'$SCM_LOGIN','USER','','$PROVIDER_ORIGIN/$SCM_LOGIN',now(),now()) ON CONFLICT (provider_id, native_id) DO UPDATE SET login=EXCLUDED.login RETURNING id")"
# Target the account-scoped unique index so a same-account re-run is idempotent, while a *cross-account*
# collision on (git_provider_id, subject) — a genuine misconfig — still surfaces loudly via the other
# unique index (matching identity_link's uq_identity_link_active_per_provider, added in 1780825201546).
q "INSERT INTO identity_link (account_id, provider_id, subject, linked_at, linked_via, external_actor_id, username_at_signup) VALUES ($ACCOUNT_ID,$PROVIDER_ID,'$SCM_ID',now(),'OAUTH_LOGIN',$USER_ID,'$SCM_LOGIN') ON CONFLICT (account_id, provider_id, COALESCE(team_id, '')) WHERE disabled_at IS NULL DO NOTHING" >/dev/null
# Mentor access is an account-scoped authorization flag, not implied by app-admin. Grant it only to
# this disposable E2E account, then re-issue the JWT so the authority is present immediately.
q "INSERT INTO account_feature (account_id, flag, enabled_at) VALUES ($ACCOUNT_ID,'mentor_access',now()) ON CONFLICT DO NOTHING" >/dev/null
say "seeded the disposable account's SCM identity and mentor access"
set_jwt "$(login)"; [ -n "$JWT" ] || die "re-login failed after seeding the SCM identity"  # so the JWT's preferred_username resolves to the linked SCM user

# ---- 4. create (or reuse) the workspace + connection -----------------------
WORKSPACES="$(api GET /workspaces)"
WORKSPACE_JSON="$(echo "$WORKSPACES" | jq -c --arg s "$WS_SLUG" '.[] | select(.workspaceSlug==$s)' | head -1)"
WS_ID="$(echo "$WORKSPACE_JSON" | jq -r '.id // empty')"
if [ -z "$WS_ID" ]; then
  say "creating the isolated E2E workspace…"
  E2E_PRIVATE_PAT="$PAT" jq -nc \
    --arg s "$WS_SLUG" --arg n "E2E Practice Detection" --arg a "$ACCOUNT_LOGIN" --arg t "$ACCOUNT_TYPE" \
    --arg k "$KIND" --arg u "$PROVIDER_ORIGIN" \
    '{workspaceSlug:$s,displayName:$n,accountLogin:$a,accountType:$t,kind:$k,personalAccessToken:env.E2E_PRIVATE_PAT,serverUrl:$u}' | \
    api POST /workspaces -H 'content-type: application/json' --data-binary @- >/dev/null
  WORKSPACE_JSON="$(api GET /workspaces | jq -c --arg s "$WS_SLUG" '.[] | select(.workspaceSlug==$s)' | head -1)"
  WS_ID="$(echo "$WORKSPACE_JSON" | jq -r '.id // empty')"
else
  echo "$WORKSPACE_JSON" | jq -e --arg a "$ACCOUNT_LOGIN" --arg k "$KIND" '
    (.accountLogin | ascii_downcase) == ($a | ascii_downcase) and .providerType == $k
  ' >/dev/null || die "workspace slug already belongs to a different SCM account or provider"
  if [ "$KIND" = GITLAB ]; then
    WORKSPACE_DETAIL="$(api GET "/workspaces/$WS_SLUG")"
    NORMALIZED_PROVIDER_ORIGIN="${PROVIDER_ORIGIN%/}"
    echo "$WORKSPACE_DETAIL" | jq -e --arg u "$NORMALIZED_PROVIDER_ORIGIN" '
      ((.serverUrl // "") | sub("/$"; "")) == $u
    ' >/dev/null || die "workspace slug already belongs to a different SCM server"
  fi
fi
[ -n "$WS_ID" ] || die "workspace create/lookup failed"
# The setup is deliberately idempotent, so refresh the SCM credential on every run instead of
# retaining whatever encrypted PAT an earlier disposable run stored. Keep the secret in stdin.
E2E_PRIVATE_PAT="$PAT" jq -nc '{personalAccessToken:env.E2E_PRIVATE_PAT}' | \
  api PATCH "/workspaces/$WS_SLUG/token" -H 'content-type: application/json' --data-binary @- >/dev/null
say "workspace SCM credential refreshed"
psql_e2e -tA >/dev/null <<SQL
INSERT INTO workspace_membership (workspace_id, user_id, role, league_points, hidden, created_at)
  VALUES ($WS_ID, $USER_ID, 'ADMIN', 0, false, now()) ON CONFLICT DO NOTHING;
SQL
say "target workspace is ready and the disposable account is an admin member"
# API-created workspaces default practices OFF — enable it (+ auto/manual triggers) so detection runs.
api PATCH "/workspaces/$WS_SLUG/features" -H 'content-type: application/json' \
  -d '{"practicesEnabled":true,"mentorEnabled":true,"practiceReviewAutoTriggerEnabled":true,"practiceReviewManualTriggerEnabled":true}' >/dev/null
say "practice detection and mentor enabled on the workspace"

# ---- 5. OpenAI-compatible catalog + runtime bindings ----------------------
# Keep the real key in the connection catalog. It never enters agent_config, SQL, or a sandbox.
SETTINGS="$(api GET /admin/llm/settings)"
if [ "$(echo "$SETTINGS" | jq -r '.allowWorkspaceConnections')" != true ]; then
  api PUT /admin/llm/settings -H 'content-type: application/json' \
    -d '{"allowWorkspaceConnections":true}' >/dev/null
fi

CONNECTION_NAME="E2E OpenAI-compatible"; CONNECTION_SLUG="e2e-openai-compatible"
CONNECTION_JSON="$(api GET "/workspaces/$WS_SLUG/llm/connections" | jq -c --arg s "$CONNECTION_SLUG" '.[] | select(.slug==$s)' | head -1)"
CONNECTION_ID="$(echo "$CONNECTION_JSON" | jq -r '.id // empty')"
if [ -z "$CONNECTION_ID" ]; then
  CONNECTION_ID="$(E2E_PRIVATE_LLM_KEY="$LLM_KEY" jq -nc \
    --arg n "$CONNECTION_NAME" --arg s "$CONNECTION_SLUG" --arg b "$LLM_BASE_URL" --arg p "$LLM_PROTOCOL" \
    --arg a "$LLM_AUTH_MODE" \
    '{displayName:$n,slug:$s,baseUrl:$b,apiProtocol:$p,authMode:$a,apiKey:env.E2E_PRIVATE_LLM_KEY,enabled:false}' | \
    api POST "/workspaces/$WS_SLUG/llm/connections" -H 'content-type: application/json' --data-binary @- | jq -r '.id')"
else
  NORMALIZED_LLM_BASE_URL="${LLM_BASE_URL%/}"
  echo "$CONNECTION_JSON" | jq -e --arg b "$NORMALIZED_LLM_BASE_URL" --arg p "$LLM_PROTOCOL" --arg a "$LLM_AUTH_MODE" '
    ((.baseUrl | sub("/$"; "")) == $b) and .apiProtocol == $p and .authMode == $a
  ' >/dev/null || die "existing E2E connection uses a different immutable route; use a fresh workspace slug or delete it first"
  E2E_PRIVATE_LLM_KEY="$LLM_KEY" jq -nc '{apiKey:env.E2E_PRIVATE_LLM_KEY}' | \
    api PATCH "/workspaces/$WS_SLUG/llm/connections/$CONNECTION_ID" \
      -H 'content-type: application/json' --data-binary @- >/dev/null
fi
[ -n "$CONNECTION_ID" ] || die "LLM connection create/lookup failed"
PROBE_RESULT="$(api POST "/workspaces/$WS_SLUG/llm/connections/$CONNECTION_ID/probe")"
echo "$PROBE_RESULT" | jq -e '.reachable == true' >/dev/null || die "OpenAI-compatible connection probe failed"
say "OpenAI-compatible connection probe succeeded"

if [ "$LLM_PRICING_MODE" = PRICED ]; then
  PRICE_JSON="$(jq -nc --arg m "$LLM_PRICING_MODE" --arg i "$LLM_INPUT_USD" --arg o "$LLM_OUTPUT_USD" \
    '{pricingMode:$m,per1mInputUsd:($i|tonumber),per1mOutputUsd:($o|tonumber)}')"
else
  PRICE_JSON="$(jq -nc --arg m "$LLM_PRICING_MODE" --arg n "$LLM_PRICE_NOTE" '{pricingMode:$m,priceNote:$n}')"
fi
MODEL_ID="$(api GET "/workspaces/$WS_SLUG/llm/models" | jq -r --argjson c "$CONNECTION_ID" --arg m "$MODEL" '.[] | select(.connectionId==$c and .upstreamModelId==$m) | .id' | head -1)"
if [ -z "$MODEL_ID" ]; then
  MODEL_ID="$(api POST "/workspaces/$WS_SLUG/llm/connections/$CONNECTION_ID/models" -H 'content-type: application/json' -d "$(jq -nc \
    --arg n "E2E model" --arg m "$MODEL" --argjson price "$PRICE_JSON" \
    '{displayName:$n,upstreamModelId:$m,enabled:false} + $price')" | jq -r '.id')"
else
  api PATCH "/workspaces/$WS_SLUG/llm/models/$MODEL_ID" -H 'content-type: application/json' \
    -d "$(echo "$PRICE_JSON" | jq '. + {enabled:false}')" >/dev/null
fi
[ -n "$MODEL_ID" ] || die "LLM model create/lookup failed"
api PATCH "/workspaces/$WS_SLUG/llm/connections/$CONNECTION_ID" -H 'content-type: application/json' -d '{"enabled":true}' >/dev/null
api PATCH "/workspaces/$WS_SLUG/llm/models/$MODEL_ID" -H 'content-type: application/json' -d '{"enabled":true}' >/dev/null

CFG_ID="$(api GET "/workspaces/$WS_SLUG/agent-configs" | jq -r '.[] | select(.name=="e2e-llm") | .id' | head -1)"
if [ -z "$CFG_ID" ]; then
  CFG_ID="$(api POST "/workspaces/$WS_SLUG/agent-configs" -H 'content-type: application/json' -d "$(jq -nc \
    --argjson m "$MODEL_ID" \
    '{name:"e2e-llm",enabled:true,workspaceModelId:$m,timeoutSeconds:1200,maxConcurrentJobs:1,allowInternet:true}')" | jq -r '.id')"
else
  api PATCH "/workspaces/$WS_SLUG/agent-configs/$CFG_ID" -H 'content-type: application/json' \
    -d "$(jq -nc --argjson m "$MODEL_ID" '{enabled:true,workspaceModelId:$m,allowInternet:true}')" >/dev/null
fi
api PUT "/workspaces/$WS_SLUG/ai-settings/practice-config" -H 'content-type: application/json' -d "{\"configId\":$CFG_ID}" >/dev/null
api PUT "/workspaces/$WS_SLUG/ai-settings/mentor-config" -H 'content-type: application/json' -d "{\"configId\":$CFG_ID}" >/dev/null
say "catalog model bound to practice detection and mentor"

# ---- 6. the practices ------------------------------------------------------
practice() { local slug="$1" name="$2" trig="$3" crit="$4" body
  body="$(jq -nc --arg s "$slug" --arg n "$name" --argjson t "$trig" --arg cr "$crit" \
    '{slug:$s,name:$n,artifactType:"PULL_REQUEST",triggerEvents:$t,criteria:$cr}')"
  if echo "$PRACTICES" | jq -e --arg s "$slug" 'any(.[]; .slug==$s)' >/dev/null; then
    echo "$body" | jq 'del(.slug)' | api PATCH "/workspaces/$WS_SLUG/practices/$slug" \
      -H 'content-type: application/json' --data-binary @- >/dev/null
  else
    echo "$body" | api POST "/workspaces/$WS_SLUG/practices" \
      -H 'content-type: application/json' --data-binary @- >/dev/null
  fi
  api PATCH "/workspaces/$WS_SLUG/practices/$slug/active" \
    -H 'content-type: application/json' -d '{"active":true}' >/dev/null
}
PRACTICES="$(api GET "/workspaces/$WS_SLUG/practices")"
practice submit-reviewable-work "Submit reviewable work" '["PullRequestCreated","PullRequestReady","PullRequestSynchronized"]' \
  "The MR is appropriately scoped, has a clear description, passes CI, and is not a draft dump. Flag oversized/unfocused MRs and missing descriptions."
practice act-on-feedback "Act on feedback" '["ReviewSubmitted","PullRequestSynchronized"]' \
  "After reviewers leave comments, the author addresses them with follow-up commits or replies rather than ignoring or force-resolving them."
practice plan-and-scope-issues "Plan & scope issues" '["PullRequestCreated"]' \
  "The MR references a well-defined, properly scoped issue and stays within that scope. Flag MRs with no linked issue or scope creep."
say "practices created: Submit reviewable work · Act on feedback · Plan & scope issues"

# ---- 7. monitor a repo (optional) + print the loop ------------------------
if [ -n "$REPO" ]; then
  MONITORED_REPOS="$(api GET "/workspaces/$WS_SLUG/repositories")"
  if ! echo "$MONITORED_REPOS" | jq -e --arg r "$REPO" 'index($r) != null' >/dev/null; then
    ENCODED_REPO="$(jq -rn --arg r "$REPO" '$r|@uri')"
    api POST "/workspaces/$WS_SLUG/repositories?nameWithOwner=$ENCODED_REPO" >/dev/null
  fi
  say "target repository is monitored"
fi
# PRs/MRs are stored in `issue` (discriminator issue_type='PULL_REQUEST'), populated by the async sync.
PR_SCOPE_SQL="
  FROM issue i
  JOIN repository r ON r.id = i.repository_id
  JOIN repository_to_monitor rtm ON rtm.workspace_id = $WS_ID
    AND lower(rtm.name_with_owner) = lower(r.name_with_owner)
    AND (rtm.native_id IS NULL OR rtm.native_id = r.native_id)
  WHERE i.issue_type = 'PULL_REQUEST'"
if [ -n "$REPO" ]; then
  [[ "$REPO" =~ ^[A-Za-z0-9._/-]+$ ]] || die "E2E_REPO contains unsupported characters"
  PR_SCOPE_SQL+=" AND lower(r.name_with_owner) = lower('$REPO')"
fi
if [ -n "$PR_ID" ]; then
  [ "$(psql_e2e -tAc "SELECT CASE WHEN EXISTS (SELECT 1 $PR_SCOPE_SQL AND i.id = $PR_ID) THEN 1 ELSE 0 END")" = 1 ] || \
    die "E2E_PR_ID is not a pull request monitored by the target workspace"
else
  PR_ID="$(psql_e2e -tAc "SELECT i.id $PR_SCOPE_SQL ORDER BY i.updated_at DESC NULLS LAST, i.id DESC LIMIT 1")"
fi
echo
printf '\033[32m✓ E2E ready.\033[0m  Open the local UI, use Dev sign in, and select the E2E workspace.\n'
if [ -n "$PR_ID" ]; then
  printf '  Trigger a real review:\n    curl -X POST "%s/api/dev/trigger-review?prId=%s&workspaceId=%s"\n' "$APP_URL" "$PR_ID" "$WS_ID"
else
  printf '  No PR synced yet — push/open an MR on the connected repo (webhook), or sync, then:\n    curl -X POST "%s/api/dev/trigger-review?prId=<id>&workspaceId=%s"\n' "$APP_URL" "$WS_ID"
fi
