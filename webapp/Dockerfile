# syntax=docker/dockerfile:1.26@sha256:ecfaec9ed6d810b56388c508f4121597bfbba70d41a6dfeee4d8cad5f295fc32
FROM ghcr.io/pnpm/pnpm:12.0.0@sha256:bce5ae25ef95edd79e696d7fa8489b80561ef660100fd35bd0286d0f90db3dcc AS build
WORKDIR /repo
RUN pnpm runtime set node 24.19.0 -g
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json .oxfmtrc.json /repo/
COPY patches/ /repo/patches/
COPY webapp/package.json /repo/webapp/
COPY docs/package.json /repo/docs/
RUN --mount=type=cache,id=pnpm-store,target=/pnpm/store,sharing=locked \
    pnpm fetch --filter webapp
RUN --mount=type=cache,id=pnpm-store,target=/pnpm/store,sharing=locked \
    pnpm install --offline --frozen-lockfile --filter webapp
COPY webapp/ /repo/webapp/
RUN --mount=type=secret,id=sentry_auth_token,required=false \
    --mount=type=secret,id=sentry_org,required=false \
    --mount=type=secret,id=sentry_project,required=false \
    export SENTRY_AUTH_TOKEN="$(cat /run/secrets/sentry_auth_token 2>/dev/null || true)" && \
    export SENTRY_ORG="$(cat /run/secrets/sentry_org 2>/dev/null || true)" && \
    export SENTRY_PROJECT="$(cat /run/secrets/sentry_project 2>/dev/null || true)" && \
    pnpm --filter webapp run build && \
    find webapp/dist -name '*.map' -delete && \
    ! find webapp/dist -name '*.map' -print -quit | grep -q .

FROM nginx:stable-alpine@sha256:97d490c12ba55b4946b01546d1c3ed324e8d41ab1c9fcb2a616aa470620e5b46
ARG COOLIFY_BRANCH
ARG SOURCE_COMMIT
ENV BUILD_GIT_BRANCH=$COOLIFY_BRANCH BUILD_GIT_COMMIT=$SOURCE_COMMIT
RUN apk add --no-cache bash sed grep curl
COPY --from=build /repo/webapp/dist /usr/share/nginx/html
COPY webapp/docker/entrypoint.sh /entrypoint.sh
COPY webapp/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY webapp/src/environment/index.ts /app/src/environment/index.ts
RUN chmod +x /entrypoint.sh
EXPOSE 80
ENTRYPOINT ["/bin/bash", "-c", "/entrypoint.sh && exec nginx -g 'daemon off;'"]
