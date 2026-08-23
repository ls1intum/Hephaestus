// pi-mentor-runner.spec.ts — smoke suite for the mentor runner.
//
// Runs the runner as a child process in PROTOCOL_ONLY mode (stub Pi SDK) so we can exercise:
//   1. U+2028/U+2029 framing — the single highest-bang-for-buck test per the audit
//   2. Hello handshake roundtrip
//   3. Concurrent prompt rejection (-32001 turn_already_in_flight)
//
// Frames are typed against pi-mentor-protocol.ts, the same declarations the runner emits against,
// so a change to the wire contract that this suite does not follow is a type error rather than an
// assertion that quietly stops matching anything.
//
// Wired into CI via `.github/workflows/ci-quality-gates.yml` (application-server-quality
// step). Non-zero exit fails the gate, so a regression to framing or concurrent-prompt
// rejection logic blocks merge. Run locally with:
//   pnpm run test:agents

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import {
	JSONRPC_VERSION,
	type JsonRpcErrorResponse,
	type JsonRpcSuccessResponse,
	type MentorEventNotification,
	type MentorOutboundFrame,
	type MentorRequest,
} from "../../../main/resources/agent/pi-mentor-protocol.ts";

const RUNNER = path.resolve(
	import.meta.dirname,
	"../../../main/resources/agent/pi-mentor-runner.ts",
);

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

/** The envelope every frame the runner writes shares; the helpers below narrow it further. */
function isOutboundFrame(value: unknown): value is MentorOutboundFrame {
	return (
		typeof value === "object" &&
		value !== null &&
		"jsonrpc" in value &&
		value.jsonrpc === JSONRPC_VERSION
	);
}

/**
 * The runner writes JSON; this is the one place bytes become values. Everything downstream reads
 * the frame through `MentorOutboundFrame`, so the assertions below are checked against the same
 * declarations the runner produces — and a line that is not a frame at all fails here, by that name,
 * rather than as a missing field several assertions later.
 */
function parseFrame(line: string): MentorOutboundFrame {
	const frame: unknown = JSON.parse(line);
	if (!isOutboundFrame(frame)) {
		throw new Error(`not a JSON-RPC 2.0 frame: ${line.slice(0, 200)}`);
	}
	return frame;
}

function isEventNotification(frame: MentorOutboundFrame): frame is MentorEventNotification {
	return "method" in frame && frame.method === "event";
}

function isSuccess(frame: MentorOutboundFrame): frame is JsonRpcSuccessResponse {
	return "result" in frame;
}

function isFailure(frame: MentorOutboundFrame): frame is JsonRpcErrorResponse {
	return "error" in frame;
}

/** `params.event.type` for an event frame, or undefined for anything else. */
function eventType(frame: MentorOutboundFrame): string | undefined {
	return isEventNotification(frame) ? frame.params.event.type : undefined;
}

/** `params.threadId` for an event frame, or undefined for anything else. */
function eventThreadId(frame: MentorOutboundFrame): string | null | undefined {
	return isEventNotification(frame) ? frame.params.threadId : undefined;
}

function frameId(frame: MentorOutboundFrame): string | number | null | undefined {
	return "id" in frame ? frame.id : undefined;
}

interface Reader {
	push: (chunk: Buffer) => void;
	next: (timeoutMs?: number) => Promise<string>;
}

