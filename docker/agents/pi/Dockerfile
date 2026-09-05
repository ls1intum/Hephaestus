# syntax=docker/dockerfile:1.26@sha256:ecfaec9ed6d810b56388c508f4121597bfbba70d41a6dfeee4d8cad5f295fc32
# renovate: datasource=node-version depName=node
ARG NODE_VERSION=24.19.0
FROM ghcr.io/pnpm/pnpm:12.0.0@sha256:bce5ae25ef95edd79e696d7fa8489b80561ef660100fd35bd0286d0f90db3dcc AS dependencies
ARG NODE_VERSION
WORKDIR /opt/pi-sdk
RUN pnpm runtime set node ${NODE_VERSION} -g
COPY pi/package.json pi/pnpm-lock.yaml pi/pnpm-workspace.yaml ./
RUN pnpm install --prod --frozen-lockfile --ignore-scripts

FROM node:${NODE_VERSION}-slim@sha256:a9f5f7c91a432850b2a8a7797adf5eadb6c733ceed61167806cee7ea7fbc29df
ARG NODE_VERSION

# The base digest is pinned, so Debian security updates published after the upstream image was
# built can reach it only here; Renovate bumps the digest only when upstream republishes one.
#
# SOURCE_COMMIT is what makes that upgrade actually run. The builder imports the `cache-main`
# registry cache and nothing else in this layer changes between builds, so without a per-build
# input BuildKit would replay a weeks-old package set and the upgrade would silently stop
# happening — the exact failure this layer exists to prevent. webapp/Dockerfile gets the same
# argument, one instruction ahead of its own upgrade.
ARG SOURCE_COMMIT
RUN echo "source commit: ${SOURCE_COMMIT:-<unset — local build>}" && \
    apt-get update -qq && apt-get upgrade -y -qq && \
    apt-get install -y --no-install-recommends git findutils tree jq ca-certificates grep && \
    rm -rf /var/lib/apt/lists/*

RUN groupmod --new-name agent node && \
    usermod --login agent --home /home/agent --move-home --shell /bin/bash node

# renovate: datasource=npm depName=@earendil-works/pi-coding-agent
ARG PI_VERSION=0.84.4
COPY --from=dependencies --chown=1000:1000 /opt/pi-sdk /opt/pi-sdk
RUN test -d /opt/pi-sdk/node_modules/@earendil-works/pi-coding-agent \
      || (echo "Pi SDK not found at /opt/pi-sdk/node_modules" >&2; exit 1) && \
    mkdir -p /workspace && chown 1000:1000 /workspace

RUN mkdir -p /tmp/abi-check && ln -sf /opt/pi-sdk/node_modules /tmp/abi-check/node_modules && \
    cd /tmp/abi-check && \
    printf 'const sdk = await import("@earendil-works/pi-coding-agent");\nif (!sdk || typeof sdk !== "object") { throw new Error("Pi SDK import yielded no module namespace"); }\nconsole.log("pi sdk exports:", Object.keys(sdk).length);\n' > check.ts && \
    node --permission --allow-fs-read=/tmp/abi-check --allow-fs-read=/opt/pi-sdk check.ts && \
    mkdir -p /tmp/abi-check/work/nested /tmp/abi-check/agent /tmp/abi-check/home && \
    printf 'SENTINEL-ORCHESTRATOR\n' > /tmp/abi-check/agent/AGENTS.md && \
    printf 'DECOY\n' > /tmp/abi-check/work/AGENTS.md && \
    printf '%s\n' \
      'import { readFileSync } from "node:fs";' \
      'import { DefaultResourceLoader, SettingsManager } from "@earendil-works/pi-coding-agent";' \
      'const cwd = "/tmp/abi-check/work/nested";' \
      'const agentDir = "/tmp/abi-check/agent";' \
      '// Resolves resources exactly the way the runner does, under the same permissions: an untrusted' \
      '// project so the SDK never walks the ancestors of the working directory for skills, discovery of' \
      '// context files off, and the staged orchestrator handed over by name. The working directory sits' \
      '// below ancestors outside the allowlist and beside a decoy AGENTS.md: an SDK that reaches out fails' \
      '// the build, and so does a loader that carries anything but the staged orchestrator.' \
      'const settingsManager = SettingsManager.create(cwd, agentDir, { projectTrusted: false });' \
      'const orchestratorPath = `${agentDir}/AGENTS.md`;' \
      'const orchestrator = readFileSync(orchestratorPath, "utf8");' \
      'const loader = new DefaultResourceLoader({ cwd, agentDir, settingsManager, noContextFiles: true, agentsFilesOverride: () => ({ agentsFiles: [{ path: orchestratorPath, content: orchestrator }] }) });' \
      'await loader.reload();' \
      'const { agentsFiles } = loader.getAgentsFiles();' \
      'if (agentsFiles.length !== 1 || !agentsFiles[0].content.includes("SENTINEL-ORCHESTRATOR")) { throw new Error(`expected the staged orchestrator alone, got ${JSON.stringify(agentsFiles.map((f) => f.path))}`); }' \
      > runner-path-check.ts && \
    HOME=/tmp/abi-check/home node --permission --allow-fs-read=/tmp/abi-check --allow-fs-read=/opt/pi-sdk runner-path-check.ts && \
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
    node --version; \
    # `!` exempts a command from errexit and only the last iteration's status would escape the
    # loop, so each hit must fail explicitly.
    for manager in npm npx corepack yarn yarnpkg pnpm pnpx; do \
        if command -v "$manager" >/dev/null 2>&1; then \
            echo "FATAL: '$manager' resolves to $(command -v "$manager"); this image must carry no package manager." >&2; exit 1; \
        fi; \
    done

LABEL hephaestus.agent.runtime-contract=2
LABEL hephaestus.agent.node-version=${NODE_VERSION}
LABEL hephaestus.agent.pi-version=${PI_VERSION}

USER 1000:1000
WORKDIR /workspace
