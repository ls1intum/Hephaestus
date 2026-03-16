# syntax=docker/dockerfile:1.22@sha256:4a43a54dd1fedceb30ba47e76cfcf2b47304f4161c0caeac2db1c61804ea3c91
# OpenCode agent container image
# Used by OpenCodeAgentAdapter — command set by SandboxSpec, no entrypoint.
ARG NODE_TAG=22-slim@sha256:9c2c405e3ff9b9afb2873232d24bb06367d649aa3e6259cbe314da59578e81e9
FROM node:${NODE_TAG}

ARG OPENCODE_VERSION=1.2.26
RUN npm install -g opencode-ai@${OPENCODE_VERSION} && \
    npm cache clean --force && \
    mkdir -p /home/agent /workspace && \
    chown -R 1000:1000 /home/agent /workspace

ENV HOME=/home/agent

USER 1000:1000
WORKDIR /workspace
