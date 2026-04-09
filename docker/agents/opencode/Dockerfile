# syntax=docker/dockerfile:1.4
# OpenCode agent container image
# Used by OpenCodeAgentAdapter — command set by SandboxSpec, no entrypoint.
ARG NODE_TAG=22-slim
FROM node:${NODE_TAG}

ARG TARGETARCH

RUN apt-get update -qq && apt-get install -y --no-install-recommends git findutils tree jq curl ca-certificates unzip && \
    rm -rf /var/lib/apt/lists/*

ARG OPENCODE_VERSION=1.2.26
RUN npm install -g opencode-ai@${OPENCODE_VERSION} && \
    npm cache clean --force && \
    mkdir -p /home/agent /workspace && \
    chown -R 1000:1000 /home/agent /workspace

# Bun runtime for precomputation scripts (static analysis before agent runs)
ARG BUN_VERSION=1.3.11
RUN case "${TARGETARCH}" in \
        amd64) bun_arch="x64" ;; \
        arm64) bun_arch="aarch64" ;; \
        *) echo "Unsupported TARGETARCH: ${TARGETARCH}" >&2; exit 1 ;; \
    esac && \
    curl -fsSL "https://github.com/oven-sh/bun/releases/download/bun-v${BUN_VERSION}/bun-linux-${bun_arch}.zip" -o /tmp/bun.zip && \
    unzip -o /tmp/bun.zip -d /tmp && \
    mv "/tmp/bun-linux-${bun_arch}/bun" /usr/local/bin/bun && \
    chmod +x /usr/local/bin/bun && \
    rm -rf /tmp/bun*

# Precompute runner + shared libraries (practice scripts injected at runtime from DB)
COPY --chown=1000:1000 precompute/runner.ts /opt/precompute/runner.ts
COPY --chown=1000:1000 precompute/lib/ /opt/precompute/lib/

# Git security: neutralize hooks and external commands from mounted repos.
# System-level config cannot be overridden by repo-local .git/config.
RUN git config --system core.hooksPath /nonexistent && \
    git config --system core.fsmonitor false && \
    git config --system safe.directory /workspace/repo

ENV HOME=/home/agent
ENV GIT_PAGER=cat
ENV GIT_TERMINAL_PROMPT=0
ENV LANG=C.UTF-8

USER 1000:1000
WORKDIR /workspace
