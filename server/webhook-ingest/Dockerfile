# syntax=docker/dockerfile:1.23@sha256:2780b5c3bab67f1f76c781860de469442999ed1a0d7992a5efdf2cffc0e3d769

FROM node:22-alpine@sha256:4d64b49e6c891c8fc821007cb1cdc6c0db7773110ac2c34bf2e6960adef62ed3 AS base

FROM base AS builder

# Some native deps expect glibc symbols on Alpine; gcompat provides minimal compat
RUN apk add --no-cache gcompat
WORKDIR /app

# Copy files needed for install and build
COPY package.json tsconfig.json ./
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

# Install curl for healthcheck (matches compose.core.yaml)
# Install dumb-init for proper PID 1 signal handling
# (Node.js doesn't properly forward signals when running as PID 1)
RUN apk add --no-cache curl dumb-init

# Run as non-root user for security
RUN addgroup --system --gid 1001 nodejs \
	&& adduser --system --uid 1001 hono

# Copy pruned deps and build artifacts with proper ownership
COPY --from=builder --chown=hono:nodejs /app/node_modules /app/node_modules
COPY --from=builder --chown=hono:nodejs /app/dist /app/dist
COPY --from=builder --chown=hono:nodejs /app/package.json /app/package.json

USER hono
EXPOSE 4200

# Health check matching compose.core.yaml
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=3s \
	CMD curl -f http://localhost:4200/health || exit 1

# Use dumb-init as entrypoint for proper signal handling
# This ensures SIGTERM is properly forwarded to the Node.js process
ENTRYPOINT ["dumb-init", "--"]
CMD ["node", "/app/dist/index.js"]

