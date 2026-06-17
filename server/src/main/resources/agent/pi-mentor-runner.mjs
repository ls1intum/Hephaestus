// pi-mentor-runner.mjs — interactive Pi-mentor runner. Long-lived JSON-RPC 2.0 over stdin/stdout
// (one object per line, terminator strictly `\n`).
//
// Protocol (Java ↔ runner):
//   stdin  (Java→runner): requests `{jsonrpc, id, method, params}`
//   stdout (runner→Java): responses + notifications (`method:"event"`) + runner→Java callbacks
//                         (`fetch_context`).
// Methods: hello, open_thread, prompt, steer, abort, close_thread, shutdown.
// Session restore: Java injects `.sessions/<threadId>.jsonl` into the container at start
//                  time (sourced from `chat_thread.session_jsonl` BYTEA in Postgres). The runner's
//                  `bindThread` → `switchSession` loads byte-identical prior turns transparently
//                  via the Pi SDK SessionManager — no explicit replay RPC is needed.
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

// PROTOCOL_ONLY tests / CI smoke-test invocations run the runner as a plain Node process where
// /workspace is unwritable; MENTOR_RUNNER_* env overrides let callers point at a tmpdir without
// forking the runner code. The workspace literals below are pinned by `WorkspaceAbiSyncTest` —
// keep them quoted strings, not template expressions, so the grep stays exact.
const WORKSPACE_ROOT = "/workspace";
const MENTOR_SYSTEM_PROMPT_PATH = "agent/mentor/system.md"; // WorkspaceAbi.MENTOR_SYSTEM_PROMPT_PATH
const CWD = process.env.MENTOR_RUNNER_CWD ?? WORKSPACE_ROOT;
const SESSIONS_DIR = process.env.MENTOR_RUNNER_SESSIONS_DIR ?? `${WORKSPACE_ROOT}/.sessions`;
const SYSTEM_PROMPT_PATH = process.env.MENTOR_RUNNER_SYSTEM_PROMPT_PATH ?? `${WORKSPACE_ROOT}/${MENTOR_SYSTEM_PROMPT_PATH}`;
// The SDK's `getAgentDir()` is the canonical default ("~/.pi/agent"). We resolve it lazily
// inside `ensureRuntime()` so the module loads without the SDK in protocol-only test mode.
const AGENT_DIR_OVERRIDE = process.env.PI_CODING_AGENT_DIR ?? null;
const PROTOCOL_VERSION = 1;

// WorkspaceAbi.EXIT_ENVELOPE_MISMATCH — exit code on protocol-version / image / config drift
// that makes this runner incompatible with the calling Java side. Java's launcher distinguishes
// this exit from a generic crash so deploy regressions surface as a structured failure.
const ENVELOPE_MISMATCH_EXIT = 42;

