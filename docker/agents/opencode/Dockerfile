# syntax=docker/dockerfile:1.4
# OpenCode agent container image
# Used by OpenCodeAgentAdapter — command set by SandboxSpec, no entrypoint.
ARG NODE_TAG=22-slim
FROM node:${NODE_TAG}

RUN apt-get update -qq && apt-get install -y --no-install-recommends git findutils tree jq && \
    rm -rf /var/lib/apt/lists/*

ARG OPENCODE_VERSION=1.2.26
RUN npm install -g opencode-ai@${OPENCODE_VERSION} && \
    npm cache clean --force && \
    mkdir -p /home/agent /workspace && \
    chown -R 1000:1000 /home/agent /workspace

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
