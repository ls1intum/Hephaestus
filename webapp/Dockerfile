FROM node:latest as build

WORKDIR /app

COPY ./ /app/

RUN printenv
RUN cat .env
#11 0.081 COOLIFY_URL=ws8008.35.207.83.232.sslip.io
#11 0.081 COOLIFY_BRANCH=chore/configure-server-url
#11 0.081 COOLIFY_CONTAINER_NAME=ws80008-213620030145
#11 0.081 NG_APPLICATION_SERVER_URL=https://api.$COOLIFY_URL

RUN COOLIFY_URL_VALUE=$(grep '^COOLIFY_URL=' .env | cut -d '=' -f2) && \
    sed -i "s|\$COOLIFY_URL|$COOLIFY_URL_VALUE|g" .env

RUN cat .env

RUN npm install
RUN npm run build

FROM nginx:latest

COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html

EXPOSE 80