// Startup envelope check: if the Java launcher pins an expected protocol version via env,
// fail-fast with a structured exit so the deploy doesn't silently downgrade to a stub.
{
    const expectedRaw = process.env.MENTOR_RUNNER_EXPECTED_PROTOCOL_VERSION;
    if (expectedRaw !== undefined && expectedRaw !== "") {
        const expected = Number(expectedRaw);
        if (!Number.isFinite(expected) || expected !== PROTOCOL_VERSION) {
            process.stderr.write(
                `[pi-mentor-runner] FATAL envelope mismatch: ` +
                    `MENTOR_RUNNER_EXPECTED_PROTOCOL_VERSION=${expectedRaw}, runner PROTOCOL_VERSION=${PROTOCOL_VERSION}. ` +
                    `Exiting ${ENVELOPE_MISMATCH_EXIT}.\n`,
            );
            process.exit(ENVELOPE_MISMATCH_EXIT);
        }
    }
}

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
// whitelist lives Java-side in MentorAspects.ALLOWED_OUTPUT_KEYS (full-key match against
// the aspect providers' OUTPUT_KEY constants). Keep this set aligned with the
// {User,Workspace,PracticeCatalog,FindingsHistory,PracticeStanding,DeliveredFeedback}AspectProvider basenames.
const FETCH_CONTEXT_ALLOWED = new Set([
    "workspace.json",
    "user.json",
    "practice_catalog.json",
    "findings_history.json",
    "practice_standing.json",
    "delivered_feedback.json",
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

// LF-only line splitter. JSON.stringify leaves U+2028/U+2029 unescaped (nodejs/node-v0.x-archive
// #8221) so any splitter that treats Unicode line separators as newlines would corrupt JSON
// payloads. We split on 0x0a only (CRLF tolerant); this is what Node `readline` does too, but
// rolling our own keeps the framing rule trivially auditable and shared with the test fixture.
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

/** Pause dispatch when stdout backs up past this size; prevents OOM under slow SSE consumers. */
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

/** Fail-fast cooldown for runtime init: re-loading the SDK on every inbound frame is wasteful. */
let runtimeInitFailure = null;
const RUNTIME_INIT_COOLDOWN_MS = 30_000;

// Serialised dispatch chain — every stdin frame AND watchdog rebind appends here so callers
// cannot race `runtime.switchSession` against each other.
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
            AuthStorage,
            ModelRegistry,
            getAgentDir,
        } = await loadSdk();
        // `typeof getAgentDir === "function"` guard accommodates MENTOR_RUNNER_PROTOCOL_ONLY=1 mode,
        // where the stub SDK (see buildStubSdk) does not export getAgentDir. Tests then fall through
        // to the workspace-relative default that matches production's PI_CODING_AGENT_DIR.
        const agentDir = AGENT_DIR_OVERRIDE ?? (typeof getAgentDir === "function" ? getAgentDir() : "/workspace/.pi");

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

        // Same race workaround as pi-runner.mjs: register the hephaestus provider directly on
        // the ModelRegistry before createAgentSession. Reused across cwd switches; providers are
        // cwd-independent. PROTOCOL_ONLY mode's stub SDK exposes neither class.
        let sharedAuthStorage;
        let sharedModelRegistry;
        if (typeof AuthStorage?.create === "function" && typeof ModelRegistry?.create === "function") {
            sharedAuthStorage = AuthStorage.create();
            sharedModelRegistry = ModelRegistry.create(sharedAuthStorage);
            const hephaestusBaseUrl = process.env.PI_HEPHAESTUS_BASE_URL;
            const hephaestusModel = process.env.PI_HEPHAESTUS_MODEL;
            if (hephaestusBaseUrl && hephaestusModel) {
                sharedModelRegistry.registerProvider("hephaestus", {
                    name: "Hephaestus Gateway",
                    baseUrl: hephaestusBaseUrl,
                    apiKey: "PI_HEPHAESTUS_API_KEY",
                    authHeader: true,
                    api: "openai-completions",
                    models: [
                        {
                            id: hephaestusModel,
                            name: hephaestusModel,
                            reasoning: false,
                            input: ["text"],
                            cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
                            contextWindow: 131072,
                            maxTokens: 4096,
                        },
                    ],
                });
                log(`registered hephaestus provider: baseUrl=${hephaestusBaseUrl} model=${hephaestusModel}`);
            }
        }

        // Factory: rebuilds cwd-bound services on every session lifecycle event.
        const createRuntime = async ({ cwd, sessionManager, sessionStartEvent }) => {
            const services = await createAgentSessionServices({
                cwd,
                agentDir: agentDir,
                authStorage: sharedAuthStorage,
                modelRegistry: sharedModelRegistry,
            });
            const loader = systemPrompt
                ? new DefaultResourceLoader({
                      cwd,
                      agentDir: agentDir,
                      systemPromptOverride: () => systemPrompt,
                  })
                : new DefaultResourceLoader({ cwd, agentDir: agentDir });
            await loader.reload();
            // Built-in read/bash/grep let the mentor explore the read-only repo checkout
            // at /workspace/repo/ (git log, diffs, file contents). edit/write are denied —
            // the mentor is an observer, not a code author.
            const result = await createAgentSessionFromServices({
                services,
                sessionManager,
                sessionStartEvent,
                customTools: [fetchContextTool, linkFindingTool],
                tools: ["fetch_context", "link_finding", "read", "bash", "grep"],
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
            "Fetch a Hephaestus context aspect (workspace state, user activity, practice catalog, finding history, " +
            "prepared practice standing) from the server. Returns JSON content. Allowed paths: workspace.json, user.json, " +
            "practice_catalog.json, findings_history.json, practice_standing.json. Names match the aspect provider OUTPUT_KEY constants.",
        parameters: {
            type: "object",
            additionalProperties: false,
            required: ["path"],
            properties: {
                path: { type: "string", minLength: 1 },
            },
        },
        execute: async (_toolCallId, params) => {
            // NOT `path` — that's the imported `node:path` module; shadowing it here is a
            // future footgun if anyone adds `path.join(...)`.
            const aspect = String(params?.path ?? "").trim();
            // Pi treats THROWN errors as the tool's failure signal — a returned `isError:true`
            // is ignored by the runtime, so throw to flag the call as failed.
            if (!FETCH_CONTEXT_ALLOWED.has(aspect)) {
                throw new Error(`fetch_context: path "${aspect}" is not in the allow-list`);
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
                    log(`fetch_context timed out: thread=${activeThreadId} path=${aspect} id=${callbackId}`);
                    reject(new Error(`fetch_context(${aspect}) timed out after ${FETCH_CONTEXT_TIMEOUT_MS}ms`));
                }
            }, FETCH_CONTEXT_TIMEOUT_MS);
            state.pendingFetchContexts.set(callbackId, { resolve, reject, timer });

            writeFrame({
                jsonrpc: "2.0",
                id: callbackId,
                method: "fetch_context",
                params: { threadId: activeThreadId, path: aspect },
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

async function handleHello(id /*, params */) {
    // Java validates `protocolVersion` AND `protocolOnly` (MentorChatService#verifyProtocol);
    // shipping the latter on hello lets Java fail-closed if MENTOR_RUNNER_PROTOCOL_ONLY=1
    // leaks into a real deploy, instead of every user receiving stubbed answers.
    sendResult(id, { protocolVersion: PROTOCOL_VERSION, protocolOnly: PROTOCOL_ONLY });
    // Prewarm the SDK in the background while Java orchestrates DB load + aspect build.
    // Fired AFTER the hello reply because SDK module evaluation is synchronous.
    if (!PROTOCOL_ONLY) {
        setImmediate(() => {
            ensureRuntime().catch((e) => log("prewarm ensureRuntime failed (will retry on demand):", e));
        });
    }
}

// Canonical lowercase UUID, the only shape Java ever sends. Validating defensively at the
// runner boundary means a future caller that bypasses Java (dev bridge, mis-routed message)
// cannot land an arbitrary path inside `path.join(SESSIONS_DIR, …)` — `path.join` is NOT a
// security primitive and happily resolves `..` / absolute paths out of the base.
const THREAD_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

async function handleOpenThread(id, params) {
    const threadId = String(params?.threadId ?? "").trim().toLowerCase();
    if (!threadId) {
        return sendError(id, ERR.INVALID_REQUEST, "threadId is required");
    }
    if (!THREAD_ID_PATTERN.test(threadId)) {
        return sendError(id, ERR.INVALID_REQUEST, "threadId must be a canonical UUID");
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
        // Defence-in-depth: even with the regex above, assert the resolved path stays inside
        // SESSIONS_DIR. `path.resolve` collapses any residual `..` or symlink hop.
        const resolvedSessions = path.resolve(SESSIONS_DIR) + path.sep;
        if (!path.resolve(sessionPath).startsWith(resolvedSessions)) {
            return sendError(id, ERR.INVALID_REQUEST, "threadId resolves outside sessions dir");
        }
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
    // Switch FIRST so a switchSession failure doesn't leave the previous session unsubscribed
    // with no path back: if we tore down `prev.unsubscribe` first and switch threw, the
    // previous thread would lose its event stream permanently. SDK guarantees the prior
    // session's listeners are invalidated as a side effect of a successful switchSession.
    const prevState = activeThreadId ? threads.get(activeThreadId) : null;
    const { cancelled } = await runtime.switchSession(state.sessionPath);
    if (cancelled) {
        throw new Error(`switchSession cancelled by extension hook for thread ${state.threadId}`);
    }
    if (prevState?.unsubscribe) {
        try {
            prevState.unsubscribe();
        } catch (e) {
            log("prev unsubscribe threw:", e);
        }
        prevState.unsubscribe = null;
    }
    activeThreadId = state.threadId;
    state.unsubscribe = runtime.session.subscribe((event) => forwardEvent(state, event));
    log(`bound thread ${state.threadId} → ${state.sessionPath}`);
}

function forwardEvent(state, event) {
    // Emit session_persisted BEFORE agent_end so TranslatorState captures the bytes before
    // finalise runs. Watchdog-synthesised agent_end frames bypass this path.
    if (event?.type === "agent_end") {
        emitSessionPersisted(state);
    }
    sendEvent(state.threadId, event);
    if (event?.type === "agent_end") {
        clearTurnWatchdog(state);
        state.inFlight = false;
        maybePostTurnGc();
    }
}

function emitSessionPersisted(state) {
    if (!existsSync(state.sessionPath)) {
        // Legitimate case: PROTOCOL_ONLY stub never persists. In production this is anomalous —
        // surface as pi_error so Java logs a warning rather than silently caching stale bytes.
        if (!PROTOCOL_ONLY) {
            sendEvent(state.threadId, { type: "pi_error", message: "session file missing on agent_end" });
        }
        return;
    }
    try {
        const bytes = readFileSync(state.sessionPath, "utf8");
        if (typeof bytes !== "string" || bytes.length === 0) return;
        writeFrame({
            jsonrpc: "2.0",
            method: "event",
            params: { threadId: state.threadId, event: { type: "session_persisted", jsonl: bytes } },
        });
    } catch (e) {
        log(`emitSessionPersisted failed for thread=${state.threadId}: ${e?.message ?? e}`);
        sendEvent(state.threadId, { type: "pi_error", message: `session_persist_read_failed: ${e?.message ?? e}` });
    }
}

/** Post-turn major GC fires only above this heap watermark (requires Node --expose-gc). */
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
            // Synthesise terminal events so Java unblocks. Guard against double-fire: if the
            // SDK already emitted a real agent_end before rejecting (rare but observed under
            // abort+error races), `state.inFlight` is already false and the translator would
            // see a second Finish.
            if (!state.inFlight) return;
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

// Max characters of context surfaced to the LLM per fetch_context call. Aspect JSONs
// occasionally balloon (e.g. `findings.json` for a heavy reviewer); without a cap, a single
// tool call can blow the model's context window. 200 K chars ≈ 50 K tokens at ~4 chars/token —
// comfortably below gpt-oss-120b's 128 K window. Counted in JS string length (UTF-16 code
// units), not bytes; aspects are ASCII-dominant so the variance is small.
const FETCH_CONTEXT_MAX_CHARS = 200_000;

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
            const originalLength = text.length;
            let truncated = false;
            if (text.length > FETCH_CONTEXT_MAX_CHARS) {
                // Hard-cut the JSON; the marker rides on a separate content part so a model
                // that parses the first part as JSON never has to skip our truncation prose.
                text = text.slice(0, FETCH_CONTEXT_MAX_CHARS);
                truncated = true;
            }
            const parts = [{ type: "text", text }];
            if (truncated) {
                parts.push({
                    type: "text",
                    text: `[truncated ${originalLength - FETCH_CONTEXT_MAX_CHARS} chars from response]`,
                });
            }
            pending.resolve({
                content: parts,
                details: { ok: true, length: text.length, truncated, originalLength },
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
    // Thread closed between timer fire and queue drain — rebinding would leak a subscription.
    if (!threads.has(state.threadId)) {
        log(`watchdog rebind skipped: thread=${state.threadId} already closed`);
        return;
    }
    // The real `agent_end` raced ahead of us through the queue and cleared `inFlight`. Without
    // this guard we'd emit a SECOND synthetic `agent_end`, and Java's translator would Finish
    // the assistant message twice (or worse, finalise on a turn that already finalised).
    if (!state.inFlight) {
        log(`watchdog rebind skipped: thread=${state.threadId} turn already completed`);
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
    // open_thread + prompt would otherwise vanish into the log.
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
    // All stdin frames + side-effect rebinds funnel through `enqueue` to serialise
    // `runtime.switchSession` against itself.
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
    // EOF routes through the dispatch queue so SIGTERM and EOF run the same teardown.
    process.stdin.on("end", () => {
        log("stdin EOF — shutting down");
        enqueue(() => handleShutdown(undefined));
    });
    process.stdin.on("error", (e) => {
        log("stdin error:", e);
        // Distinct from ENVELOPE_MISMATCH_EXIT (42): this is a transport-layer failure, not a
        // structured protocol/image drift. Node default fatal exit code is 1.
        process.exit(1);
    });

    process.on("uncaughtException", (e) => {
        log("uncaughtException:", e);
        try {
            // Same rationale: uncaught is a runtime crash, not an envelope mismatch.
            process.stdout.write("", () => process.exit(1));
        } catch {
            process.exit(1);
        }
    });
    process.on("unhandledRejection", (e) => {
        log("unhandledRejection:", e);
    });

    // Signal-driven shutdown uses the same path as RPC `shutdown`. Pass `undefined` (NOT `null`)
    // so `sendResult` skips emitting a response frame for this synthetic shutdown.
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
