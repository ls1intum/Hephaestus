# syntax=docker/dockerfile:1.24@sha256:87999aa3d42bdc6bea60565083ee17e86d1f3339802f543c0d03998580f9cb89
ARG NODE_TAG=24-slim@sha256:242549cd46785b480c832479a730f4f2a20865d61ea2e404fdb2a5c3d3b73ecf
FROM node:${NODE_TAG}

ARG TARGETARCH

RUN apt-get update -qq && apt-get install -y --no-install-recommends git findutils tree jq curl ca-certificates unzip libjemalloc2 && \
    rm -rf /var/lib/apt/lists/*

# renovate: datasource=npm depName=@earendil-works/pi-coding-agent
ARG PI_VERSION=0.74.1
# Install the Pi SDK into a deterministic, hash-free path at /opt/pi-sdk/node_modules.
# We use plain `npm install --prefix` here — not pnpm globals — because pnpm 11 stores
# globals under /usr/local/pnpm/global/v11/<content-hash>/, which the previous Dockerfile
# fished out via `ls | head -n 1` (fragile if anything else ever lands in that tree).
# `npm install --prefix` writes a real /opt/pi-sdk/node_modules/@earendil-works/pi-coding-agent
# directory with no symlink indirection. The runner imports via the symlink PiRuntimeFactory
# plants at /<workspace>/node_modules → /opt/pi-sdk/node_modules.
RUN mkdir -p /opt/pi-sdk && \
    npm install --prefix /opt/pi-sdk --omit=dev --no-audit --no-fund --no-progress \
        @earendil-works/pi-coding-agent@${PI_VERSION} && \
    test -d /opt/pi-sdk/node_modules/@earendil-works/pi-coding-agent \
      || (echo "Pi SDK not found at /opt/pi-sdk/node_modules" >&2; exit 1) && \
    npm cache clean --force && \
    mkdir -p /home/agent /workspace && \
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

# Memory tunables. ALL allocator-tuning env vars are scoped to the long-lived Node mentor
# runner only — set per-process by PiRuntimeFactory#nodeEnvFor at spawn time. Nothing is set
# image-wide. Reason: this image also runs `bun` (mimalloc), `git`, `jq`, `curl` on the
# practice-review precompute critical path; preloading jemalloc or capping glibc arenas onto
# those is unmeasured (we explicitly rejected unmeasured tuning in wave 25 — applying it here
# image-wide would be the same mistake).
#
# Here we only install libjemalloc2 + create the per-arch symlink to a stable path so
# PiRuntimeFactory can hard-code one LD_PRELOAD literal across amd64+arm64 buildx.
RUN arch="${TARGETARCH:-$(dpkg --print-architecture)}" && \
    case "${arch}" in \
        amd64|x86_64) src="/usr/lib/x86_64-linux-gnu/libjemalloc.so.2" ;; \
        arm64|aarch64) src="/usr/lib/aarch64-linux-gnu/libjemalloc.so.2" ;; \
        *) echo "Unsupported architecture for libjemalloc: ${arch}" >&2; exit 1 ;; \
    esac && \
    test -f "${src}" || (echo "libjemalloc.so.2 missing at ${src}" >&2; exit 1) && \
    ln -sf "${src}" /usr/local/lib/libjemalloc.so.2

USER 1000:1000
WORKDIR /workspace
