#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOST="${HEPHAESTUS_PUBLIC_TEST_HOST:-hephaestus-test.felixdietrich.com}"
ORIGIN="https://${HOST}"
APP_PORT="${HEPHAESTUS_PUBLIC_TEST_APP_PORT:-38085}"
MANAGEMENT_PORT="${HEPHAESTUS_PUBLIC_TEST_MANAGEMENT_PORT:-38086}"
POSTGRES_PORT="${HEPHAESTUS_PUBLIC_TEST_POSTGRES_PORT:-55432}"
NATS_SERVER="${HEPHAESTUS_PUBLIC_TEST_NATS_SERVER:-nats://localhost:4222}"
WEBAPP_CONTAINER="${HEPHAESTUS_PUBLIC_TEST_WEBAPP_CONTAINER:-heph-local-webapp}"
TRAEFIK_DYNAMIC_FILE="${HEPHAESTUS_PUBLIC_TEST_TRAEFIK_FILE:-/data/coolify/proxy/dynamic/hephaestus-local-test.yaml}"
SERVER_LOG="${HEPHAESTUS_PUBLIC_TEST_SERVER_LOG:-/tmp/heph-public-server.log}"
SERVER_SCRIPT="${HEPHAESTUS_PUBLIC_TEST_SERVER_SCRIPT:-/tmp/start-heph-public-server.sh}"
NGINX_CONF="${HEPHAESTUS_PUBLIC_TEST_NGINX_CONF:-/tmp/heph-local-nginx.conf}"

usage() {
	cat <<EOF
Usage: $0 [start|stop|status|smoke|seed-status]

Starts the current Jean worktree behind the machine-local Coolify Traefik route.
Secrets stay in server/.env, copied from the Jean root by scripts/jean-setup.sh.

Environment overrides:
  HEPHAESTUS_PUBLIC_TEST_HOST=$HOST
  HEPHAESTUS_PUBLIC_TEST_APP_PORT=$APP_PORT
  HEPHAESTUS_PUBLIC_TEST_MANAGEMENT_PORT=$MANAGEMENT_PORT
EOF
}

backend_pids() {
	pgrep -f "${SERVER_SCRIPT}|spring-boot:run -Dspring-boot.run.profiles=local|de.tum.cit.aet.hephaestus.Application --spring.profiles.active=local" || true
}

coolify_gateway() {
	docker network inspect coolify --format '{{(index .IPAM.Config 0).Gateway}}' 2>/dev/null || printf '10.0.4.1'
}

write_server_script() {
	cat > "$SERVER_SCRIPT" <<EOF
#!/usr/bin/env bash
set -euo pipefail
cd "$ROOT/server"
set -a
if [ -f .env ]; then source .env; fi
set +a
export APPLICATION_HOST_URL="$ORIGIN"
export HEPHAESTUS_WEBAPP_URL="$ORIGIN"
export HEPHAESTUS_AUTH_ISSUER="$ORIGIN"
export HEPHAESTUS_AUTH_API_BASE_PATH="\${HEPHAESTUS_AUTH_API_BASE_PATH:-/api}"
export HEPHAESTUS_INTEGRATION_SLACK_REDIRECT_URI="$ORIGIN/api/oauth/callback/slack"
export POSTGRES_PORT="$POSTGRES_PORT"
export NATS_SERVER="$NATS_SERVER"
export SERVER_PORT="$APP_PORT"
export MANAGEMENT_PORT="$MANAGEMENT_PORT"
# The public route strips /api at Traefik. Tomcat native forwarded-header handling
# restores scheme/host without folding X-Forwarded-Prefix into {baseUrl}; auth.api-base-path
# is the single owner of the public /api prefix for OAuth init + callback URLs.
export SERVER_FORWARD_HEADERS_STRATEGY=native
export JAVA_TOOL_OPTIONS="\${JAVA_TOOL_OPTIONS:+\$JAVA_TOOL_OPTIONS }-Djava.net.preferIPv4Stack=true"
export HEPHAESTUS_SYNC_RUN_ON_STARTUP="\${HEPHAESTUS_SYNC_RUN_ON_STARTUP:-false}"
export HEPHAESTUS_SYNC_BACKFILL_ENABLED="\${HEPHAESTUS_SYNC_BACKFILL_ENABLED:-false}"
if [ -n "\${GITLAB_PAT:-}" ] && [ -n "\${GITLAB_GROUP_PATH:-}" ]; then
  export GITLAB_ENABLED=true
  export GITLAB_WORKSPACE_INIT_DEFAULT=true
  export GITLAB_WORKSPACE_CREATION="\${GITLAB_WORKSPACE_CREATION:-true}"
  export HEPHAESTUS_FEATURES_FLAGS_GITLAB_WORKSPACE_CREATION="\${HEPHAESTUS_FEATURES_FLAGS_GITLAB_WORKSPACE_CREATION:-true}"
  export GITLAB_SERVER_URL="\${GITLAB_SERVER_URL:-https://gitlab.lrz.de}"
else
  export GITLAB_ENABLED="\${GITLAB_ENABLED:-false}"
  export GITLAB_WORKSPACE_CREATION="\${GITLAB_WORKSPACE_CREATION:-false}"
fi
if [ -z "\${GITLAB_DEFAULT_SERVER_URL:-}" ] && [ -n "\${GITLAB_SERVER_URL:-}" ]; then
  export GITLAB_DEFAULT_SERVER_URL="\$GITLAB_SERVER_URL"
fi
export LEADERBOARD_NOTIFICATION_ENABLED="\${LEADERBOARD_NOTIFICATION_ENABLED:-false}"
export POSTHOG_ENABLED="\${POSTHOG_ENABLED:-false}"
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
EOF
	chmod +x "$SERVER_SCRIPT"
}

