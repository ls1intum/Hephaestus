# syntax=docker/dockerfile:1.4
# Full-Bun agent sandbox: Bun is both the package manager and the runtime, and the final
# image contains no Node.js. Debian 12 is the same base `node:24-slim` was built on, so
# dropping Node changes no glibc/OS behaviour for git, jq, curl or the precompute path.
ARG DEBIAN_TAG=bookworm-slim
FROM debian:${DEBIAN_TAG}

ARG TARGETARCH

RUN apt-get update -qq && apt-get install -y --no-install-recommends git findutils tree jq curl ca-certificates unzip && \
    rm -rf /var/lib/apt/lists/*

# Bun: installed before the SDK because it *is* the package manager for the layer below.
# The sha256 pins below are per-version: a bump that does not refresh them fails the build.
# ci-quality-gates.yml greps this exact `ARG BUN_VERSION=` line to pin the CI toolchain.
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

# Debian slim has no uid 1000; git and bun both want a resolvable passwd entry for $HOME.
RUN groupadd --gid 1000 agent && \
    useradd --uid 1000 --gid 1000 --home-dir /home/agent --create-home --shell /bin/bash agent

# renovate: datasource=npm depName=@earendil-works/pi-coding-agent
ARG PI_VERSION=0.74.1
# Install the Pi SDK into a deterministic, hash-free path at /opt/pi-sdk/node_modules.
# `bun install` against a hand-written package.json (not `bun add`) keeps the resolved version
# exactly ${PI_VERSION} and the layer reproducible. The runner imports by bare specifier, which
# resolves through the symlink PiRuntimeFactory plants at /<workspace>/node_modules → here.
#
# Bun blocks lifecycle scripts by default and no `trustedDependencies` is declared.
# Three dependencies ship one: @google/genai (preinstall `echo`), protobufjs (postinstall that
# only ever writes a version-scheme warning to stderr) and koffi (install script that fetches a
# prebuilt only when the tarball lacks one — it never does; koffi vendors every triplet).
# Verified by diffing an npm-with-scripts tree against a bun-scripts-blocked tree on the same
# arch: all three packages came out byte-identical. Running them would also be impossible here,
# since every one of them shells out to `node`.
RUN mkdir -p /opt/pi-sdk && cd /opt/pi-sdk && \
    printf '{"name":"hephaestus-pi-sdk","private":true,"dependencies":{"@earendil-works/pi-coding-agent":"%s"}}\n' \
        "${PI_VERSION}" > package.json && \
    BUN_INSTALL_CACHE_DIR=/tmp/bun-cache bun install --production --no-progress && \
    rm -rf /tmp/bun-cache && \
    test -d /opt/pi-sdk/node_modules/@earendil-works/pi-coding-agent \
      || (echo "Pi SDK not found at /opt/pi-sdk/node_modules" >&2; exit 1) && \
    arch="${TARGETARCH:-$(dpkg --print-architecture)}" && \
    case "${arch}" in \
        amd64|x86_64) koffi_triplet="linux_x64" ;; \
        arm64|aarch64) koffi_triplet="linux_arm64" ;; \
        *) echo "Unsupported TARGETARCH/architecture: ${arch}" >&2; exit 1 ;; \
    esac && \
    test -f "/opt/pi-sdk/node_modules/koffi/build/koffi/${koffi_triplet}/koffi.node" \
      || (echo "koffi prebuilt missing for ${koffi_triplet}: its blocked install script mattered after all" >&2; exit 1) && \
    chown -R 1000:1000 /opt/pi-sdk && \
    mkdir -p /workspace && chown 1000:1000 /workspace

# Prove the runtime contract at build time instead of discovering it in a sandbox: resolve the
# SDK by bare specifier through a workspace-style symlink, exactly as PiRuntimeFactory does.
RUN mkdir -p /tmp/abi-check && ln -sf /opt/pi-sdk/node_modules /tmp/abi-check/node_modules && \
    cd /tmp/abi-check && \
    printf 'const sdk = await import("@earendil-works/pi-coding-agent");\nif (!sdk || typeof sdk !== "object") { throw new Error("Pi SDK import yielded no module namespace"); }\nconsole.log("pi sdk exports:", Object.keys(sdk).length);\n' > check.ts && \
    bun check.ts && \
    rm -rf /tmp/abi-check

# Precompute runner + shared libraries (practice scripts injected at runtime from DB)
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

# This image is Bun-only. Runs last so it also catches a Node
# smuggled in by any layer above. Checks $PATH resolution *and* the standard bin directories,
# because an unreferenced /usr/local/bin/node would still be an executable Node.
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
