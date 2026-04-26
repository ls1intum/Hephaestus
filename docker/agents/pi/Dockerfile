# syntax=docker/dockerfile:1.23@sha256:2780b5c3bab67f1f76c781860de469442999ed1a0d7992a5efdf2cffc0e3d769
# Pi coding agent container image
# Used by PiAgentAdapter — command set by SandboxSpec, no entrypoint.
ARG NODE_TAG=22-slim@sha256:d415caac2f1f77b98caaf9415c5f807e14bc8d7bdea62561ea2fef4fbd08a73c
FROM node:${NODE_TAG}

ARG TARGETARCH

RUN apt-get update -qq && apt-get install -y --no-install-recommends git findutils tree jq curl ca-certificates unzip && \
    rm -rf /var/lib/apt/lists/*

ARG PI_VERSION=0.65.0
RUN npm install -g @mariozechner/pi-coding-agent@${PI_VERSION} && \
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

# Git security baseline: neutralize common hooks/external-command vectors in the image.
# Runtime env-based git config in DockerSandboxAdapter provides the hard override layer.
RUN git config --system core.hooksPath /nonexistent && \
    git config --system core.fsmonitor false && \
    git config --system safe.directory /workspace/repo

ENV HOME=/home/agent
ENV GIT_PAGER=cat
ENV GIT_TERMINAL_PROMPT=0
ENV LANG=C.UTF-8

USER 1000:1000
WORKDIR /workspace