write_webapp_env() {
	mkdir -p "$ROOT/webapp/dist"
	cat > "$ROOT/webapp/dist/env-config.js" <<EOF
window.__ENV__ = {
  APPLICATION_VERSION: "DEV-public-test",
  APPLICATION_CLIENT_URL: "$ORIGIN",
  APPLICATION_SERVER_URL: "$ORIGIN/api",
  XSRF_COOKIE_NAME: "__Host-XSRF-TOKEN",
  SENTRY_ENVIRONMENT: "local-public-test",
  SENTRY_DSN: "",
  POSTHOG_ENABLED: "false",
  POSTHOG_PROJECT_API_KEY: "",
  POSTHOG_API_HOST: "",
  LEGAL_PROFILE: "",
  TANSTACK_DEVTOOLS_ENABLED: "false",
  GIT_BRANCH: "$(git -C "$ROOT" branch --show-current 2>/dev/null || true)",
  GIT_COMMIT: "$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || true)",
  DEPLOYED_AT: "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
};
EOF
}

write_nginx_conf() {
	cat > "$NGINX_CONF" <<'EOF'
server {
  listen 80;
  server_name _;
  root /usr/share/nginx/html;
  index index.html;

  location /assets/ {
    try_files $uri =404;
    access_log off;
    add_header Cache-Control "public, max-age=31536000, immutable" always;
  }

  location / {
    try_files $uri $uri/ /index.html;
    add_header Cache-Control "no-cache" always;
  }
}
EOF
}

