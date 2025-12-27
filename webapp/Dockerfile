# Stage 1: Build
FROM node:22-alpine AS build
WORKDIR /app

# Copy package.json for dependency installation
COPY webapp/package.json /app/

# Install dependencies
# Note: Using npm install instead of npm ci because this is a workspace package
# and the package-lock.json is at the monorepo root, not in this folder
RUN npm install --ignore-scripts

# Copy the webapp application code
COPY webapp/ /app/

# Copy intelligence-service shared types (required for @intelligence-service/* imports)
COPY server/intelligence-service/src/mentor/ /server/intelligence-service/src/mentor/
COPY server/intelligence-service/src/shared/ /server/intelligence-service/src/shared/

# Create a symlink so the shared code can resolve node_modules from webapp's dependencies
# This is needed because imports like 'uuid' and 'ai' in the shared code need to resolve
# to the webapp's node_modules since we don't install intelligence-service deps in Docker
RUN ln -s /app/node_modules /server/intelligence-service/node_modules

# Build the React application
RUN npm run build

# Stage 2: Serve
FROM nginx:stable-alpine
RUN apk update && apk upgrade && \
    apk add --no-cache bash sed grep && \
    rm -rf /var/cache/apk/*

COPY --from=build /app/dist /usr/share/nginx/html
COPY webapp/docker/entrypoint.sh /entrypoint.sh
COPY webapp/docker/nginx.conf /etc/nginx/conf.d/default.conf

# Copy TypeScript source for entrypoint.sh to extract env var names (single source of truth)
COPY webapp/src/environment/index.ts /app/src/environment/index.ts

RUN chmod +x /entrypoint.sh

EXPOSE 80
ENTRYPOINT ["/bin/bash", "-c", "/entrypoint.sh && exec nginx -g 'daemon off;'"]
