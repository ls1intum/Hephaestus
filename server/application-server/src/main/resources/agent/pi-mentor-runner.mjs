// pi-mentor-runner.mjs — interactive Pi-mentor runner. Long-lived JSON-RPC 2.0 over stdin/stdout
// (one object per line, terminator strictly `\n`).
//
// Protocol (Java ↔ runner):
//   stdin  (Java→runner): requests `{jsonrpc, id, method, params}`
//   stdout (runner→Java): responses + notifications (`method:"event"`) + runner→Java callbacks
//                         (`fetch_context`).
// Methods: hello, open_thread, replay_context, prompt, steer, abort, close_thread, shutdown.
// Custom tools: fetch_context (callback to Java, whitelisted paths), link_finding (event emit).
// Error codes: -32600 invalid_request, -32601 method_not_found, -32000 thread_not_open,
//              -32001 turn_already_in_flight, -32002 pi_error, -32003 invalid_state.

import { existsSync, mkdirSync, readFileSync } from "node:fs";
import { randomUUID } from "node:crypto";
import path from "node:path";

// Pi SDK is loaded lazily so the protocol layer (framing, JSON-RPC dispatch, fetch_context
// callback plumbing) can be exercised in test environments without an LLM proxy. Set
// `MENTOR_RUNNER_PROTOCOL_ONLY=1` to swap the SDK for a no-op stub that emits deterministic
// events sufficient for protocol tests. Production builds never set this.
const PROTOCOL_ONLY = process.env.MENTOR_RUNNER_PROTOCOL_ONLY === "1";

// PROTOCOL_ONLY swaps the Pi SDK for a deterministic stub. Catching a config drift that flips
// this on in prod is critical — every prompt would return `stub: <text>` instead of an LLM
// response, with no other surface signal. Log loudly on startup; the runner has no signed
// flag to refuse to run, but ops should see this in the container's first stderr line.
if (PROTOCOL_ONLY) {
    process.stderr.write(
        "[pi-mentor-runner] WARN MENTOR_RUNNER_PROTOCOL_ONLY=1 — Pi SDK disabled, all prompts will be stubbed. " +
            "This must never be set in production. Unset MENTOR_RUNNER_PROTOCOL_ONLY to use the real Pi runtime.\n",
    );
}

let sdk;
async function loadSdk() {
    if (sdk) return sdk;
    if (PROTOCOL_ONLY) {
        sdk = buildStubSdk();
    } else {
        sdk = await import("@earendil-works/pi-coding-agent");
    }
    return sdk;
}

// Production paths are pinned to the container layout (/workspace/...). PROTOCOL_ONLY tests
// and CI smoke-test invocations run the runner as a plain Node process where /workspace is
// unwritable; MENTOR_RUNNER_SESSIONS_DIR / MENTOR_RUNNER_SYSTEM_PROMPT_PATH let callers point
// at a tmpdir without forking the runner code.
const CWD = process.env.MENTOR_RUNNER_CWD ?? "/workspace";
const SESSIONS_DIR = process.env.MENTOR_RUNNER_SESSIONS_DIR ?? "/workspace/.sessions";
const SYSTEM_PROMPT_PATH = process.env.MENTOR_RUNNER_SYSTEM_PROMPT_PATH ?? "/workspace/agent/mentor/system.md";
// The SDK's `getAgentDir()` is the canonical default ("~/.pi/agent"). We resolve it lazily
// inside `ensureRuntime()` so the module loads without the SDK in protocol-only test mode.
const AGENT_DIR_OVERRIDE = process.env.PI_CODING_AGENT_DIR ?? null;
const PROTOCOL_VERSION = 1;

const FETCH_CONTEXT_TIMEOUT_MS = 10_000;
const TURN_BUDGET_MS = (() => {
    const raw = Number(process.env.MENTOR_TURN_BUDGET_MS);
    return Number.isFinite(raw) && raw > 0 ? raw : 120_000;
})();
// 30 s production grace; small overrides are test-only so watchdog rebind scenarios run in ms.
const TURN_GRACE_MS = (() => {
    const raw = Number(process.env.MENTOR_TURN_GRACE_MS);
    return Number.isFinite(raw) && raw > 0 ? raw : 30_000;
})();

// Aspect-name whitelist for the fetch_context tool. Any other path is rejected before the
// callback even leaves the runner. This is a defence-in-depth check; the authoritative
// whitelist lives Java-side in MentorChatService.ALLOWED_FETCH_KEYS (full-key match against
// the aspect providers' OUTPUT_KEY constants). Keep this set aligned with the
// {User,Workspace,PracticeCatalog,FindingsHistory}AspectProvider basenames.
const FETCH_CONTEXT_ALLOWED = new Set([
    "workspace.json",
    "user.json",
    "practice_catalog.json",
    "findings_history.json",
]);

// JSON-RPC error codes
const ERR = Object.freeze({
    INVALID_REQUEST: -32600,
    METHOD_NOT_FOUND: -32601,
    THREAD_NOT_OPEN: -32000,
    TURN_IN_FLIGHT: -32001,
    PI_ERROR: -32002,
    INVALID_STATE: -32003,
});

function log(...args) {
    const ts = new Date().toISOString();
    const msg = args
        .map((a) => (a instanceof Error ? `${a.message}\n${a.stack ?? ""}` : typeof a === "string" ? a : JSON.stringify(a)))
        .join(" ");
    process.stderr.write(`[pi-mentor-runner ${ts}] ${msg}\n`);
}

//
// Buffer.indexOf(0x0a) — NOT readline. The Pi SDK rpc.md docs §28-37 spell out that Node's
// readline mis-splits on U+2028/U+2029, which are 3-byte UTF-8 sequences legal inside JSON
// strings. The Java side (JsonlStdoutPump.java) uses the same strict semantics on its end.
//
// We accept an optional trailing `\r` (CRLF) and strip it, matching the docs' guidance.

