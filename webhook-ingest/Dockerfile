# syntax=docker/dockerfile:1.4

FROM node:24-alpine AS base

FROM base AS builder

# Some native deps expect glibc symbols on Alpine; gcompat provides minimal compat
RUN apk add --no-cache gcompat
ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH
# Bake the pinned pnpm version into the image so the first install does not need
# network. Version explicit (not `--activate` alone) because package.json is
# COPYed in a later step.
RUN corepack enable && corepack prepare pnpm@11.1.2 --activate
WORKDIR /repo

# Copy workspace manifests for layer caching
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json .npmrc /repo/
COPY webapp/package.json /repo/webapp/
COPY webhook-ingest/package.json /repo/webhook-ingest/
COPY server/agent-extensions/package.json /repo/server/agent-extensions/
COPY docs/package.json /repo/docs/

# Install webhook-ingest + its transitive workspace deps
RUN --mount=type=cache,target=/pnpm/store,sharing=locked \
    pnpm install --frozen-lockfile --filter webhook-ingest...

# Copy source + tsconfig for build
COPY webhook-ingest/tsconfig.json /repo/webhook-ingest/
COPY webhook-ingest/src /repo/webhook-ingest/src

# Build the application
RUN pnpm --filter webhook-ingest run build

# Produce an isolated production deployment in /deploy with only prod deps.
# --legacy is required in pnpm 11 unless inject-workspace-packages is enabled
# (see https://pnpm.io/cli/deploy). webhook-ingest has no workspace deps today
# but --legacy keeps the command stable if one is ever added.
RUN pnpm --filter webhook-ingest deploy --prod --legacy /deploy

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

# Copy isolated prod deployment from builder (node_modules + package.json + dist)
COPY --from=builder --chown=hono:nodejs /deploy/node_modules /app/node_modules
COPY --from=builder --chown=hono:nodejs /deploy/dist /app/dist
COPY --from=builder --chown=hono:nodejs /deploy/package.json /app/package.json

USER hono
EXPOSE 4200

# Health check matching compose.core.yaml
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=3s \
	CMD curl -f http://localhost:4200/health || exit 1

# Use dumb-init as entrypoint for proper signal handling
# This ensures SIGTERM is properly forwarded to the Node.js process
ENTRYPOINT ["dumb-init", "--"]
CMD ["node", "/app/dist/index.js"]