write_traefik_route() {
	if [ ! -d "$(dirname "$TRAEFIK_DYNAMIC_FILE")" ]; then
		echo "  skipped Traefik route: Coolify dynamic directory not found"
		return
	fi

	local gateway
	gateway="$(coolify_gateway)"
	cat > "$TRAEFIK_DYNAMIC_FILE" <<EOF
# Temporary Hephaestus test route managed by scripts/jean-public-test.sh.
# Uses Coolify's Traefik and the current Jean worktree server/webapp.
http:
  middlewares:
    hephaestus-test-redirect-to-https:
      redirectScheme:
        scheme: https
    hephaestus-test-gzip:
      compress: {}
    hephaestus-test-api-strip-prefix:
      stripPrefix:
        prefixes:
          - /api
  routers:
    hephaestus-test-http:
      entryPoints:
        - http
      rule: Host(\`${HOST}\`)
      service: hephaestus-test-webapp
      middlewares:
        - hephaestus-test-redirect-to-https
      priority: 1
    hephaestus-test-api-https:
      entryPoints:
        - https
      rule: Host(\`${HOST}\`) && PathPrefix(\`/api\`)
      service: hephaestus-test-backend
      middlewares:
        - hephaestus-test-api-strip-prefix
        - hephaestus-test-gzip
      tls:
        certResolver: letsencrypt
      priority: 100
    hephaestus-test-webhooks-https:
      entryPoints:
        - https
      rule: Host(\`${HOST}\`) && PathPrefix(\`/webhooks\`)
      service: hephaestus-test-backend
      middlewares:
        - hephaestus-test-gzip
      tls:
        certResolver: letsencrypt
      priority: 110
    hephaestus-test-webapp-https:
      entryPoints:
        - https
      rule: Host(\`${HOST}\`)
      service: hephaestus-test-webapp
      middlewares:
        - hephaestus-test-gzip
      tls:
        certResolver: letsencrypt
      priority: 1
  services:
    hephaestus-test-backend:
      loadBalancer:
        servers:
          - url: http://${gateway}:${APP_PORT}
    hephaestus-test-webapp:
      loadBalancer:
        servers:
          - url: http://${WEBAPP_CONTAINER}:80
EOF
}

start_webapp() {
	if [ "${HEPHAESTUS_PUBLIC_TEST_SKIP_WEBAPP_BUILD:-false}" != "true" ]; then
		(cd "$ROOT" && pnpm run build:webapp)
	fi
	write_webapp_env
	write_nginx_conf
	docker rm -f "$WEBAPP_CONTAINER" >/dev/null 2>&1 || true
	docker run -d --name "$WEBAPP_CONTAINER" --restart unless-stopped --network coolify \
		-v "$ROOT/webapp/dist:/usr/share/nginx/html:ro" \
		-v "$NGINX_CONF:/etc/nginx/conf.d/default.conf:ro" \
		nginx:stable-alpine >/dev/null
}

stop_backend() {
	local pids
	pids="$(backend_pids)"
	if [ -n "$pids" ]; then
		echo "$pids" | xargs -r kill
		sleep 5
	fi
}

start_backend() {
	write_server_script
	stop_backend
	setsid -f bash -c "exec '$SERVER_SCRIPT' >'$SERVER_LOG' 2>&1 < /dev/null"
	for _ in $(seq 1 90); do
		if curl -fsS "http://localhost:${MANAGEMENT_PORT}/actuator/health/readiness" >/dev/null 2>&1; then
			return
		fi
		sleep 2
	done
	echo "Backend did not become ready. Tail of $SERVER_LOG:" >&2
	tail -120 "$SERVER_LOG" >&2 || true
	return 1
}

start() {
	echo "Starting Hephaestus public test route for $ORIGIN"
	write_traefik_route
	start_webapp
	start_backend
	status
	smoke
}

stop() {
	stop_backend
	docker rm -f "$WEBAPP_CONTAINER" >/dev/null 2>&1 || true
	echo "Stopped Hephaestus public test processes."
}

status() {
	echo "Public URL: $ORIGIN"
	echo "Backend health:"
	curl -fsS "http://localhost:${MANAGEMENT_PORT}/actuator/health/readiness" || true
	echo
	echo "Backend PIDs:"
	backend_pids || true
	echo "Webapp container:"
	docker ps --filter "name=^/${WEBAPP_CONTAINER}$" --format '  {{.Names}} {{.Status}}'
	seed_status || true
	mentor_status || true
}

seed_status() {
	local script_postgres_port="$POSTGRES_PORT"
	set -a
	if [ -f "$ROOT/server/.env" ]; then source "$ROOT/server/.env"; fi
	set +a
	POSTGRES_PORT="$script_postgres_port"
	if [ -z "${GITLAB_PAT:-}" ] || [ -z "${GITLAB_GROUP_PATH:-}" ]; then
		echo "GitLab seed: skipped (GITLAB_PAT or GITLAB_GROUP_PATH missing)"
		return 0
	fi
	local psql_base
	psql_base=(psql -h localhost -p "$POSTGRES_PORT" -U "${POSTGRES_USER:-root}" -d "${POSTGRES_DB:-hephaestus}" -v ON_ERROR_STOP=1 -tAc)
	local rows monitors dupes
	rows="$(PGCONNECT_TIMEOUT=2 PGPASSWORD="${POSTGRES_PASSWORD:-root}" "${psql_base[@]}" "select count(*) from workspace w join connection c on c.workspace_id=w.id where lower(w.account_login)=lower('${GITLAB_GROUP_PATH}') and c.kind='GITLAB' and c.state='ACTIVE';" 2>/dev/null || true)"
	monitors="$(PGCONNECT_TIMEOUT=2 PGPASSWORD="${POSTGRES_PASSWORD:-root}" "${psql_base[@]}" "select count(*) from repository_to_monitor r join workspace w on w.id=r.workspace_id where lower(w.account_login)=lower('${GITLAB_GROUP_PATH}');" 2>/dev/null || true)"
	dupes="$(PGCONNECT_TIMEOUT=2 PGPASSWORD="${POSTGRES_PASSWORD:-root}" "${psql_base[@]}" "select count(*) from (select lower(account_login) login from workspace group by lower(account_login) having count(*) > 1) d;" 2>/dev/null || true)"
	echo "GitLab seed: group=${GITLAB_GROUP_PATH} activeConnections=${rows:-?} monitoredRepositories=${monitors:-?} duplicateAccountLogins=${dupes:-?}"
	if [ "${rows:-0}" != "1" ] || [ "${monitors:-0}" = "0" ]; then
		return 1
	fi
}

load_seed_env() {
	local script_postgres_port="$POSTGRES_PORT"
	set -a
	if [ -f "$ROOT/server/.env" ]; then source "$ROOT/server/.env"; fi
	set +a
	POSTGRES_PORT="$script_postgres_port"
}

psql_seed() {
	PGCONNECT_TIMEOUT=2 PGPASSWORD="${POSTGRES_PASSWORD:-root}" psql \
		-h localhost \
		-p "$POSTGRES_PORT" \
		-U "${POSTGRES_USER:-root}" \
		-d "${POSTGRES_DB:-hephaestus}" \
		-v ON_ERROR_STOP=1 \
		"$@"
}

mentor_status() {
	load_seed_env
	if [ -z "${GITLAB_GROUP_PATH:-}" ]; then
		echo "Mentor seed: skipped (GITLAB_GROUP_PATH missing)"
		return 0
	fi
	local ready
	ready="$(psql_seed -tAc "select count(*) from workspace w join agent_config ac on ac.id=w.mentor_config_id and ac.workspace_id=w.id where lower(w.account_login)=lower('${GITLAB_GROUP_PATH}') and w.mentor_enabled=true and ac.enabled=true;" 2>/dev/null || true)"
	echo "Mentor seed: group=${GITLAB_GROUP_PATH} ready=${ready:-?}"
	[ "${ready:-0}" = "1" ]
}

ensure_mentor_config() {
	load_seed_env
	if [ -z "${GITLAB_GROUP_PATH:-}" ]; then
		echo "Mentor seed: skipped (GITLAB_GROUP_PATH missing)"
		return 0
	fi
	if mentor_status >/dev/null 2>&1; then
		mentor_status
		return 0
	fi

	local llm_key="${MENTOR_LLM_API_KEY:-${E2E_LLM_KEY:-}}"
	local llm_base_url="${MENTOR_LLM_BASE_URL:-${E2E_LLM_BASE_URL:-https://staging.hephaestus.aet.cit.tum.de/logos/v1}}"
	local llm_model="${MENTOR_LLM_MODEL:-${E2E_MODEL:-openai/gpt-oss-120b}}"
	if [ -z "$llm_key" ]; then
		echo "Mentor seed: missing MENTOR_LLM_API_KEY/E2E_LLM_KEY and no enabled mentor config exists" >&2
		return 1
	fi

	psql_seed \
		-v group_path="$GITLAB_GROUP_PATH" \
		-v config_name="${MENTOR_LLM_CONFIG_NAME:-Public test mentor}" \
		-v model="$llm_model" \
		-v api_key="$llm_key" \
		-v base_url="$llm_base_url" \
		-qtA <<'SQL' >/dev/null
with target_workspace as (
	select id
	from workspace
	where lower(account_login) = lower(:'group_path')
	order by id
	limit 1
), upsert_config as (
	insert into agent_config (
		workspace_id,
		name,
		enabled,
		model_name,
		llm_api_key,
		llm_provider,
		timeout_seconds,
		max_concurrent_jobs,
		allow_internet,
		created_at,
		updated_at,
		credential_mode,
		model_version,
		llm_base_url
	)
	select
		target_workspace.id,
		:'config_name',
		true,
		:'model',
		:'api_key',
		'OPENAI',
		1200,
		1,
		true,
		now(),
		now(),
		'API_KEY',
		null,
		:'base_url'
	from target_workspace
	on conflict (workspace_id, name) do update set
		enabled = true,
		model_name = excluded.model_name,
		llm_api_key = excluded.llm_api_key,
		llm_provider = excluded.llm_provider,
		timeout_seconds = excluded.timeout_seconds,
		max_concurrent_jobs = excluded.max_concurrent_jobs,
		allow_internet = excluded.allow_internet,
		updated_at = now(),
		credential_mode = excluded.credential_mode,
		model_version = excluded.model_version,
		llm_base_url = excluded.llm_base_url
	returning id, workspace_id
)
update workspace
set mentor_config_id = upsert_config.id,
	mentor_enabled = true,
	updated_at = now()
from upsert_config
where workspace.id = upsert_config.workspace_id;
SQL
	mentor_status
}

slack_signature() {
	local body="$1"
	local ts="$2"
	local secret="${HEPHAESTUS_INTEGRATION_SLACK_SIGNING_SECRET:-}"
	if [ -z "$secret" ] && [ -f "$ROOT/server/.env" ]; then
		secret="$(grep -E '^HEPHAESTUS_INTEGRATION_SLACK_SIGNING_SECRET=' "$ROOT/server/.env" | tail -1 | cut -d= -f2-)"
	fi
	if [ -z "$secret" ]; then
		return 1
	fi
	printf 'v0=%s' "$(printf 'v0:%s:%s' "$ts" "$body" | openssl dgst -sha256 -hmac "$secret" -hex | awk '{print $2}')"
}

github_oauth_configured() {
	[ -n "${GITHUB_OAUTH_CLIENT_ID:-}" ] && return 0
	[ -f "$ROOT/server/.env" ] && grep -Eq '^GITHUB_OAUTH_CLIENT_ID=.' "$ROOT/server/.env"
}

smoke() {
	echo "Smoke testing $ORIGIN"
	curl -fsS -o /dev/null "$ORIGIN/"
	curl -fsS "$ORIGIN/env-config.js" | grep -q "APPLICATION_SERVER_URL: \"$ORIGIN/api\""
	local code
	code="$(curl -sS -o /dev/null -w '%{http_code}' "$ORIGIN/api/auth/me")"
	[ "$code" = "401" ] || { echo "Expected /api/auth/me to return 401, got $code" >&2; return 1; }

	if github_oauth_configured; then
		local location
		location="$(curl -fsSI "$ORIGIN/api/auth/login?provider=github&returnTo=/" | awk 'tolower($1)=="location:" {gsub(/\r$/, "", $2); print $2; exit}')"
		[ "$location" = "$ORIGIN/api/oauth2/authorization/github" ] || {
			echo "Expected GitHub login to redirect to $ORIGIN/api/oauth2/authorization/github, got $location" >&2
			return 1
		}
		location="$(curl -fsSI "$ORIGIN/api/oauth2/authorization/github" | awk 'tolower($1)=="location:" {gsub(/\r$/, "", $2); print $2; exit}')"
		case "$location" in
			*"redirect_uri=$ORIGIN/api/login/oauth2/code/github"*) ;;
			*)
				echo "Expected GitHub OAuth redirect_uri to be $ORIGIN/api/login/oauth2/code/github, got $location" >&2
				return 1
				;;
		esac
	fi

	seed_status
	ensure_mentor_config

	local body ts sig response
	body='{"type":"url_verification","challenge":"hephaestus-public-test-ok"}'
	ts="$(date +%s)"
	sig="$(slack_signature "$body" "$ts" || true)"
	if [ -n "$sig" ]; then
		response="$(curl -fsS -X POST "$ORIGIN/webhooks/slack" \
			-H 'Content-Type: application/json' \
			-H "X-Slack-Request-Timestamp: $ts" \
			-H "X-Slack-Signature: $sig" \
			--data "$body")"
		[ "$response" = "hephaestus-public-test-ok" ] || {
			echo "Slack challenge smoke failed: $response" >&2
			return 1
		}
	else
		echo "  skipped signed Slack smoke: no local signing secret"
	fi
	echo "Smoke OK."
}

cmd="${1:-start}"
case "$cmd" in
	start) start ;;
	stop) stop ;;
	status) status ;;
	smoke) smoke ;;
	seed-status) seed_status ;;
	-h|--help|help) usage ;;
	*) usage >&2; exit 2 ;;
esac