function createLineSplitter(onLine) {
    let buffer = Buffer.alloc(0);
    const MAX_LINE_BYTES = 8 * 1024 * 1024; // 8 MiB hard cap; aspects are tiny but be safe
    return (chunk) => {
        buffer = buffer.length === 0 ? chunk : Buffer.concat([buffer, chunk]);
        while (true) {
            const nl = buffer.indexOf(0x0a);
            if (nl === -1) {
                if (buffer.length > MAX_LINE_BYTES) {
                    log(`oversized line dropped at ${buffer.length} bytes`);
                    buffer = Buffer.alloc(0);
                }
                return;
            }
            let lineBuf = buffer.subarray(0, nl);
            buffer = buffer.subarray(nl + 1);
            if (lineBuf.length > 0 && lineBuf[lineBuf.length - 1] === 0x0d) {
                lineBuf = lineBuf.subarray(0, lineBuf.length - 1);
            }
            if (lineBuf.length === 0) continue;
            try {
                onLine(lineBuf.toString("utf8"));
            } catch (e) {
                log("line handler threw:", e);
            }
        }
    };
}

function writeFrame(obj) {
    process.stdout.write(JSON.stringify(obj) + "\n");
}

/**
 * Backpressure threshold: once Node's stdout write queue exceeds this many bytes, the
 * dispatch queue pauses for a `drain` event before running the next task. Pi can emit
 * hundreds of text_delta events per second; if Java's consumer is slow (SSE backed up by a
 * client) the runner's heap balloons unboundedly without this guard, eventually OOM-killing
 * the container mid-turn. 256 KB is roughly one Pi turn's worth of deltas — well below
 * Node's default 16 KB highWaterMark × ~16 chunk burst budget.
 */
const STDOUT_BACKPRESSURE_THRESHOLD_BYTES = 256 * 1024;

function sendResult(id, result) {
    // JSON-RPC 2.0 §4: `id` absent (undefined here) = notification → MUST NOT respond.
    // `id: null` is a valid request id — DO respond (used by §6 batch-rejection paths).
    if (id === undefined) return;
    writeFrame({ jsonrpc: "2.0", id, result: result ?? null });
}

function sendError(id, code, message, data) {
    // Same rule as sendResult: only skip when id is genuinely absent (notification). `null`
    // is a valid id and JSON-RPC §6 explicitly requires it for batch-error / parse-error
    // responses where the server cannot determine which request id was at fault.
    if (id === undefined) return;
    const err = { code, message };
    if (data !== undefined) err.data = data;
    writeFrame({ jsonrpc: "2.0", id, error: err });
}

function sendEvent(threadId, event) {
    writeFrame({ jsonrpc: "2.0", method: "event", params: { threadId, event } });
}

//
// Strategy: hold ONE AgentSessionRuntime. Switch sessions per thread via `runtime.switchSession`
// (re-subscribing on each switch per SDK docs).

const threads = new Map(); // threadId → ThreadState

class ThreadState {
    constructor(threadId, sessionPath) {
        this.threadId = threadId;
        this.sessionPath = sessionPath;
        this.inFlight = false;
        this.watchdogTimer = null;
        this.pendingFetchContexts = new Map(); // callbackId → {resolve, reject, timer}
        this.unsubscribe = null;
    }
}

// Currently-bound thread on the AgentSessionRuntime (since runtime is single-session at a time).
let activeThreadId = null;

let runtime = null; // AgentSessionRuntime
let runtimeInitPromise = null;
let systemPrompt = null; // cached after first read

/**
 * Last runtime-init failure — used to fail-fast for a short window so a permanently-broken
 * SDK doesn't get re-loaded once per inbound frame. The container is throwaway; one short
 * cooldown burns budget proportional to operational signal (logs + metrics) rather than
 * spinning a load attempt per request.
 */
let runtimeInitFailure = null;
const RUNTIME_INIT_COOLDOWN_MS = 30_000;

// Serialised dispatch chain. Initialised by `start()`; every stdin frame AND every
// internally-fired side-effect (e.g. watchdog session rebind) appends to it so callers cannot
// race `runtime.switchSession` against each other. See the comment block on `start()` for the
// race story this prevents.
let dispatchQueue = Promise.resolve();

function enqueue(fn) {
    dispatchQueue = dispatchQueue
        .then(async () => {
            // Pause for stdout drain before running the next task if writes are backing up.
            // Awaiting here naturally pauses the inbound pipe (since stdin frames also queue
            // through enqueue), which is the correct backpressure target: don't accept more
            // Pi events than we can ship to Java.
            if (process.stdout.writableLength > STDOUT_BACKPRESSURE_THRESHOLD_BYTES) {
                await new Promise((resolve) => process.stdout.once("drain", resolve));
            }
            return fn();
        })
        .catch((e) => log("dispatch queue swallowed:", e?.message ?? e));
    return dispatchQueue;
}

