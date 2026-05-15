# syntax=docker/dockerfile:1.4
ARG NODE_TAG=22-slim
FROM node:${NODE_TAG}

ARG TARGETARCH

RUN apt-get update -qq && apt-get install -y --no-install-recommends git findutils tree jq curl ca-certificates unzip libjemalloc2 && \
    rm -rf /var/lib/apt/lists/*

# renovate: datasource=npm depName=@earendil-works/pi-coding-agent
ARG PI_VERSION=0.74.0
RUN npm install -g @earendil-works/pi-coding-agent@${PI_VERSION} && \
    npm cache clean --force && \
    mkdir -p /home/agent/.pi /workspace && \
    chown -R 1000:1000 /home/agent /workspace

# Bun runtime for precomputation scripts (static analysis before agent runs)
ARG BUN_VERSION=1.3.11
RUN arch="${TARGETARCH:-$(dpkg --print-architecture)}" && \
    case "${arch}" in \
        amd64|x86_64) bun_arch="x64"; bun_sha256="8611ba935af886f05a6f38740a15160326c15e5d5d07adef966130b4493607ed" ;; \
        arm64|aarch64) bun_arch="aarch64"; bun_sha256="d13944da12a53ecc74bf6a720bd1d04c4555c038dfe422365356a7be47691fdf" ;; \
        *) echo "Unsupported TARGETARCH/architecture: ${arch}" >&2; exit 1 ;; \
    esac && \
    curl --fail --show-error --silent --location --retry 3 --retry-delay 2 \
        "https://github.com/oven-sh/bun/releases/download/bun-v${BUN_VERSION}/bun-linux-${bun_arch}.zip" \
        -o /tmp/bun.zip && \
    echo "${bun_sha256}  /tmp/bun.zip" | sha256sum -c - && \
    unzip -o /tmp/bun.zip -d /tmp && \
    mv "/tmp/bun-linux-${bun_arch}/bun" /usr/local/bin/bun && \
    chmod +x /usr/local/bin/bun && \
    rm -rf /tmp/bun*

# Precompute runner + shared libraries (practice scripts injected at runtime from DB)
COPY --chown=1000:1000 precompute/runner.ts /opt/precompute/runner.ts
COPY --chown=1000:1000 precompute/lib/ /opt/precompute/lib/

# Neutralize git hooks/external-command vectors (DockerSandboxAdapter re-enforces at runtime).
RUN git config --system core.hooksPath /nonexistent && \
    git config --system core.fsmonitor false && \
    git config --system safe.directory /workspace/repo

ENV HOME=/home/agent
ENV GIT_PAGER=cat
ENV GIT_TERMINAL_PROMPT=0
ENV LANG=C.UTF-8

# Memory tunables for long-lived Node runners (mentor multi-session containers):
#   MALLOC_ARENA_MAX=2 — caps glibc per-thread arenas. Default 8×ncpu × 64 MB virtual reservations
#       cause ~10-30 MB native fragmentation in Node servers with native addons (undici,
#       photon-node, …). Measured saving on the mentor stress harness: ~9 MB per runner steady-state.
#   LD_PRELOAD=libjemalloc — jemalloc returns dirty pages to the OS more aggressively than glibc
#       malloc (via MALLOC_CONF.dirty_decay_ms / muzzy_decay_ms). Once preloaded glibc never runs;
#       MALLOC_ARENA_MAX above is then a no-op but keep it for the rare case LD_PRELOAD is unset.
# Per-arch symlink to a stable name so the ENV LD_PRELOAD below is architecture-independent.
RUN arch="${TARGETARCH:-$(dpkg --print-architecture)}" && \
    case "${arch}" in \
        amd64|x86_64) src="/usr/lib/x86_64-linux-gnu/libjemalloc.so.2" ;; \
        arm64|aarch64) src="/usr/lib/aarch64-linux-gnu/libjemalloc.so.2" ;; \
        *) echo "Unsupported architecture for libjemalloc: ${arch}" >&2; exit 1 ;; \
    esac && \
    test -f "${src}" || (echo "libjemalloc.so.2 missing at ${src}" >&2; exit 1) && \
    ln -sf "${src}" /usr/local/lib/libjemalloc.so.2
ENV MALLOC_ARENA_MAX=2
ENV MALLOC_CONF="narenas:1,dirty_decay_ms:5000,muzzy_decay_ms:5000"
ENV LD_PRELOAD=/usr/local/lib/libjemalloc.so.2

USER 1000:1000
WORKDIR /workspace
