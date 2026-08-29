# syntax=docker/dockerfile:1.4
# renovate: datasource=docker depName=node
ARG NODE_VERSION=24.19.0
FROM node:${NODE_VERSION}-slim@sha256:a9f5f7c91a432850b2a8a7797adf5eadb6c733ceed61167806cee7ea7fbc29df

RUN apt-get update -qq && apt-get install -y --no-install-recommends git findutils tree jq ca-certificates grep && \
    rm -rf /var/lib/apt/lists/*

RUN groupmod --new-name agent node && \
    usermod --login agent --home /home/agent --move-home --shell /bin/bash node

# renovate: datasource=npm depName=@earendil-works/pi-coding-agent
ARG PI_VERSION=0.84.3
RUN mkdir -p /opt/pi-sdk && cd /opt/pi-sdk && \
    printf '{"name":"hephaestus-pi-sdk","private":true,"dependencies":{"@earendil-works/pi-coding-agent":"%s"}}\n' \
        "${PI_VERSION}" > package.json && \
    npm install --omit=dev --ignore-scripts --no-audit --no-fund && \
    test -d /opt/pi-sdk/node_modules/@earendil-works/pi-coding-agent \
      || (echo "Pi SDK not found at /opt/pi-sdk/node_modules" >&2; exit 1) && \
    chown -R 1000:1000 /opt/pi-sdk && \
    mkdir -p /workspace && chown 1000:1000 /workspace

RUN mkdir -p /tmp/abi-check && ln -sf /opt/pi-sdk/node_modules /tmp/abi-check/node_modules && \
    cd /tmp/abi-check && \
    printf 'const sdk = await import("@earendil-works/pi-coding-agent");\nif (!sdk || typeof sdk !== "object") { throw new Error("Pi SDK import yielded no module namespace"); }\nconsole.log("pi sdk exports:", Object.keys(sdk).length);\n' > check.ts && \
    node --permission --allow-fs-read=/tmp/abi-check --allow-fs-read=/opt/pi-sdk check.ts && \
    ! node --permission --allow-fs-read=/tmp/abi-check --allow-fs-read=/opt/pi-sdk \
      -e 'require("node:fs").readFileSync("/etc/passwd")' 2>/dev/null && \
    ! node --permission --allow-fs-read=/tmp/abi-check --allow-fs-read=/opt/pi-sdk \
      -e 'require("node:child_process").spawnSync("true")' 2>/dev/null && \
    rm -rf /tmp/abi-check

COPY --chown=1000:1000 precompute/runner.ts /opt/precompute/runner.ts
COPY --chown=1000:1000 precompute/lib/ /opt/precompute/lib/

RUN git config --system core.hooksPath /nonexistent && \
    git config --system core.fsmonitor false && \
    git config --system safe.directory /workspace/repo

ENV HOME=/home/agent
ENV GIT_PAGER=cat
ENV GIT_TERMINAL_PROMPT=0
ENV LANG=C.UTF-8

RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
        /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack \
        /usr/local/bin/yarn /usr/local/bin/yarnpkg /usr/local/bin/pnpm /usr/local/bin/pnpx && \
    set -eu; \
    if command -v bun >/dev/null 2>&1; then \
        echo "FATAL: Bun resolves to $(command -v bun); this image must be Node-only." >&2; exit 1; \
    fi; \
    found="$(find /usr/bin /usr/local/bin /bin /sbin /usr/sbin /opt -maxdepth 3 -name bun \
        \( -type f -o -type l \) 2>/dev/null || true)"; \
    if [ -n "$found" ]; then \
        echo "FATAL: Bun artefacts present in the image:" >&2; echo "$found" >&2; exit 1; \
    fi; \
    node --version; \
    for manager in npm npx corepack yarn yarnpkg pnpm pnpx; do ! command -v "$manager"; done

LABEL hephaestus.agent.runtime-contract=2
LABEL hephaestus.agent.node-version=${NODE_VERSION}
LABEL hephaestus.agent.pi-version=${PI_VERSION}

USER 1000:1000
WORKDIR /workspace
