# syntax=docker/dockerfile:1.22@sha256:4a43a54dd1fedceb30ba47e76cfcf2b47304f4161c0caeac2db1c61804ea3c91
# Claude Code agent container image
# Used by ClaudeCodeAgentAdapter — command set by SandboxSpec, no entrypoint.
ARG NODE_TAG=22-slim@sha256:4f77a690f2f8946ab16fe1e791a3ac0667ae1c3575c3e4d0d4589e9ed5bfaf3d
FROM node:${NODE_TAG}

ARG CLAUDE_CODE_VERSION=2.1.76
RUN npm install -g @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION} && \
    npm cache clean --force && \
    mkdir -p /home/agent/.claude /workspace && \
    echo '{"hasCompletedOnboarding":true}' > /home/agent/.claude.json && \
    chown -R 1000:1000 /home/agent /workspace

ENV HOME=/home/agent

USER 1000:1000
WORKDIR /workspace
