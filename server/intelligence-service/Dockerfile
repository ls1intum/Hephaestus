# syntax=docker/dockerfile:1.22@sha256:4a43a54dd1fedceb30ba47e76cfcf2b47304f4161c0caeac2db1c61804ea3c91

FROM node:22-alpine@sha256:4d64b49e6c891c8fc821007cb1cdc6c0db7773110ac2c34bf2e6960adef62ed3 AS base

FROM base AS builder

# Some native deps expect glibc symbols on Alpine; gcompat provides minimal compat
RUN apk add --no-cache gcompat
WORKDIR /app

# Copy minimal files needed for install and build
COPY package.json tsconfig.json drizzle.config.ts vitest.config.ts ./
COPY scripts ./scripts
COPY test ./test
COPY src ./src

# Install deps with BuildKit cache mount, build, and prune dev deps for smaller runtime
# Note: Using npm install instead of npm ci because this is a workspace package
# and the package-lock.json is at the monorepo root, not in this folder
RUN --mount=type=cache,target=/root/.npm \
    npm install --ignore-scripts --prefer-offline \
    && npm run build \
    && npm prune --production

FROM base AS runner
ENV NODE_ENV=production
WORKDIR /app

# Run as non-root user for security
RUN addgroup --system --gid 1001 nodejs \
	&& adduser --system --uid 1001 hono

# Copy pruned deps and build artifacts with proper ownership
COPY --from=builder --chown=hono:nodejs /app/node_modules /app/node_modules
COPY --from=builder --chown=hono:nodejs /app/dist /app/dist
COPY --from=builder --chown=hono:nodejs /app/package.json /app/package.json

USER hono
EXPOSE 8000

# Basic health check against the health endpoint (busybox wget is available)
HEALTHCHECK --interval=30s --timeout=3s --retries=3 CMD wget -qO- http://localhost:8000/health || exit 1

CMD ["node", "/app/dist/src/index.js"]
