# syntax=docker/dockerfile:1.4
ARG DEBIAN_TAG=bookworm-slim
FROM debian:${DEBIAN_TAG}

ARG TARGETARCH

RUN apt-get update -qq && apt-get install -y --no-install-recommends git findutils tree jq curl ca-certificates unzip && \
    rm -rf /var/lib/apt/lists/*

# Refresh the release-asset hashes from SHASUMS256.txt with every version bump.
# renovate: datasource=github-releases depName=oven-sh/bun extractVersion=^bun-v(?<version>.*)$
ARG BUN_VERSION=1.4.0
RUN arch="${TARGETARCH:-$(dpkg --print-architecture)}" && \
    case "${arch}" in \
        amd64|x86_64) bun_arch="x64"; bun_sha256="2d03fb5fb83ac8b567aca0a281b2ce1a1a19d488f56c2968d88c3f25e92fe452" ;; \
        arm64|aarch64) bun_arch="aarch64"; bun_sha256="4b1a332ee861983eb93bcfe6f770fff94e3e31b2c388bdaea3c8ed35e58eed0e" ;; \
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

# Provide a resolvable non-root home and passwd entry.
RUN groupadd --gid 1000 agent && \
    useradd --uid 1000 --gid 1000 --home-dir /home/agent --create-home --shell /bin/bash agent

# renovate: datasource=npm depName=@earendil-works/pi-coding-agent
ARG PI_VERSION=0.84.3
RUN mkdir -p /opt/pi-sdk && cd /opt/pi-sdk && \
    printf '{"name":"hephaestus-pi-sdk","private":true,"dependencies":{"@earendil-works/pi-coding-agent":"%s"}}\n' \
        "${PI_VERSION}" > package.json && \
    BUN_INSTALL_CACHE_DIR=/tmp/bun-cache bun install --production --ignore-scripts --no-progress && \
    rm -rf /tmp/bun-cache && \
    test -d /opt/pi-sdk/node_modules/@earendil-works/pi-coding-agent \
      || (echo "Pi SDK not found at /opt/pi-sdk/node_modules" >&2; exit 1) && \
    chown -R 1000:1000 /opt/pi-sdk && \
    mkdir -p /workspace && chown 1000:1000 /workspace

# Resolve the SDK through the same workspace symlink used at runtime.
RUN mkdir -p /tmp/abi-check && ln -sf /opt/pi-sdk/node_modules /tmp/abi-check/node_modules && \
    cd /tmp/abi-check && \
    printf 'const sdk = await import("@earendil-works/pi-coding-agent");\nif (!sdk || typeof sdk !== "object") { throw new Error("Pi SDK import yielded no module namespace"); }\nconsole.log("pi sdk exports:", Object.keys(sdk).length);\n' > check.ts && \
    bun check.ts && \
    rm -rf /tmp/abi-check

COPY --chown=1000:1000 precompute/runner.ts /opt/precompute/runner.ts
COPY --chown=1000:1000 precompute/lib/ /opt/precompute/lib/

# Neutralize git hooks/external-command vectors (DockerSandboxAdapter re-enforces at runtime).
RUN git config --system core.hooksPath /nonexistent && \
    git config --system core.fsmonitor false && \
    git config --system safe.directory /workspace/repo

ENV HOME=/home/agent
ENV GIT_PAGER=cat
ENV GIT_TERMINAL_PROMPT=0
ENV LANG=C.UTF-8

# Check both PATH and standard binary directories after all installation layers.
RUN set -eu; \
    for b in node nodejs npm npx corepack yarn pnpm; do \
        if command -v "$b" >/dev/null 2>&1; then \
            echo "FATAL: '$b' resolves to $(command -v "$b") — this image must be Bun-only." >&2; exit 1; \
        fi; \
    done; \
    found="$(find /usr/bin /usr/local/bin /bin /sbin /usr/sbin /opt -maxdepth 3 \
        \( -name node -o -name nodejs -o -name npm -o -name npx -o -name corepack \) \
        \( -type f -o -type l \) 2>/dev/null || true)"; \
    if [ -n "$found" ]; then \
        echo "FATAL: Node.js artefacts present in the image:" >&2; echo "$found" >&2; exit 1; \
    fi; \
    echo "OK: no node/npm in image; runtime is $(bun --version) at $(command -v bun)"

# The build above proves the runtime contract — Bun resolves and imports the SDK exactly as
# PiRuntimeFactory arranges it, and no Node survives. These labels are the only part of that proof a
# server can read before it commits a job to a container: it compares the contract version against
# SandboxLayout.RUNTIME_CONTRACT_VERSION, which AgentImageContractSyncTest pins to the line below.
# Bump both together when an older image could no longer run the staged runners. ADR 0031.
LABEL hephaestus.agent.runtime-contract=1
LABEL hephaestus.agent.bun-version=${BUN_VERSION}
LABEL hephaestus.agent.pi-version=${PI_VERSION}

USER 1000:1000
WORKDIR /workspace
