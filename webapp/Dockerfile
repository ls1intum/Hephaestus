# syntax=docker/dockerfile:1.4

# Stage 1: Build
FROM node:24-alpine AS build
ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH
WORKDIR /repo

# Enable Corepack-managed pnpm and bake the pinned version into the image so the
# first `pnpm install` does not need network access to resolve packageManager.
# Version explicit (not `--activate` alone) because package.json is COPYed later.
RUN corepack enable && corepack prepare pnpm@11.1.2 --activate

# Copy the workspace manifests first to maximize layer caching
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json .npmrc /repo/
COPY webapp/package.json /repo/webapp/
COPY server/agent-extensions/package.json /repo/server/agent-extensions/
COPY docs/package.json /repo/docs/

# Install webapp deps (no internal workspace deps, so no `...` needed)
RUN --mount=type=cache,target=/pnpm/store,sharing=locked \
    pnpm install --frozen-lockfile --filter webapp

# Copy the webapp application code
COPY webapp/ /repo/webapp/

# Build the React application
WORKDIR /repo/webapp
RUN pnpm run build

# Stage 2: Serve
FROM nginx:stable-alpine

# Bake git metadata into the image using build arguments provided by Coolify
ARG COOLIFY_BRANCH
ARG SOURCE_COMMIT
# Use distinct names to avoid runtime overrides by Coolify
ENV BUILD_GIT_BRANCH=$COOLIFY_BRANCH
ENV BUILD_GIT_COMMIT=$SOURCE_COMMIT

RUN apk update && apk upgrade && \
    apk add --no-cache bash sed grep curl && \
    rm -rf /var/cache/apk/*

COPY --from=build /repo/webapp/dist /usr/share/nginx/html
COPY webapp/docker/entrypoint.sh /entrypoint.sh
COPY webapp/docker/nginx.conf /etc/nginx/conf.d/default.conf

# Copy TypeScript source for entrypoint.sh to extract env var names (single source of truth)
COPY webapp/src/environment/index.ts /app/src/environment/index.ts

RUN chmod +x /entrypoint.sh

EXPOSE 80
ENTRYPOINT ["/bin/bash", "-c", "/entrypoint.sh && exec nginx -g 'daemon off;'"]
