FROM node:latest as build

WORKDIR /app

COPY ./ /app/

# Ensure .env file exists
RUN mv .env* .env || true
RUN touch .env
RUN cat .env

# Fix buggy replacement of COOLIFY_URL in .env
RUN COOLIFY_URL_VALUE=$(grep '^COOLIFY_URL=' .env | cut -d '=' -f2) && \
    sed -i "s|\$COOLIFY_URL|$COOLIFY_URL_VALUE|g" .env


RUN ls -la /app

RUN ls -la .

# Export environment variables from .env
# This assumes that .env contains lines like VARIABLE=value
# and does not contain spaces around the '='
RUN set -a && \
    echo "Listing /app directory:" && \
    ls -la /app && \
    echo "Displaying contents of /app/.env:" && \
    cat /app/.env && \
    . /app/.env && \
    set +a && \
    echo "Generating environment.prod.ts" && \
    cat > src/environments/environment.prod.ts <<EOF
export const environment = {
  clientUrl: '${APPLICATION_CLIENT_URL}',
  serverUrl: '${APPLICATION_SERVER_URL}',
  keycloak: {
    url: '${KEYCLOAK_URL}',
    realm: '${KEYCLOAK_REALM}',
    clientId: '${KEYCLOAK_CLIENT_ID}',
    skipLoginPage: ${KEYCLOAK_SKIP_LOGIN}
  },
  umami: {
    enabled: '${UMAMI_ENABLED}',
    scriptUrl: '${UMAMI_SCRIPT_URL}',
    websiteId: '${UMAMI_WEBSITE_ID}',
    domains: '${UMAMI_DOMAINS}'
  },
  legal: {
    imprintHtml: '${LEGAL_IMPRINT_HTML}'
  }
};
EOF

RUN npm install
RUN npm run build

FROM nginx:latest

COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