async function ensureRuntime() {
    if (runtime) return runtime;
    if (runtimeInitPromise) return runtimeInitPromise;
    if (runtimeInitFailure && Date.now() - runtimeInitFailure.at < RUNTIME_INIT_COOLDOWN_MS) {
        throw runtimeInitFailure.err;
    }

    runtimeInitPromise = (async () => {
        mkdirSync(SESSIONS_DIR, { recursive: true });
        const {
            createAgentSessionRuntime,
            createAgentSessionFromServices,
            createAgentSessionServices,
            SessionManager,
            DefaultResourceLoader,
            getAgentDir,
        } = await loadSdk();
        const agentDir = AGENT_DIR_OVERRIDE ?? (typeof getAgentDir === "function" ? getAgentDir() : "/home/agent/.pi");

        // System prompt is optional in v1 — the Java caller may inject it later. If the
        // resource exists we load it once and reuse via ResourceLoader override.
        if (existsSync(SYSTEM_PROMPT_PATH)) {
            try {
                systemPrompt = readFileSync(SYSTEM_PROMPT_PATH, "utf8");
                log(`loaded system prompt: ${systemPrompt.length} bytes`);
            } catch (e) {
                log("system prompt read failed (continuing without):", e);
            }
        }

        // Custom tools (defined once; same instances reused across thread switches).
        const fetchContextTool = defineFetchContextTool();
        const linkFindingTool = defineLinkFindingTool();

        // Factory: rebuilds cwd-bound services each time the runtime needs a new session
        // (newSession, switchSession, fork, importFromJsonl). See pi-coding-agent docs/sdk.md §137-155.
        const createRuntime = async ({ cwd, sessionManager, sessionStartEvent }) => {
            const services = await createAgentSessionServices({ cwd, agentDir: agentDir });
            const loader = systemPrompt
                ? new DefaultResourceLoader({
                      cwd,
                      agentDir: agentDir,
                      systemPromptOverride: () => systemPrompt,
                  })
                : new DefaultResourceLoader({ cwd, agentDir: agentDir });
            await loader.reload();
            // Tools allowlist: deny everything except our two custom tools. Pi's default tool
            // set is ["read","bash","edit","write"] (sdk.ts §271-277) — exposing those to the
            // mentor agent would let the LLM run shell commands inside the container. The
            // mentor only needs fetch_context (server callback) and link_finding (event emit).
            const result = await createAgentSessionFromServices({
                services,
                sessionManager,
                sessionStartEvent,
                customTools: [fetchContextTool, linkFindingTool],
                tools: ["fetch_context", "link_finding"],
                resourceLoader: loader,
            });
            return { ...result, services, diagnostics: services.diagnostics };
        };

        // Start with an in-memory session — the first open_thread will switch to the persistent file.
        const r = await createAgentSessionRuntime(createRuntime, {
            cwd: CWD,
            agentDir: agentDir,
            sessionManager: SessionManager.inMemory(),
        });
        runtime = r;
        log("runtime initialised");
        return runtime;
    })();
    try {
        const r = await runtimeInitPromise;
        runtimeInitFailure = null;
        return r;
    } catch (err) {
        runtimeInitFailure = { err, at: Date.now() };
        throw err;
    } finally {
        runtimeInitPromise = null;
    }
}

//
// When Pi calls this tool, we emit a JSON-RPC request to Java and await a matching response.
// 10s timeout — on miss, we resolve Pi's tool call with an error result so the agent reasons
// about the gap rather than crashing the turn.

function defineFetchContextTool() {
    const { defineTool } = sdk;
    return defineTool({
        name: "fetch_context",
        label: "Fetch Context",
        description:
            "Fetch a Hephaestus context aspect (workspace state, user activity, practice catalog, finding history) " +
            "from the server. Returns JSON content. Allowed paths: workspace.json, user.json, " +
            "practice_catalog.json, findings_history.json. Names match the aspect provider OUTPUT_KEY constants.",
        parameters: {
            type: "object",
            additionalProperties: false,
            required: ["path"],
            properties: {
                path: { type: "string", minLength: 1 },
            },
        },
        execute: async (_toolCallId, params) => {
            const path = String(params?.path ?? "").trim();
            // Pi treats THROWN errors as the tool's failure signal (agent-loop.ts §632-638);
            // a returned `isError: true` field is ignored by the runtime. Throw so the LLM sees
            // an `is_error` tool result AND so the runtime properly classifies the call as failed.
            if (!FETCH_CONTEXT_ALLOWED.has(path)) {
                throw new Error(`fetch_context: path "${path}" is not in the allow-list`);
            }
            if (!activeThreadId) {
                throw new Error("fetch_context: no active thread bound to the runtime");
            }
            const state = threads.get(activeThreadId);
            if (!state) {
                throw new Error(`fetch_context: thread state lost for ${activeThreadId}`);
            }
            const callbackId = `fc-${randomUUID()}`;
            const { promise, resolve, reject } = Promise.withResolvers();
            const timer = setTimeout(() => {
                if (state.pendingFetchContexts.delete(callbackId)) {
                    log(`fetch_context timed out: thread=${activeThreadId} path=${path} id=${callbackId}`);
                    reject(new Error(`fetch_context(${path}) timed out after ${FETCH_CONTEXT_TIMEOUT_MS}ms`));
                }
            }, FETCH_CONTEXT_TIMEOUT_MS);
            state.pendingFetchContexts.set(callbackId, { resolve, reject, timer });

            // Emit the callback request. Note: Java sees this as a top-level JSON-RPC request.
            writeFrame({
                jsonrpc: "2.0",
                id: callbackId,
                method: "fetch_context",
                params: { threadId: activeThreadId, path },
            });
            return promise;
        },
    });
}

function defineLinkFindingTool() {
    const { defineTool } = sdk;
    return defineTool({
        name: "link_finding",
        label: "Link Finding",
        description:
            "Surface a Hephaestus practice finding inline in the chat by linking it to its UUID. " +
            "Use this when referring to a specific finding from a prior review.",
        parameters: {
            type: "object",
            additionalProperties: false,
            required: ["findingId"],
            properties: {
                findingId: { type: "string", minLength: 1 },
            },
        },
        execute: async (_toolCallId, params) => {
            const findingId = String(params?.findingId ?? "").trim();
            if (!findingId) {
                throw new Error("link_finding: findingId is required");
            }
            // Emit a synthetic event the Java translator maps to a `data-finding` UI chunk.
            if (activeThreadId) {
                sendEvent(activeThreadId, { type: "link_finding", findingId });
            }
            return {
                content: [{ type: "text", text: `Linked finding ${findingId}` }],
                details: { findingId },
            };
        },
    });
}

