FROM node:22-alpine AS base

FROM base AS builder

# Some native deps expect glibc symbols on Alpine; gcompat provides minimal compat
RUN apk add --no-cache gcompat
WORKDIR /app

# Copy minimal files needed for install and build
COPY package*.json tsconfig.json src ./

# Install deps, build, and prune dev deps for smaller runtime
RUN npm ci \
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

CMD ["node", "/app/dist/index.js"]
