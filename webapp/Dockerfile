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

# Export environment variables from .env
# This assumes that .env contains lines like VARIABLE=value
# and does not contain spaces around the '='
RUN export $(grep -v '^#' .env | xargs) && \
    echo "Generating environment.prod.ts" && \
    cat > src/environments/environment.prod.ts <<EOF
export const environment = {
  serverUrl: '${APPLICATION_SERVER_URL}',
  umami: {
    enabled: '${UMAMI_ENABLED}',
    scriptUrl: '${UMAMI_SCRIPT_URL}',
    websiteId: '${UMAMI_WEBSITE_ID}',
    domains: '${UMAMI_DOMAINS}'
  }
};
EOF

RUN npm install
RUN npm run build

FROM nginx:latest

COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