/**
 * Defensive fallback in case Java forgets to flatten. Walks an AI SDK UIMessage `parts` array
 * and concatenates every text part. Non-text parts (tool calls, reasoning, data-finding) are
 * intentionally dropped — they don't belong in the LLM-visible recap.
 */
function flattenPartsToText(parts) {
    if (!Array.isArray(parts)) return "";
    const out = [];
    for (const part of parts) {
        if (part && typeof part === "object" && part.type === "text" && typeof part.text === "string") {
            out.push(part.text);
        }
    }
    return out.join("\n");
}

async function handleHello(id /*, params */) {
    // Java validates only `protocolVersion` (MentorChatService#verifyProtocol); the runner
    // image pins Node ≥22 (see docker/agents/pi/Dockerfile), so callers can rely on
    // Promise.withResolvers natively — no polyfill or capability advertisement needed.
    sendResult(id, { protocolVersion: PROTOCOL_VERSION });
    // Cold-start: trigger Pi SDK init in the background so it overlaps with Java's
    // orchestration between hello-reply and the first open_thread (DB load, aspect build,
    // SSE wiring). ensureRuntime caches its own promise so the foreground open_thread call
    // awaits the same result without re-loading.
    //
    // Fired AFTER the hello reply because Pi SDK module evaluation runs synchronously between
    // dynamic-import yield points; firing before the reply would delay hello by the load time.
    // Failure is logged to stderr (the runner has no structured-event observability surface
    // wired yet) and ensureRuntime's own cooldown handles retry on subsequent demand.
    if (!PROTOCOL_ONLY) {
        setImmediate(() => {
            ensureRuntime().catch((e) => log("prewarm ensureRuntime failed (will retry on demand):", e));
        });
    }
}

async function handleOpenThread(id, params) {
    const threadId = String(params?.threadId ?? "").trim();
    if (!threadId) {
        return sendError(id, ERR.INVALID_REQUEST, "threadId is required");
    }
    try {
        await ensureRuntime();
    } catch (e) {
        log("runtime init failed:", e);
        return sendError(id, ERR.PI_ERROR, `runtime init failed: ${e.message ?? e}`);
    }

    let state = threads.get(threadId);
    if (!state) {
        const sessionPath = path.join(SESSIONS_DIR, `${threadId}.jsonl`);
        state = new ThreadState(threadId, sessionPath);
        threads.set(threadId, state);
    }

    try {
        await bindThread(state);
        sendResult(id, { threadId, sessionPath: state.sessionPath });
    } catch (e) {
        log(`open_thread failed for ${threadId}:`, e);
        sendError(id, ERR.PI_ERROR, `open_thread failed: ${e.message ?? e}`);
    }
}

// Detach previous thread (unsubscribe), switch the runtime session file, re-subscribe.
// SDK docs §163-171 are explicit that listeners bind to a specific AgentSession; missing the
// rebind would silently drop events after the first switch.
async function bindThread(state) {
    if (activeThreadId === state.threadId && state.unsubscribe) {
        return; // already bound
    }
    // Tear down the previous binding.
    if (activeThreadId) {
        const prev = threads.get(activeThreadId);
        if (prev?.unsubscribe) {
            try {
                prev.unsubscribe();
            } catch (e) {
                log("prev unsubscribe threw:", e);
            }
            prev.unsubscribe = null;
        }
    }
    // switchSession on the runtime (creates the file if missing — SessionManager handles persistence).
    // If the file doesn't exist yet, Pi SDK creates a fresh session bound to that path.
    const { cancelled } = await runtime.switchSession(state.sessionPath);
    if (cancelled) {
        throw new Error(`switchSession cancelled by extension hook for thread ${state.threadId}`);
    }
    activeThreadId = state.threadId;
    // Re-subscribe to the new session.
    state.unsubscribe = runtime.session.subscribe((event) => {
        forwardEvent(state, event);
    });
    log(`bound thread ${state.threadId} → ${state.sessionPath}`);
}

function forwardEvent(state, event) {
    // Pass through raw Pi event for Java MentorEventTranslator. Clear in-flight flag on agent_end.
    sendEvent(state.threadId, event);
    if (event?.type === "agent_end") {
        // The watchdog synthesises its own agent_end at the end of a forced rebind (see
        // runWatchdogRebind). That path emits its own agent_end via sendEvent directly — it
        // does NOT route through forwardEvent — so this branch only fires for genuine
        // SDK-emitted turn completions.
        clearTurnWatchdog(state);
        state.inFlight = false;
        maybePostTurnGc();
    }
}

/**
 * Best-effort post-turn V8 compaction. Triggered only when {@code --expose-gc} was passed
 * (mentor; not practice). Effect-only: we do NOT emit observability events for this — write-only
 * telemetry that no Java consumer reads is just stdout noise. If we ever want a production
 * metric for GC behavior, wire it deliberately through a dedicated metrics path.
 *
 * <p>Gated on heap-watermark: a full STW major GC costs ~50–200 ms on a 100 MB heap; for a
 * just-finished 1-token turn the cost is all pause, no benefit. Only fire if V8's own heap
 * has grown past the 64 MB mark — below that, V8's own scavenger handles things without our
 * help.
 *
 * <p>Yielded via setImmediate so the agent_end frame drains to the kernel pipe buffer first.
 */
const POST_TURN_GC_HEAP_THRESHOLD_BYTES = 64 * 1024 * 1024;

function maybePostTurnGc() {
    if (typeof global.gc !== "function") return;
    if (process.memoryUsage().heapUsed < POST_TURN_GC_HEAP_THRESHOLD_BYTES) return;
    setImmediate(() => {
        try {
            global.gc();
        } catch (e) {
            log("post-turn gc threw:", e);
        }
    });
}

