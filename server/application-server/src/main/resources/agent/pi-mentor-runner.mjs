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

const CWD = "/workspace";
const SESSIONS_DIR = "/workspace/.sessions";
const SYSTEM_PROMPT_PATH = "/workspace/agent/mentor/system.md";
// The SDK's `getAgentDir()` is the canonical default ("~/.pi/agent"). We resolve it lazily
// inside `ensureRuntime()` so the module loads without the SDK in protocol-only test mode.
const AGENT_DIR_OVERRIDE = process.env.PI_CODING_AGENT_DIR ?? null;
const PROTOCOL_VERSION = 1;

const FETCH_CONTEXT_TIMEOUT_MS = 10_000;
const TURN_BUDGET_MS = (() => {
    const raw = Number(process.env.MENTOR_TURN_BUDGET_MS);
    return Number.isFinite(raw) && raw > 0 ? raw : 120_000;
})();
const TURN_GRACE_MS = 30_000;

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

function sendResult(id, result) {
    if (id === undefined || id === null) return; // notification request — no response
    writeFrame({ jsonrpc: "2.0", id, result: result ?? null });
}

function sendError(id, code, message, data) {
    if (id === undefined || id === null) return;
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

async function ensureRuntime() {
    if (runtime) return runtime;
    if (runtimeInitPromise) return runtimeInitPromise;

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
        return await runtimeInitPromise;
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
        clearTurnWatchdog(state);
        state.inFlight = false;
    }
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

    // Append all replayed messages as system-note custom entries that DO participate in the LLM
    // context (per session-manager.d.ts §82-99 — appendCustomMessageEntry feeds buildSessionContext).
    // We intentionally do NOT re-prompt; the agent should treat replay as a recap of prior turns,
    // not as a fresh user instruction. The history shows what was said; the next `prompt` frame
    // drives the new turn.
    const sm = runtime.services?.sessionManager ?? runtime.session.sessionManager;
    try {
        if (sm && typeof sm.appendCustomMessageEntry === "function") {
            // Boundary note: makes it obvious to the LLM that the following turns are recap.
            sm.appendCustomMessageEntry(
                "hephaestus.replay_boundary",
                `Context replayed from previous turns — ${messages.length} message(s).`,
                /* display */ false,
            );
            for (const msg of messages) {
                const role = msg?.role === "assistant" ? "assistant" : "user";
                // Java owns the flattening: `text` is the pre-extracted concatenation of every
                // type==="text" entry in `parts`. We accept `parts` as a fallback so future
                // structured replay (tool calls, reasoning) can roll through without a server
                // ABI bump; today only text entries make it into the LLM-visible recap.
                const text = String(msg?.text ?? flattenPartsToText(msg?.parts)).trim();
                if (!text) continue;
                const tag = role === "assistant" ? "Assistant said" : "Student said";
                sm.appendCustomMessageEntry(`hephaestus.replay.${role}`, `${tag}: ${text}`, /* display */ false);
            }
        } else {
            // Degraded mode — see audit note at top of file. Document and continue.
            log("appendCustomMessageEntry unavailable; replay degraded to a no-op");
        }
        sendResult(id, { replayed: messages.length });
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
    state.watchdogTimer = setTimeout(async () => {
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
            if (runtime) {
                try {
                    await runtime.switchSession(state.sessionPath);
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
    }, TURN_BUDGET_MS + TURN_GRACE_MS);
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
    writeFrame({
        jsonrpc: "2.0",
        method: "event",
        params: {
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
    const splitter = createLineSplitter(async (line) => {
        let frame;
        try {
            frame = JSON.parse(line);
        } catch (e) {
            log(`parse error: ${e.message} (line len=${line.length})`);
            // We can't correlate; just drop.
            return;
        }
        try {
            await dispatch(frame);
        } catch (e) {
            log("dispatch failed:", e);
        }
    });

    process.stdin.on("data", (chunk) => splitter(chunk));
    process.stdin.on("end", () => {
        log("stdin EOF — shutting down");
        process.exit(0);
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

    announceReady();
}

start();
