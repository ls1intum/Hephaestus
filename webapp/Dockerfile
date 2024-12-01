FROM node:22 as build

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm install

COPY . ./

RUN npm run build

FROM nginx:stable-alpine

ENV APPLICATION_CLIENT_URL=https://default-client.url
ENV APPLICATION_SERVER_URL=https://default-server.url

ENV KEYCLOAK_URL=https://default-keycloak.url
ENV KEYCLOAK_REALM=default-realm
ENV KEYCLOAK_CLIENT_ID=default-client-id
ENV KEYCLOAK_SKIP_LOGIN=false

ENV LEGAL_IMPRINT_HTML="<p>Default Imprint</p>"
ENV LEGAL_PRIVACY_HTML="<p>Default Privacy</p>"

ENV UMAMI_ENABLED=false
ENV UMAMI_SCRIPT_URL=""
ENV UMAMI_WEBSITE_ID=""
ENV UMAMI_DOMAINS=""

COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html

COPY generate_config.sh /usr/local/bin/generate_config.sh
RUN chmod +x /usr/local/bin/generate_config.sh

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

ENTRYPOINT ["/usr/local/bin/generate_config.sh"]
CMD ["nginx", "-g", "daemon off;"]