async function handleReplayContext(id, params) {
    const threadId = String(params?.threadId ?? "").trim();
    const messages = Array.isArray(params?.messages) ? params.messages : null;
    if (!threadId || !messages) {
        return sendError(id, ERR.INVALID_REQUEST, "threadId and messages are required");
    }
    const state = threads.get(threadId);
    if (!state) {
        return sendError(id, ERR.THREAD_NOT_OPEN, `thread ${threadId} is not open`);
    }
    try {
        await bindThread(state);
    } catch (e) {
        return sendError(id, ERR.PI_ERROR, `bind failed: ${e.message ?? e}`);
    }

    // Seed the LLM context with prior conversation history.
    //
    // Root-cause note: appendCustomMessageEntry (used here before) writes entries of
    // type "custom_message" to the session JSONL. Those entries surface in buildSessionContext
    // (used by the interactive CLI renderer) but are NOT included in createContextSnapshot —
    // the method runPromptMessages calls to build the messages array sent to the LLM.
    // Only agent._state.messages (populated by processEvents on each message_end) reaches
    // the LLM. appendCustomMessageEntry was therefore a silent no-op for conversation history.
    //
    // Fix: push synthetic user/assistant pairs directly into agent._state.messages.
    // Guard: if _state.messages is already non-empty the session file was loaded with prior
    // turns (warm container, same thread); replay would duplicate them — skip.
    // Stub mode: runtime.session.agent is undefined; skip gracefully.
    const agent = runtime.session?.agent;
    if (!agent) {
        // Stub / PROTOCOL_ONLY mode — no real agent, replay is a no-op.
        return sendResult(id, { replayed: 0 });
    }
    if (agent._state.messages.length > 0 || messages.length === 0) {
        log(`replay_context: skip (existing=${agent._state.messages.length}, offered=${messages.length})`);
        return sendResult(id, { replayed: 0 });
    }
    let replayed = 0;
    try {
        for (const msg of messages) {
            const role = msg?.role === "assistant" ? "assistant" : "user";
            // Java owns the flattening: `text` is the pre-extracted concatenation of every
            // type==="text" entry in `parts`. We accept `parts` as a fallback so future
            // structured replay (tool calls, reasoning) can roll through without a server
            // ABI bump; today only text entries make it into the LLM-visible recap.
            const text = String(msg?.text ?? flattenPartsToText(msg?.parts)).trim();
            if (!text) continue;
            // Synthetic message shapes for `agent._state.messages`. Assistant messages MUST
            // carry `usage` and `stopReason` — Pi SDK's `calculateContextTokens` (called from
            // `getContextStats` on every prompt) reads `usage.totalTokens` unconditionally on
            // any assistant message whose `stopReason` is neither "aborted" nor "error". A
            // missing `usage` throws `Cannot read properties of undefined (reading
            // 'totalTokens')` and the whole prompt rejects. Seeding zeroed usage tells the SDK
            // "this synthetic message contributed no measured tokens" — the next real turn's
            // assistant message will populate proper usage and compaction picks up from there.
            if (role === "assistant") {
                agent._state.messages.push({
                    role: "assistant",
                    content: [{ type: "text", text }],
                    stopReason: "stop",
                    usage: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, totalTokens: 0 },
                });
            } else {
                agent._state.messages.push({ role: "user", content: [{ type: "text", text }] });
            }
            replayed++;
        }
        log(`replay_context: seeded ${replayed} messages into LLM context`);
        sendResult(id, { replayed });
    } catch (e) {
        log(`replay_context failed: ${e.message ?? e}`);
        sendError(id, ERR.PI_ERROR, `replay failed: ${e.message ?? e}`);
    }
}

async function handlePrompt(id, params) {
    const threadId = String(params?.threadId ?? "").trim();
    const text = String(params?.text ?? "");
    if (!threadId || !text) {
        return sendError(id, ERR.INVALID_REQUEST, "threadId and text are required");
    }
    const state = threads.get(threadId);
    if (!state) {
        return sendError(id, ERR.THREAD_NOT_OPEN, `thread ${threadId} is not open`);
    }
    if (state.inFlight) {
        return sendError(id, ERR.TURN_IN_FLIGHT, `thread ${threadId} already has a turn in flight`);
    }

    try {
        await bindThread(state);
    } catch (e) {
        return sendError(id, ERR.PI_ERROR, `bind failed: ${e.message ?? e}`);
    }

    state.inFlight = true;
    startTurnWatchdog(state);

    // Accept-and-stream: respond to the prompt RPC immediately; the actual turn is observed
    // via subscribed events. This mirrors the SDK's own RPC mode semantics (rpc.md §44-77).
    sendResult(id, { accepted: true });

    runtime.session
        .prompt(text)
        .then(() => {
            log(`prompt resolved: thread=${threadId}`);
        })
        .catch((e) => {
            log(`prompt rejected for thread ${threadId}: ${e?.message ?? e}`);
            // Emit a synthetic event so Java can release its lock and surface to the user.
            sendEvent(threadId, { type: "pi_error", error: String(e?.message ?? e) });
            sendEvent(threadId, { type: "agent_end", messages: [] });
            clearTurnWatchdog(state);
            state.inFlight = false;
        });
}

async function handleSteer(id, params) {
    const threadId = String(params?.threadId ?? "").trim();
    const text = String(params?.text ?? "");
    if (!threadId || !text) {
        return sendError(id, ERR.INVALID_REQUEST, "threadId and text are required");
    }
    const state = threads.get(threadId);
    if (!state) {
        return sendError(id, ERR.THREAD_NOT_OPEN, `thread ${threadId} is not open`);
    }
    try {
        await bindThread(state);
        await runtime.session.steer(text);
        sendResult(id, { accepted: true });
    } catch (e) {
        sendError(id, ERR.PI_ERROR, `steer failed: ${e.message ?? e}`);
    }
}

