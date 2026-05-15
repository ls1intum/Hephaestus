// pi-mentor-runner.spec.mjs — Node `node:test` smoke suite for the mentor runner.
//
// Runs the runner as a child process in PROTOCOL_ONLY mode (stub Pi SDK) so we can exercise:
//   1. U+2028/U+2029 framing — the single highest-bang-for-buck test per the audit
//   2. Hello handshake roundtrip
//   3. Concurrent prompt rejection (-32001 turn_already_in_flight)
//
// Wired into CI via `.github/workflows/ci-quality-gates.yml` (application-server-quality
// step). Non-zero exit fails the gate, so a regression to framing or concurrent-prompt
// rejection logic blocks merge. Run locally with:
//   node --test server/application-server/src/test/resources/agent/pi-mentor-runner.spec.mjs

import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import test from "node:test";
import assert from "node:assert/strict";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const RUNNER = path.resolve(__dirname, "../../../main/resources/agent/pi-mentor-runner.mjs");

// Production runner targets /workspace/.sessions, which is unwritable in CI / local node test
// runs. Spawn each runner with an isolated tmpdir to keep the smoke tests hermetic.
const SESSIONS_TMPDIR = mkdtempSync(path.join(tmpdir(), "pi-mentor-runner-spec-"));
process.on("exit", () => {
    try {
        rmSync(SESSIONS_TMPDIR, { recursive: true, force: true });
    } catch {
        /* best-effort tmpdir cleanup; safe to ignore on shutdown */
    }
});

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
        env: {
            ...process.env,
            MENTOR_RUNNER_PROTOCOL_ONLY: "1",
            MENTOR_RUNNER_STUB_DELAY_MS: "5",
            MENTOR_RUNNER_SESSIONS_DIR: SESSIONS_TMPDIR,
        },
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
        env: {
            ...process.env,
            MENTOR_RUNNER_PROTOCOL_ONLY: "1",
            MENTOR_RUNNER_STUB_DELAY_MS: "150",
            MENTOR_RUNNER_SESSIONS_DIR: SESSIONS_TMPDIR,
        },
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

test("batch JSON-RPC request is rejected with -32600 (not silently dropped)", async () => {
    // JSON-RPC 2.0 §6 permits top-level arrays as batches. Neither end emits batches today;
    // the runner rejects them loudly so a future Java caller that bundles requests doesn't
    // see its frames vanish into the runner's log.
    const child = spawn(process.execPath, [RUNNER], {
        env: {
            ...process.env,
            MENTOR_RUNNER_PROTOCOL_ONLY: "1",
            MENTOR_RUNNER_SESSIONS_DIR: SESSIONS_TMPDIR,
        },
        stdio: ["pipe", "pipe", "pipe"],
    });
    const reader = createReader();
    child.stdout.on("data", (chunk) => reader.push(chunk));
    child.stderr.on("data", (chunk) => process.stderr.write(`[runner-stderr] ${chunk}`));
    try {
        await readReady(reader);

        // Send a batch (top-level array) with two requests.
        const batch = [
            { jsonrpc: "2.0", id: "b1", method: "hello", params: {} },
            { jsonrpc: "2.0", id: "b2", method: "hello", params: {} },
        ];
        child.stdin.write(JSON.stringify(batch) + "\n");

        // Expect a single error frame with id:null and code -32600.
        const resp = await readUntil(reader, (f) => f?.error?.code === -32600);
        assert.equal(resp.id, null, "batch rejection error must carry id:null per JSON-RPC §6");
    } finally {
        child.stdin.write(JSON.stringify({ jsonrpc: "2.0", id: "shut", method: "shutdown", params: {} }) + "\n");
        await new Promise((r) => child.on("exit", r));
    }
});

