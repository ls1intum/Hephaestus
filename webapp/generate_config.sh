#!/bin/sh
# This script generates the environment.json file for the webapp based on the environment variables during the docker container startup.

APP_VERSION=$(cat /version.txt)

cat <<EOF > /usr/share/nginx/html/environment.json
{
  "version": "{$APP_VERSION}",
  "clientUrl": "${APPLICATION_CLIENT_URL}",
  "serverUrl": "${APPLICATION_SERVER_URL}",
  "sentry": {
    "dsn": "${SENTRY_DNS}",
    "environment": "prod"
  },
  "keycloak": {
    "url": "${KEYCLOAK_URL}",
    "realm": "${KEYCLOAK_REALM}",
    "clientId": "${KEYCLOAK_CLIENT_ID}",
    "skipLoginPage": ${KEYCLOAK_SKIP_LOGIN}
  },
  "umami": {
    "enabled": ${UMAMI_ENABLED},
    "scriptUrl": "${UMAMI_SCRIPT_URL}",
    "websiteId": "${UMAMI_WEBSITE_ID}",
    "domains": "${UMAMI_DOMAINS}"
  },
  "legal": {
    "imprintHtml": "$(echo "${LEGAL_IMPRINT_HTML}" | sed 's/"/\\"/g')",
    "privacyHtml": "$(echo "${LEGAL_PRIVACY_HTML}" | sed 's/"/\\"/g')"
  }
}
EOF

exec "$@"
