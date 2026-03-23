# syntax=docker/dockerfile:1.4
# Claude Code agent container image
# Used by ClaudeCodeAgentAdapter — command set by SandboxSpec, no entrypoint.
ARG NODE_TAG=22-slim
FROM node:${NODE_TAG}

RUN apt-get update -qq && apt-get install -y --no-install-recommends git && \
    rm -rf /var/lib/apt/lists/*

ARG CLAUDE_CODE_VERSION=2.1.76
RUN npm install -g @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION} && \
    npm cache clean --force && \
    mkdir -p /home/agent/.claude /workspace && \
    echo '{"hasCompletedOnboarding":true}' > /home/agent/.claude.json && \
    chown -R 1000:1000 /home/agent /workspace

ENV HOME=/home/agent

USER 1000:1000
WORKDIR /workspace