test("watchdog cross-thread rebind: no event leakage from concurrently-bound thread", async () => {
    // Scenario: thread A is mid-prompt (watchdog armed) when ANOTHER thread B opens, which
    // — via the regular bindThread teardown — flips activeThreadId from A to B and replaces
    // A's subscription with B's. When A's watchdog later fires (a few ms after), runtime is
    // bound to B but the rebind needs to install a fresh A subscription AND remove B's
    // (otherwise the next event broadcast hits both subscribers and emits threadId=B frames
    // through forwardEvent(state_B, …)).
    //
    // The stub's switchSession is a no-op and shares one subscribers Set across sessions, so
    // a missing cross-thread teardown leaves B_callback in the broadcast list. We force the
    // window by:
    //   1. open A — A is active, no watchdog yet
    //   2. prompt A with a 200 ms slow stub → arms A's watchdog (80 ms budget+grace)
    //   3. open B — bindThread B fires inside the watchdog window, BEFORE A's watchdog ticks.
    //      Now activeThreadId=B, A_callback gone, B_callback added. A's ThreadState still
    //      lives in the `threads` Map; its watchdog timer is still armed.
    //   4. A's watchdog fires at ~80 ms (post-open-B). With the fix, the rebind sees
    //      activeThreadId=B ≠ state.threadId=A, tears down B_callback, switches session,
    //      adds newA_callback, sets activeThreadId=A. Without the fix, B_callback stays and
    //      activeThreadId remains stale at B.
    //   5. The first prompt's residue (text_delta at ~100 ms, natural agent_end at ~200 ms)
    //      broadcasts through whatever subscribers remain. With the fix only newA_callback
    //      receives them → threadId=A only. Without the fix, B_callback also fires →
    //      threadId=B leak.
    //
    // The legitimate pre-rebind events (the initial agent_start at t=0 fires only through
    // A_callback because B isn't open yet) are NOT a leak; we demarcate the "leak window"
    // as everything AFTER the turn_watchdog_fired event.
    const child = spawn(process.execPath, [RUNNER], {
        env: {
            ...process.env,
            MENTOR_RUNNER_PROTOCOL_ONLY: "1",
            MENTOR_RUNNER_STUB_DELAY_MS: "100", // 100+100 = 200 ms total stub turn
            MENTOR_TURN_BUDGET_MS: "50",
            MENTOR_TURN_GRACE_MS: "30",
            MENTOR_RUNNER_SESSIONS_DIR: SESSIONS_TMPDIR,
        },
        stdio: ["pipe", "pipe", "pipe"],
    });
    const reader = createReader();
    child.stdout.on("data", (chunk) => reader.push(chunk));
    child.stderr.on("data", (chunk) => process.stderr.write(`[runner-stderr] ${chunk}`));
    const send = (obj) => child.stdin.write(JSON.stringify(obj) + "\n");
    try {
        await readReady(reader);

        // 1. open A — activeThreadId becomes A; subscribers = {A_callback}
        send({ jsonrpc: "2.0", id: "oA", method: "open_thread", params: { threadId: "thread-A" } });
        await readUntil(reader, (f) => f?.id === "oA");

        // 2. prompt A — arms watchdog A (80 ms). Stub broadcasts agent_start NOW; A_callback
        //    is the only subscriber so we observe threadId=A only (legitimate).
        send({ jsonrpc: "2.0", id: "pA1", method: "prompt", params: { threadId: "thread-A", text: "go" } });
        const ack = await readUntil(reader, (f) => f?.id === "pA1");
        assert.equal(ack.result?.accepted, true);

        // 3. open B — bindThread B tears down A_callback, adds B_callback, activeThreadId=B.
        //    Must arrive BEFORE the watchdog ticks (80 ms after pA1 was accepted). The stub
        //    is fast — open_thread is a sync handler, so this races in well under 80 ms.
        send({ jsonrpc: "2.0", id: "oB", method: "open_thread", params: { threadId: "thread-B" } });
        await readUntil(reader, (f) => f?.id === "oB");

        // 4 + 5. Collect every event frame until the stub's residue setTimeout chain drains,
        // classified by whether it arrived before or after the turn_watchdog_fired marker.
        const events = [];
        let watchdogSeenAtIndex = -1;
        const start = Date.now();
        while (Date.now() - start < 2000) {
            const frame = await reader.next(500).catch(() => null);
            if (!frame) break;
            const parsed = JSON.parse(frame);
            if (parsed?.method !== "event") continue;
            events.push(parsed);
            if (parsed?.params?.event?.type === "turn_watchdog_fired") {
                watchdogSeenAtIndex = events.length - 1;
            }
            // Stop ≈400 ms after the watchdog fired so the residue text_delta + natural
            // agent_end from the first prompt's setTimeout chain have time to reach us
            // (stub is 200 ms total; watchdog fires at 80 ms → residue at 100 ms, 200 ms).
            if (watchdogSeenAtIndex >= 0 && Date.now() - start > 600) break;
        }

        assert.ok(
            watchdogSeenAtIndex >= 0,
            "expected turn_watchdog_fired event during the watchdog window; got events: " +
                JSON.stringify(events.map((f) => ({ tid: f?.params?.threadId, t: f?.params?.event?.type })))
        );

        // The watchdog emits three events for thread A in close sequence:
        //   1. turn_watchdog_fired (direct sendEvent, before abort)
        //   2. abort-broadcast agent_end (fires through whatever subscriber is active at that
        //      instant — pre-rebind that's B_callback, so ONE legitimate threadId=B agent_end
        //      arrives here. Not a leak: the fix's order-of-operations puts the teardown of
        //      B's subscription AFTER the abort, by design.)
        //   3. direct sendEvent agent_end (post-rebind, marks "subscribers Set is now in the
        //      fixed state — only newA_callback".)
        //
        // The leak signal is anything broadcast AFTER step 3: the stub's residue setTimeout
        // chain (text_delta, natural agent_end) fires through whatever subscribers remain.
        // WITH the fix → only newA_callback → no thread-B events. WITHOUT → also B_callback →
        // duplicate thread-B events.
        //
        // We demarcate "post-rebind" as the FIRST threadId=A agent_end after the watchdog
        // marker (that is the direct sendEvent in step 3, since the abort-broadcast above
        // tags threadId=B — A_callback was already removed by bindThread B's teardown).
        const directRebindIdx = events.findIndex(
            (f, i) =>
                i > watchdogSeenAtIndex &&
                f?.params?.threadId === "thread-A" &&
                f?.params?.event?.type === "agent_end"
        );
        assert.ok(
            directRebindIdx > watchdogSeenAtIndex,
            "expected watchdog's direct agent_end (threadId=A) after turn_watchdog_fired; got events: " +
                JSON.stringify(events.map((f) => ({ tid: f?.params?.threadId, t: f?.params?.event?.type })))
        );
        const postRebind = events.slice(directRebindIdx + 1);
        const leakedB = postRebind.filter((f) => f?.params?.threadId === "thread-B");
        assert.deepEqual(
            leakedB.map((f) => f?.params?.event?.type),
            [],
            "thread B's subscription must be torn down by the watchdog rebind — any threadId=B " +
                "event AFTER the watchdog's direct agent_end indicates a leaked subscription " +
                "(the first prompt's residue setTimeout chain broadcast through B_callback). " +
                "post-rebind frames seen: " +
                JSON.stringify(postRebind.map((f) => ({ tid: f?.params?.threadId, t: f?.params?.event?.type })))
        );
    } finally {
        send({ jsonrpc: "2.0", id: "shut", method: "shutdown", params: {} });
        await new Promise((r) => child.on("exit", r));
    }
});

// Note: fetch_context end-to-end coverage lives Java-side in MentorRunnerClientTest +
// MentorChatServiceTest. A Node-side smoke test would either assert on stderr text (brittle)
// or require a real Pi LLM round-trip. Skipped here on purpose.