// ─── Test-side line splitter mirrors the runner's strict semantics ───────────
function createReader(): Reader {
	let buffer: Buffer = Buffer.alloc(0);
	const queue: string[] = [];
	const waiters: Array<(line: string) => void> = [];
	const onLine = (line: string) => {
		const waiter = waiters.shift();
		if (waiter) {
			waiter(line);
		} else {
			queue.push(line);
		}
	};
	return {
		push(chunk: Buffer) {
			buffer = Buffer.concat([buffer, chunk]);
			for (;;) {
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
		async next(timeoutMs = 5000): Promise<string> {
			const queued = queue.shift();
			if (queued !== undefined) return queued;
			return new Promise<string>((resolve, reject) => {
				const timer = setTimeout(() => {
					const idx = waiters.indexOf(resolveOnce);
					if (idx >= 0) waiters.splice(idx, 1);
					reject(new Error(`timeout after ${timeoutMs}ms waiting for line`));
				}, timeoutMs);
				const resolveOnce = (line: string) => {
					clearTimeout(timer);
					resolve(line);
				};
				waiters.push(resolveOnce);
			});
		},
	};
}

interface RunnerHandle {
	child: ReturnType<typeof spawn>;
	reader: Reader;
	send: (request: MentorRequest) => void;
}

/**
 * `env` overrides are merged over the process env, so a caller only names what it changes.
 * MENTOR_RUNNER_SESSIONS_DIR always points at the hermetic tmpdir.
 */
function spawnRunner(env: Record<string, string> = {}): RunnerHandle {
	const child = spawn(process.execPath, [RUNNER], {
		env: {
			...process.env,
			MENTOR_RUNNER_PROTOCOL_ONLY: "1",
			MENTOR_RUNNER_STUB_DELAY_MS: "5",
			MENTOR_RUNNER_SESSIONS_DIR: SESSIONS_TMPDIR,
			...env,
		},
		stdio: ["pipe", "pipe", "pipe"],
	});
	const reader = createReader();
	child.stdout.on("data", (chunk: Buffer) => reader.push(chunk));
	// Surface runner stderr to test output for diagnostics; never assert against it.
	child.stderr.on("data", (chunk: Buffer) =>
		process.stderr.write(`[runner-stderr] ${chunk.toString("utf8")}`),
	);
	const send = (request: MentorRequest) => {
		child.stdin.write(`${JSON.stringify(request)}\n`);
	};
	return { child, reader, send };
}

/** Send `shutdown` and wait for the child to exit — the teardown every test shares. */
async function shutdown({ child, send }: RunnerHandle): Promise<void> {
	send({ jsonrpc: "2.0", id: "shut", method: "shutdown", params: {} });
	await new Promise<void>((resolve) => {
		child.on("exit", () => resolve());
	});
}

async function readUntil(
	reader: Reader,
	predicate: (frame: MentorOutboundFrame) => boolean,
	opts: { max?: number; timeoutMs?: number } = {},
): Promise<MentorOutboundFrame> {
	const max = opts.max ?? 50;
	for (let i = 0; i < max; i++) {
		const frame = parseFrame(await reader.next(opts.timeoutMs ?? 5000));
		if (predicate(frame)) return frame;
	}
	throw new Error("predicate never matched in readUntil");
}

async function readReady(reader: Reader): Promise<MentorOutboundFrame> {
	return readUntil(reader, (f) => eventType(f) === "runner_ready");
}

/**
 * Read the response to `id` and hand back its `result`.
 *
 * A wrong-shaped response throws rather than asserting: the shape is a precondition for the
 * assertions each test then makes, not a claim the test is here to check.
 */
async function readResult(reader: Reader, id: string): Promise<JsonRpcSuccessResponse["result"]> {
	const frame = await readUntil(reader, (f) => frameId(f) === id);
	if (!isSuccess(frame)) {
		throw new Error(`expected a result for id "${id}", got ${JSON.stringify(frame)}`);
	}
	return frame.result;
}

/** Read the response to `id` and hand back its `error`. See `readResult` on why this throws. */
async function readError(reader: Reader, id: string): Promise<JsonRpcErrorResponse["error"]> {
	const frame = await readUntil(reader, (f) => frameId(f) === id);
	if (!isFailure(frame)) {
		throw new Error(`expected an error for id "${id}", got ${JSON.stringify(frame)}`);
	}
	return frame.error;
}

// `void`: node:test's own runner owns the promise each test hands back, and awaiting one here
// would register the next test only after the previous had finished.
void test("hello handshake returns protocolVersion 1", async () => {
	const runner = spawnRunner();
	try {
		await readReady(runner.reader);
		runner.send({ jsonrpc: "2.0", id: "h1", method: "hello", params: {} });
		const result = await readResult(runner.reader, "h1");
		assert.ok("protocolVersion" in result, "hello must answer with a protocolVersion");
		assert.equal(result.protocolVersion, 1);
	} finally {
		await shutdown(runner);
	}
});

void test("U+2028 and U+2029 inside JSON strings do NOT split frames", async () => {
	// The most insidious bug in the framing layer: many naive line splitters split on
	// U+2028/U+2029, but JSON.stringify leaves those 3-byte UTF-8 sequences unescaped inside
	// string values. The runner uses Buffer.indexOf(0x0a) directly to avoid this. Drive a real
	// prompt frame whose text carries the chars; if the splitter mis-handled the bytes, the
	// runner would see two malformed halves and we would never match the accept ack.
	const runner = spawnRunner();
	try {
		await readReady(runner.reader);
		const tid = "11111111-2222-3333-4444-555555555555";
		runner.send({ jsonrpc: "2.0", id: "o", method: "open_thread", params: { threadId: tid } });
		await readResult(runner.reader, "o");
		const tricky = `line1 line2 line3`;
		runner.send({
			jsonrpc: "2.0",
			id: "p",
			method: "prompt",
			params: { threadId: tid, text: tricky },
		});
		const result = await readResult(runner.reader, "p");
		assert.ok("accepted" in result, "prompt must answer with an accept ack");
		assert.equal(result.accepted, true);
	} finally {
		await shutdown(runner);
	}
});

void test("path-traversal threadId rejected with -32600", async () => {
	// The runner is a security boundary: even though Java only ever passes UUIDs, a future
	// bridge or debug shell that bypasses Java must not be able to coax path.join into
	// resolving outside SESSIONS_DIR. Reject anything that is not a canonical UUID.
	const runner = spawnRunner();
	try {
		await readReady(runner.reader);
		const cases = ["../../etc/passwd", "/etc/passwd", "..", "abc"];
		for (const [i, evil] of cases.entries()) {
			const reqId = `t-${i}`;
			runner.send({ jsonrpc: "2.0", id: reqId, method: "open_thread", params: { threadId: evil } });
			const error = await readError(runner.reader, reqId);
			assert.equal(error.code, -32600, `expected -32600 for "${evil}"`);
		}
	} finally {
		await shutdown(runner);
	}
});

void test("second concurrent prompt returns -32001 turn_already_in_flight", async () => {
	// Use a slow stub so the first prompt is still in flight when we fire the second.
	const runner = spawnRunner({ MENTOR_RUNNER_STUB_DELAY_MS: "150" });
	const threadId = "22222222-2222-2222-2222-222222222222";
	try {
		await readReady(runner.reader);
		runner.send({ jsonrpc: "2.0", id: "o1", method: "open_thread", params: { threadId } });
		await readResult(runner.reader, "o1");

		runner.send({
			jsonrpc: "2.0",
			id: "p1",
			method: "prompt",
			params: { threadId, text: "first" },
		});
		const accepted = await readResult(runner.reader, "p1");
		assert.ok("accepted" in accepted, "first prompt must be accepted");
		assert.equal(accepted.accepted, true);

		// Fire the second prompt immediately — stub delay is 150ms so the first is still streaming.
		runner.send({
			jsonrpc: "2.0",
			id: "p2",
			method: "prompt",
			params: { threadId, text: "second" },
		});
		const rejected = await readError(runner.reader, "p2");
		assert.equal(rejected.code, -32001, "expected turn_already_in_flight");
	} finally {
		await shutdown(runner);
	}
});

void test("batch JSON-RPC request is rejected with -32600 (not silently dropped)", async () => {
	// JSON-RPC 2.0 §6 permits top-level arrays as batches. Neither end emits batches today;
	// the runner rejects them loudly so a future Java caller that bundles requests doesn't
	// see its frames vanish into the runner's log.
	const runner = spawnRunner();
	try {
		await readReady(runner.reader);

		// Send a batch (top-level array) with two requests. `send` takes a single request by
		// design, so this one frame goes out through stdin directly.
		const batch: MentorRequest[] = [
			{ jsonrpc: "2.0", id: "b1", method: "hello", params: {} },
			{ jsonrpc: "2.0", id: "b2", method: "hello", params: {} },
		];
		runner.child.stdin?.write(`${JSON.stringify(batch)}\n`);

		// Expect a single error frame with id:null and code -32600.
		const frame = await readUntil(runner.reader, (f) => isFailure(f) && f.error.code === -32600);
		assert.equal(frameId(frame), null, "batch rejection error must carry id:null per JSON-RPC §6");
	} finally {
		await shutdown(runner);
	}
});

void test("watchdog cross-thread rebind: no event leakage from concurrently-bound thread", async () => {
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
	const threadA = "33333333-3333-3333-3333-333333333333";
	const threadB = "44444444-4444-4444-4444-444444444444";
	const runner = spawnRunner({
		MENTOR_RUNNER_STUB_DELAY_MS: "100", // 100+100 = 200 ms total stub turn
		MENTOR_TURN_BUDGET_MS: "50",
		MENTOR_TURN_GRACE_MS: "30",
	});
	try {
		await readReady(runner.reader);

		// 1. open A — activeThreadId becomes A; subscribers = {A_callback}
		runner.send({ jsonrpc: "2.0", id: "oA", method: "open_thread", params: { threadId: threadA } });
		await readResult(runner.reader, "oA");

		// 2. prompt A — arms watchdog A (80 ms). Stub broadcasts agent_start NOW; A_callback
		//    is the only subscriber so we observe threadId=A only (legitimate).
		runner.send({
			jsonrpc: "2.0",
			id: "pA1",
			method: "prompt",
			params: { threadId: threadA, text: "go" },
		});
		const ack = await readResult(runner.reader, "pA1");
		assert.ok("accepted" in ack, "prompt A must be accepted");
		assert.equal(ack.accepted, true);

		// 3. open B — bindThread B tears down A_callback, adds B_callback, activeThreadId=B.
		//    Must arrive BEFORE the watchdog ticks (80 ms after pA1 was accepted). The stub
		//    is fast — open_thread is a sync handler, so this races in well under 80 ms.
		runner.send({ jsonrpc: "2.0", id: "oB", method: "open_thread", params: { threadId: threadB } });
		await readResult(runner.reader, "oB");

		// 4 + 5. Collect every event frame until the stub's residue setTimeout chain drains,
		// classified by whether it arrived before or after the turn_watchdog_fired marker.
		const events: MentorEventNotification[] = [];
		let watchdogSeenAtIndex = -1;
		const start = Date.now();
		while (Date.now() - start < 2000) {
			const line = await runner.reader.next(500).catch(() => null);
			if (line === null) break;
			const parsed = parseFrame(line);
			if (!isEventNotification(parsed)) continue;
			events.push(parsed);
			if (eventType(parsed) === "turn_watchdog_fired") {
				watchdogSeenAtIndex = events.length - 1;
			}
			// Stop ≈400 ms after the watchdog fired so the residue text_delta + natural
			// agent_end from the first prompt's setTimeout chain have time to reach us
			// (stub is 200 ms total; watchdog fires at 80 ms → residue at 100 ms, 200 ms).
			if (watchdogSeenAtIndex >= 0 && Date.now() - start > 600) break;
		}

		const summarise = (frames: MentorEventNotification[]) =>
			JSON.stringify(frames.map((f) => ({ tid: eventThreadId(f), t: eventType(f) })));

		assert.ok(
			watchdogSeenAtIndex >= 0,
			`expected turn_watchdog_fired event during the watchdog window; got events: ${summarise(events)}`,
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
				i > watchdogSeenAtIndex && eventThreadId(f) === threadA && eventType(f) === "agent_end",
		);
		assert.ok(
			directRebindIdx > watchdogSeenAtIndex,
			"expected watchdog's direct agent_end (threadId=A) after turn_watchdog_fired; " +
				`got events: ${summarise(events)}`,
		);
		const postRebind = events.slice(directRebindIdx + 1);
		const leakedB = postRebind.filter((f) => eventThreadId(f) === threadB);
		assert.deepEqual(
			leakedB.map((f) => eventType(f)),
			[],
			"thread B's subscription must be torn down by the watchdog rebind — any threadId=B " +
				"event AFTER the watchdog's direct agent_end indicates a leaked subscription " +
				"(the first prompt's residue setTimeout chain broadcast through B_callback). " +
				`post-rebind frames seen: ${summarise(postRebind)}`,
		);
	} finally {
		await shutdown(runner);
	}
});

// Note: fetch_context end-to-end coverage lives Java-side in MentorRunnerClientTest +
// MentorChatServiceTest. A Node-side smoke test would either assert on stderr text (brittle)
// or require a real Pi LLM round-trip.