async function handleAbort(id, params) {
    const threadId = String(params?.threadId ?? "").trim();
    if (!threadId) {
        return sendError(id, ERR.INVALID_REQUEST, "threadId is required");
    }
    const state = threads.get(threadId);
    if (!state) {
        return sendError(id, ERR.THREAD_NOT_OPEN, `thread ${threadId} is not open`);
    }
    if (!state.inFlight) {
        return sendError(id, ERR.INVALID_STATE, "no turn in flight for this thread");
    }
    try {
        await bindThread(state);
        await runtime.session.abort();
        sendResult(id, { aborted: true });
        // agent_end will arrive naturally; that's where inFlight clears.
    } catch (e) {
        sendError(id, ERR.PI_ERROR, `abort failed: ${e.message ?? e}`);
    }
}

async function handleCloseThread(id, params) {
    const threadId = String(params?.threadId ?? "").trim();
    if (!threadId) {
        return sendError(id, ERR.INVALID_REQUEST, "threadId is required");
    }
    const state = threads.get(threadId);
    if (!state) {
        // Idempotent close.
        return sendResult(id, { closed: false });
    }
    cleanupThread(state);
    threads.delete(threadId);
    if (activeThreadId === threadId) {
        activeThreadId = null;
    }
    sendResult(id, { closed: true });
}

async function handleShutdown(id) {
    sendResult(id, { shuttingDown: true });
    // Reject pending fetch_context callbacks (Pi flushes a clean is-error tool result) and
    // tear down sessions. cleanupThread is sync, so a plain loop is enough.
    for (const state of threads.values()) cleanupThread(state);
    threads.clear();
    activeThreadId = null;
    try {
        await runtime?.dispose?.();
    } catch (e) {
        log(`runtime.dispose during shutdown failed: ${e?.message ?? e}`);
    }
    // Node buffers stdout when it's a pipe (always under Java's ProcessBuilder); the empty
    // write callback fires after the queued result frame reaches the kernel, so process.exit
    // doesn't truncate it.
    process.stdout.write("", () => {
        log("shutdown requested — exiting");
        process.exit(0);
    });
}

// Max bytes of context surfaced to the LLM per fetch_context call. Aspect JSONs occasionally
// balloon (e.g. `findings.json` for a heavy reviewer); without a cap, a single tool call can
// blow the model's context window. 200 KB ≈ 50K tokens at ~4 chars/token — comfortably below
// gpt-oss-120b's 128 K window and gives headroom for system prompt + history.
const FETCH_CONTEXT_MAX_BYTES = 200_000;

// fetch_context responses (Java → runner)
function handleFetchContextResponse(frame) {
    const callbackId = String(frame?.id ?? "");
    if (!callbackId) {
        log("fetch_context response missing id; dropping");
        return;
    }
    // Search every thread for the matching pending callback (small N).
    for (const state of threads.values()) {
        const pending = state.pendingFetchContexts.get(callbackId);
        if (!pending) continue;
        state.pendingFetchContexts.delete(callbackId);
        clearTimeout(pending.timer);
        if (frame.error) {
            // Reject so Pi records this tool call as failed (agent-loop.ts §632-638). Echo the
            // JSON-RPC error code in the rejection so server-side diagnostics survive the
            // rethrow → LLM tool-error round-trip.
            const code = frame.error.code ?? "unknown";
            const message = frame.error.message || "unknown error";
            const err = new Error(`fetch_context server error [${code}]: ${message}`);
            err.code = code;
            pending.reject(err);
        } else {
            // Pi tool results accept `content: [{type:"text", text: string}]` (verified against
            // pi-mono SDK tool-result type). When Java returns a JSON object we stringify ONCE;
            // if it returned a plain string we pass it through untouched. The earlier code
            // double-stringified strings ("\"foo\"" → "\\\"foo\\\""), which leaked an extra
            // layer of JSON escaping into the LLM prompt.
            const content = frame.result?.content;
            let text =
                content == null
                    ? "{}"
                    : typeof content === "string"
                    ? content
                    : JSON.stringify(content);
            let truncated = false;
            if (text.length > FETCH_CONTEXT_MAX_BYTES) {
                const dropped = text.length - FETCH_CONTEXT_MAX_BYTES;
                text = text.slice(0, FETCH_CONTEXT_MAX_BYTES) + `\n…[truncated ${dropped} chars]`;
                truncated = true;
            }
            pending.resolve({
                content: [{ type: "text", text }],
                details: { ok: true, length: text.length, truncated },
            });
        }
        return;
    }
    log(`fetch_context response had no matching pending callback: id=${callbackId}`);
}

// Single watchdog: fires (TURN_BUDGET_MS + TURN_GRACE_MS) after the turn starts. The wider
// budget covers the worst-case Pi turn; the grace allows Pi to settle if it's close to
// finishing on its own. On fire we dispose the session, rebind, and surface a synthetic
// agent_end so Java releases its lock. Java sees `turn_watchdog_fired` for diagnostics.
function startTurnWatchdog(state) {
    clearTurnWatchdog(state);
    // The setTimeout body runs OFF the dispatch queue (it's wall-clock-driven), so the
    // session-rebinding work — which races user RPCs that also call switchSession — is
    // funnelled BACK through the queue via `enqueue`. Without this the watchdog could
    // hit `runtime.switchSession` concurrently with a `prompt` / `close_thread` and
    // rebind to whichever resolves last, losing every subsequent event.
    state.watchdogTimer = setTimeout(() => {
        enqueue(() => runWatchdogRebind(state));
    }, TURN_BUDGET_MS + TURN_GRACE_MS);
}

