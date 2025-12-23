FROM node:22-alpine AS base

FROM base AS builder

# Some native deps expect glibc symbols on Alpine; gcompat provides minimal compat
RUN apk add --no-cache gcompat
WORKDIR /app

# Copy files needed for install and build
COPY package.json tsconfig.json ./
COPY src ./src

# Install deps, build, and prune dev deps for smaller runtime
# Note: Using npm install instead of npm ci because this is a workspace package
# and the package-lock.json is at the monorepo root, not in this folder
RUN npm install --ignore-scripts \
	&& npm run build \
	&& npm prune --production

FROM base AS runner
ENV NODE_ENV=production
WORKDIR /app

# Install curl for healthcheck (matches compose.core.yaml)
RUN apk add --no-cache curl

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

CMD ["node", "/app/dist/src/index.js"]

