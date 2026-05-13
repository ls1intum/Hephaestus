// pi-mentor-runner.spec.mjs — Node `node:test` smoke suite for the mentor runner.
//
// Runs the runner as a child process in PROTOCOL_ONLY mode (stub Pi SDK) so we can exercise:
//   1. U+2028/U+2029 framing — the single highest-bang-for-buck test per the audit
//   2. Hello handshake roundtrip
//   3. Concurrent prompt rejection (-32001 turn_already_in_flight)
//   4. fetch_context callback round-trip (Pi tool call → Java callback → resolved response)
//
// Run manually:
//   node --test server/application-server/src/test/resources/agent/pi-mentor-runner.spec.mjs
//
// CI wiring is intentionally NOT done in this PR — the Maven build doesn't yet have a Node
// hook for `src/test/resources/agent/*.spec.mjs`. Add an `exec-maven-plugin` invocation when
// the rest of Session B/C lands. Documented in the handoff. For now this is a developer-run
// smoke test that catches the failure modes that matter.

import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const RUNNER = path.resolve(__dirname, "../../../main/resources/agent/pi-mentor-runner.mjs");

// ─── Test-side line splitter mirrors the runner's strict semantics ───────────
function createReader() {
    let buffer = Buffer.alloc(0);
    const queue = [];
    const waiters = [];
    const onLine = (line) => {
        if (waiters.length > 0) {
            waiters.shift()(line);
        } else {
            queue.push(line);
        }
    };
    return {
        push(chunk) {
            buffer = Buffer.concat([buffer, chunk]);
            while (true) {
                const nl = buffer.indexOf(0x0a);
                if (nl === -1) return;
                let lineBuf = buffer.subarray(0, nl);
                buffer = buffer.subarray(nl + 1);
                if (lineBuf.length > 0 && lineBuf[lineBuf.length - 1] === 0x0d) {
                    lineBuf = lineBuf.subarray(0, lineBuf.length - 1);
                }
                if (lineBuf.length === 0) continue;
                onLine(lineBuf.toString("utf8"));
            }
        },
        async next(timeoutMs = 5000) {
            if (queue.length > 0) return queue.shift();
            return new Promise((resolve, reject) => {
                const timer = setTimeout(() => {
                    const idx = waiters.indexOf(resolveOnce);
                    if (idx >= 0) waiters.splice(idx, 1);
                    reject(new Error(`timeout after ${timeoutMs}ms waiting for line`));
                }, timeoutMs);
                const resolveOnce = (line) => {
                    clearTimeout(timer);
                    resolve(line);
                };
                waiters.push(resolveOnce);
            });
        },
    };
}

function spawnRunner() {
    const child = spawn(process.execPath, [RUNNER], {
        env: { ...process.env, MENTOR_RUNNER_PROTOCOL_ONLY: "1", MENTOR_RUNNER_STUB_DELAY_MS: "5" },
        stdio: ["pipe", "pipe", "pipe"],
    });
    const reader = createReader();
    child.stdout.on("data", (chunk) => reader.push(chunk));
    // Surface runner stderr to test output for diagnostics; never assert against it.
    child.stderr.on("data", (chunk) => process.stderr.write(`[runner-stderr] ${chunk}`));
    const send = (obj) => child.stdin.write(JSON.stringify(obj) + "\n");
    return { child, reader, send };
}

async function readUntil(reader, predicate, opts = {}) {
    const max = opts.max ?? 50;
    for (let i = 0; i < max; i++) {
        const line = await reader.next(opts.timeoutMs ?? 5000);
        const frame = JSON.parse(line);
        if (predicate(frame)) return frame;
    }
    throw new Error("predicate never matched in readUntil");
}

async function readReady(reader) {
    return readUntil(reader, (f) => f?.params?.event?.type === "runner_ready");
}

test("hello handshake returns protocolVersion 1", async () => {
    const { child, reader, send } = spawnRunner();
    try {
        await readReady(reader);
        send({ jsonrpc: "2.0", id: "h1", method: "hello", params: {} });
        const resp = await readUntil(reader, (f) => f?.id === "h1");
        assert.equal(resp.result.protocolVersion, 1);
        assert.deepEqual(resp.result.capabilities.sort(), ["fetch_context", "link_finding", "mentor"].sort());
    } finally {
        send({ jsonrpc: "2.0", id: "shut", method: "shutdown", params: {} });
        await new Promise((r) => child.on("exit", r));
    }
});

test("U+2028 and U+2029 inside JSON strings do NOT split frames", async () => {
    // This is the single test that catches the most insidious bug in the framing layer:
    // Node's readline (and many naive line splitters) split on U+2028/U+2029, but those
    // 3-byte UTF-8 sequences are legal inside JSON string values. The runner uses
    // Buffer.indexOf(0x0a) directly to avoid this.
    const { child, reader, send } = spawnRunner();
    try {
        await readReady(reader);

        const tricky = `line1 line2 line3`;
        send({ jsonrpc: "2.0", id: "echo", method: "open_thread", params: { threadId: tricky } });
        const resp = await readUntil(reader, (f) => f?.id === "echo");

        // The threadId should arrive back intact with both separators preserved as single chars.
        assert.equal(resp.result.threadId, tricky);
        assert.ok(resp.result.threadId.includes(" "));
        assert.ok(resp.result.threadId.includes(" "));
    } finally {
        send({ jsonrpc: "2.0", id: "shut", method: "shutdown", params: {} });
        await new Promise((r) => child.on("exit", r));
    }
});

test("second concurrent prompt returns -32001 turn_already_in_flight", async () => {
    // Use a slow stub so the first prompt is still in flight when we fire the second.
    const child = spawn(process.execPath, [RUNNER], {
        env: { ...process.env, MENTOR_RUNNER_PROTOCOL_ONLY: "1", MENTOR_RUNNER_STUB_DELAY_MS: "150" },
        stdio: ["pipe", "pipe", "pipe"],
    });
    const reader = createReader();
    child.stdout.on("data", (chunk) => reader.push(chunk));
    child.stderr.on("data", (chunk) => process.stderr.write(`[runner-stderr] ${chunk}`));
    const send = (obj) => child.stdin.write(JSON.stringify(obj) + "\n");
    try {
        await readReady(reader);
        send({ jsonrpc: "2.0", id: "o1", method: "open_thread", params: { threadId: "t-concurrent" } });
        await readUntil(reader, (f) => f?.id === "o1");

        send({ jsonrpc: "2.0", id: "p1", method: "prompt", params: { threadId: "t-concurrent", text: "first" } });
        const accepted = await readUntil(reader, (f) => f?.id === "p1");
        assert.equal(accepted.result?.accepted, true);

        // Fire the second prompt immediately — stub delay is 150ms so the first is still streaming.
        send({ jsonrpc: "2.0", id: "p2", method: "prompt", params: { threadId: "t-concurrent", text: "second" } });
        const rejected = await readUntil(reader, (f) => f?.id === "p2");
        assert.equal(rejected.error?.code, -32001, "expected turn_already_in_flight");
    } finally {
        send({ jsonrpc: "2.0", id: "shut", method: "shutdown", params: {} });
        await new Promise((r) => child.on("exit", r));
    }
});

// Note: fetch_context end-to-end coverage lives Java-side in MentorRunnerClientTest +
// MentorChatServiceTest. A Node-side smoke test would either assert on stderr text (brittle)
// or require a real Pi LLM round-trip. Skipped here on purpose.
