# Stage 1: Build
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Serve
FROM nginx:stable-alpine
RUN apk update && apk upgrade && \
    apk add --no-cache bash sed && \
    rm -rf /var/cache/apk/*

COPY --from=build /app/dist /usr/share/nginx/html
COPY docker/entrypoint.sh /entrypoint.sh
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

# Copy TypeScript source for entrypoint.sh to extract env var names (single source of truth)
COPY src/environment/index.ts /app/src/environment/index.ts

RUN chmod +x /entrypoint.sh

EXPOSE 80
ENTRYPOINT ["/bin/bash", "-c", "/entrypoint.sh && exec nginx -g 'daemon off;'"]
