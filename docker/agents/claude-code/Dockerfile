# syntax=docker/dockerfile:1.22@sha256:4a43a54dd1fedceb30ba47e76cfcf2b47304f4161c0caeac2db1c61804ea3c91
# Claude Code agent container image
# Used by ClaudeCodeAgentAdapter — command set by SandboxSpec, no entrypoint.
ARG NODE_TAG=22-slim@sha256:80fdb3f57c815e1b638d221f30a826823467c4a56c8f6a8d7aa091cd9b1675ea
FROM node:${NODE_TAG}

RUN apt-get update -qq && apt-get install -y --no-install-recommends git findutils tree jq && \
    rm -rf /var/lib/apt/lists/*

ARG CLAUDE_CODE_VERSION=2.1.76
RUN npm install -g @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION} && \
    npm cache clean --force && \
    mkdir -p /home/agent/.claude /workspace && \
    echo '{"hasCompletedOnboarding":true}' > /home/agent/.claude.json && \
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
