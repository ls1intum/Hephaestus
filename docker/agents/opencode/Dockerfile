# syntax=docker/dockerfile:1.4
# OpenCode agent container image
# Used by OpenCodeAgentAdapter — command set by SandboxSpec, no entrypoint.
ARG NODE_TAG=22-slim
FROM node:${NODE_TAG}

RUN apt-get update -qq && apt-get install -y --no-install-recommends git && \
    rm -rf /var/lib/apt/lists/*

ARG OPENCODE_VERSION=1.2.26
RUN npm install -g opencode-ai@${OPENCODE_VERSION} && \
    npm cache clean --force && \
    mkdir -p /home/agent /workspace && \
    chown -R 1000:1000 /home/agent /workspace

ENV HOME=/home/agent

USER 1000:1000
WORKDIR /workspace