async function runWatchdogRebind(state) {
    // If `close_thread` was enqueued between `setTimeout` firing and this task running, the
    // thread state is gone — rebinding would resurrect a ghost subscription that survives until
    // the next valid bind orphans it. Bail out; the close already cleaned up the watchdog.
    if (!threads.has(state.threadId)) {
        log(`watchdog rebind skipped: thread=${state.threadId} already closed`);
        return;
    }
    log(`watchdog fired: rebuilding session for thread=${state.threadId}`);
    sendEvent(state.threadId, { type: "turn_watchdog_fired", threadId: state.threadId });
    try {
        // Reject pending fetch_context callbacks BEFORE switchSession so the rebound
        // session can't echo stale callback ids.
        for (const [cbId, pending] of state.pendingFetchContexts) {
            clearTimeout(pending.timer);
            pending.reject(new Error("fetch_context: turn aborted by watchdog"));
            state.pendingFetchContexts.delete(cbId);
        }
        await runtime?.session?.abort().catch((e) => log(`abort during watchdog failed: ${e.message ?? e}`));
        if (state.unsubscribe) {
            try { state.unsubscribe(); } catch { /* ignore */ }
            state.unsubscribe = null;
        }
        // Rebind to a fresh AgentSession on the same JSONL file (switchSession teardown
        // invalidates captured extension ctx; listeners are session-scoped per Pi SDK).
        // If a DIFFERENT thread is currently bound, tear down its subscription first; otherwise
        // the rebind silently steals the runtime and leaks the prior subscription. After a
        // successful switchSession the runtime is now bound to OUR sessionPath, so update
        // activeThreadId; the prior code left the invariant stale after a watchdog rebind.
        if (runtime) {
            try {
                if (activeThreadId && activeThreadId !== state.threadId) {
                    const prev = threads.get(activeThreadId);
                    if (prev?.unsubscribe) {
                        try {
                            prev.unsubscribe();
                        } catch {
                            /* ignore */
                        }
                        prev.unsubscribe = null;
                    }
                }
                await runtime.switchSession(state.sessionPath);
                activeThreadId = state.threadId;
                state.unsubscribe = runtime.session.subscribe((event) => forwardEvent(state, event));
            } catch (e) {
                log(`watchdog rebind failed for thread=${state.threadId}: ${e.message ?? e}`);
                if (activeThreadId === state.threadId) activeThreadId = null;
            }
        }
    } finally {
        sendEvent(state.threadId, { type: "agent_end", messages: [] });
        state.inFlight = false;
    }
}

function clearTurnWatchdog(state) {
    if (state.watchdogTimer) clearTimeout(state.watchdogTimer);
    state.watchdogTimer = null;
}

function cleanupThread(state) {
    clearTurnWatchdog(state);
    if (state.unsubscribe) {
        try { state.unsubscribe(); } catch { /* ignore */ }
        state.unsubscribe = null;
    }
    for (const [cbId, pending] of state.pendingFetchContexts) {
        clearTimeout(pending.timer);
        // Reject so Pi sees a failed tool call (thrown error → isError: true).
        pending.reject(new Error("fetch_context: thread closed before context arrived"));
        state.pendingFetchContexts.delete(cbId);
    }
}

const METHODS = {
    hello: handleHello,
    open_thread: handleOpenThread,
    replay_context: handleReplayContext,
    prompt: handlePrompt,
    steer: handleSteer,
    abort: handleAbort,
    close_thread: handleCloseThread,
    shutdown: handleShutdown,
};

async function dispatch(frame) {
    // Two shapes arrive on stdin:
    //   1. JSON-RPC requests from Java: {jsonrpc, id, method, params}
    //   2. JSON-RPC responses to our fetch_context callbacks: {jsonrpc, id, result|error}
    // JSON-RPC 2.0 §6 allows batch (top-level array) but neither end emits batches today.
    // Reject loudly rather than silently dropping — a future Java caller that bundles
    // open_thread + replay_context + prompt would otherwise vanish into the log.
    if (Array.isArray(frame)) {
        return sendError(null, ERR.INVALID_REQUEST, "batch requests are not supported on this transport");
    }
    if (frame?.method) {
        const id = frame.id;
        const handler = METHODS[frame.method];
        if (!handler) {
            return sendError(id, ERR.METHOD_NOT_FOUND, `unknown method: ${frame.method}`);
        }
        try {
            await handler(id, frame.params ?? {});
        } catch (e) {
            log(`handler ${frame.method} threw: ${e?.message ?? e}`);
            sendError(id, ERR.PI_ERROR, `internal error: ${e?.message ?? e}`);
        }
        return;
    }
    if (frame?.id != null && (frame.result !== undefined || frame.error !== undefined)) {
        return handleFetchContextResponse(frame);
    }
    log("unrecognised frame:", JSON.stringify(frame).slice(0, 200));
}

//
// Activated by `MENTOR_RUNNER_PROTOCOL_ONLY=1`. Mimics the smallest surface the runner needs:
// runtime.switchSession / runtime.session.subscribe / .prompt / .steer / .abort. Each
// `prompt(text)` synthesises an agent_start + a text_delta + an agent_end so the framing
// loop has something deterministic to forward. The stub honours `MENTOR_RUNNER_STUB_DELAY_MS`
// so tests can simulate slow turns / watchdog firing.

