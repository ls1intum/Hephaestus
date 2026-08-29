# syntax=docker/dockerfile:1.7
FROM alpine:3.23.3 AS build
ARG TARGETARCH
ARG BUN_VERSION=1.4.0
WORKDIR /repo
# SHA-256 values are from the pinned Bun release's SHASUMS256.txt.
RUN apk add --no-cache curl unzip libstdc++ && \
    case "${TARGETARCH}" in \
      amd64) bun_arch="x64"; bun_sha256="83b5f12fd258dd8d4fdcaea65ede954366aa717dab399e20093ecab280d54e7a" ;; \
      arm64) bun_arch="aarch64"; bun_sha256="576300ce33ff16ffcd455bf178c2f095f9df845c6cc3d0284ba1c96ca0e80473" ;; \
      *) echo "Unsupported TARGETARCH: ${TARGETARCH}" >&2; exit 1 ;; \
    esac && \
    curl --fail --show-error --silent --location --retry 3 \
      "https://github.com/oven-sh/bun/releases/download/bun-v${BUN_VERSION}/bun-linux-${bun_arch}-musl.zip" -o /tmp/bun.zip && \
    echo "${bun_sha256}  /tmp/bun.zip" | sha256sum -c - && \
    unzip /tmp/bun.zip -d /tmp && mv "/tmp/bun-linux-${bun_arch}-musl/bun" /usr/local/bin/bun && \
    bun --version | grep -Fx "${BUN_VERSION}"
COPY bun.lock bunfig.toml package.json /repo/
COPY patches/ /repo/patches/
COPY webapp/package.json /repo/webapp/
COPY docs/package.json /repo/docs/
RUN --mount=type=cache,target=/root/.bun/install/cache,sharing=locked bun install --frozen-lockfile --filter webapp
COPY webapp/ /repo/webapp/
WORKDIR /repo/webapp
RUN bun run build
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
