FROM node:latest as build

WORKDIR /app

COPY ./ /app/

# Fix buggy replacement of COOLIFY_URL in .env
RUN COOLIFY_URL_VALUE=$(grep '^COOLIFY_URL=' .env | cut -d '=' -f2) && \
    sed -i "s|\$COOLIFY_URL|$COOLIFY_URL_VALUE|g" .env

RUN npm install
RUN npm run build

FROM nginx:latest

COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html

EXPOSE 80