function buildStubSdk() {
    const subscribers = new Set();
    let isStreaming = false;
    const stubSession = {
        subscribe(listener) {
            subscribers.add(listener);
            return () => subscribers.delete(listener);
        },
        async prompt(text) {
            if (isStreaming) {
                throw new Error("stub: already streaming (caller should pass streamingBehavior)");
            }
            isStreaming = true;
            const delay = Number(process.env.MENTOR_RUNNER_STUB_DELAY_MS) || 5;
            const emit = (event) => {
                for (const s of subscribers) {
                    try {
                        s(event);
                    } catch {
                        /* ignore stub listener throw */
                    }
                }
            };
            emit({ type: "agent_start" });
            await new Promise((r) => setTimeout(r, delay));
            emit({
                type: "message_update",
                assistantMessageEvent: { type: "text_delta", contentIndex: 0, delta: `stub: ${text}` },
            });
            await new Promise((r) => setTimeout(r, delay));
            emit({ type: "agent_end", messages: [] });
            isStreaming = false;
        },
        async steer(_text) {
            /* no-op */
        },
        async abort() {
            if (isStreaming) {
                for (const s of subscribers) {
                    try {
                        s({ type: "agent_end", messages: [] });
                    } catch {
                        /* ignore */
                    }
                }
                isStreaming = false;
            }
        },
    };
    return {
        defineTool: (tool) => tool,
        SessionManager: { inMemory: () => ({}) },
        DefaultResourceLoader: class { constructor() {} async reload() {} },
        createAgentSessionServices: async () => ({ diagnostics: [] }),
        createAgentSessionFromServices: async () => ({ session: stubSession, extensionsResult: { extensions: [], errors: [], runtime: null } }),
        createAgentSessionRuntime: async (_factory, _opts) => ({
            session: stubSession,
            services: { diagnostics: [], sessionManager: { appendCustomMessageEntry: () => "stub-entry" } },
            async switchSession(_path) { return { cancelled: false }; },
            async dispose() {},
        }),
    };
}

function announceReady() {
    // Notification (no id) so Java's RPC layer ignores it but the controller observes the event.
    // `threadId: null` keeps the params envelope identical to every other event frame
    // (`sendEvent`), so downstream consumers can match on `params.threadId` uniformly.
    writeFrame({
        jsonrpc: "2.0",
        method: "event",
        params: {
            threadId: null,
            event: {
                type: "runner_ready",
                protocolVersion: PROTOCOL_VERSION,
                turnBudgetMs: TURN_BUDGET_MS,
                turnGraceMs: TURN_GRACE_MS,
            },
        },
    });
}

function start() {
    // Stdin frames AND internally-fired side-effects (watchdog rebinds) all funnel through
    // module-scoped `dispatchQueue` via `enqueue`. Without that, back-to-back frames (e.g.
    // open_thread immediately followed by prompt) — or worse, a stdin frame racing the
    // watchdog timer — would hit `runtime.switchSession` concurrently and rebind the
    // single Pi session to whichever resolves last, losing every subsequent event.
    const splitter = createLineSplitter((line) => {
        enqueue(async () => {
            let frame;
            try {
                frame = JSON.parse(line);
            } catch (e) {
                log(`parse error: ${e.message} (line len=${line.length})`);
                return;
            }
            try {
                await dispatch(frame);
            } catch (e) {
                log("dispatch failed:", e);
            }
        });
    });

    process.stdin.on("data", (chunk) => splitter(chunk));
    // Route stdin EOF through the dispatch queue so cleanup invariants (runtime.dispose,
    // unsubscribe of every active thread, pending fetch_context rejection) run identically
    // for SIGTERM and EOF. Exiting directly here skipped the SessionManager file flush and
    // left jemalloc thread caches uncollected on parent-process drop.
    process.stdin.on("end", () => {
        log("stdin EOF — shutting down");
        enqueue(() => handleShutdown(undefined));
    });
    process.stdin.on("error", (e) => {
        log("stdin error:", e);
        process.exit(2);
    });

    process.on("uncaughtException", (e) => {
        log("uncaughtException:", e);
        try {
            process.stdout.write("", () => process.exit(2));
        } catch {
            process.exit(2);
        }
    });
    process.on("unhandledRejection", (e) => {
        log("unhandledRejection:", e);
    });

    // Funnel SIGTERM / SIGINT through the dispatch queue so a supervisor-driven shutdown
    // observes the same teardown invariants as an RPC `shutdown` (rejects pending fetch
    // callbacks, disposes the runtime, drains the dispatch chain). Without these, the
    // process exits with the default disposition mid-task — any in-flight Pi event between
    // user-visible chunks vanishes on the wire and Java waits for its 165s prompt deadline.
    // Pass `undefined` (not `null`) so `sendResult` skips emitting a response frame — there's
    // no RPC request to reply to. `null` would emit `{id:null, result:…}`, a JSON-RPC reply to
    // nobody, polluting Java's frame log on every signal-driven shutdown.
    for (const signal of ["SIGTERM", "SIGINT"]) {
        process.on(signal, () => {
            log(`received ${signal} — initiating clean shutdown`);
            enqueue(() => handleShutdown(undefined));
        });
    }

    // Allocator self-check. PiRuntimeFactory passes LD_PRELOAD=…/libjemalloc.so.2 in the
    // mentor runner's spawn env, and the Dockerfile creates the per-arch symlink at build time.
    // If either of those drifts (symlink missing on a future image, env var clobbered by a
    // future deploy config), ld.so silently falls back to glibc malloc and the MALLOC_CONF
    // tuning (dirty_decay, narenas) disappears. Without this check the regression would
    // only manifest as a slow RSS bloat in production with no clear cause. One stderr line at
    // startup is enough — ops can grep for it.
    if (!PROTOCOL_ONLY) {
        try {
            const maps = readFileSync("/proc/self/maps", "utf8");
            if (!/libjemalloc/.test(maps)) {
                log(
                    "WARN allocator self-check: libjemalloc NOT loaded into this process — " +
                    "falling back to glibc malloc. Memory tuning from PiRuntimeFactory#nodeEnvFor " +
                    "is disabled. Check Dockerfile symlink + LD_PRELOAD env."
                );
            }
        } catch {
            // /proc/self/maps unavailable (non-Linux, very locked-down containers). Silent.
        }
    }
    announceReady();
    // SDK prewarm is intentionally NOT triggered here — it now fires inside handleHello
    // after the reply is written. Pi SDK module evaluation is synchronous (~300-400 ms) and
    // would block hello until it completes. Firing it post-hello lets the reply land
    // instantly and the load runs while Java orchestrates open_thread.
}

start();
