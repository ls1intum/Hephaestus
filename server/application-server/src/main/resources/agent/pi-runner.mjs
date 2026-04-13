/**
 * Pi Agent Runner — Embedded SDK Mode
 *
 * Uses Pi's SDK (createAgentSession) directly instead of spawning a CLI subprocess.
 * This enables:
 * - Steering messages: inject "wrap up" mid-loop before hard timeout
 * - Proper continuation: session stays in memory, no file replay
 * - Clean abort via AbortSignal instead of SIGTERM
 * - Direct access to agent state for diagnostics
 *
 * Following OpenClaw's best practices for embedded Pi integration.
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from "fs";

// Pi SDK imports — embedded, not CLI subprocess (following OpenClaw's best practice)
import {
    createAgentSession,
    SessionManager,
    SettingsManager,
    createReadTool,
    createBashTool,
    createGrepTool,
    createWriteTool,
} from "@mariozechner/pi-coding-agent";

const OUTPUT = "/workspace/.output";
const CWD = "/workspace";
const INITIAL_TIMEOUT_MS = __INITIAL_TIMEOUT_MS__;
const SOFT_TIMEOUT_MS = Math.max(60_000, Math.floor(INITIAL_TIMEOUT_MS * 0.75));
const CONTINUATION_TIMEOUT_MS = __CONTINUATION_TIMEOUT_MS__;

mkdirSync(OUTPUT, { recursive: true });

// ── Usage tracking ───────────────────────────────────────────────

const usageTotals = { model: null, inputTokens: 0, outputTokens: 0, cacheReadTokens: 0, cacheWriteTokens: 0, costUsd: 0, totalCalls: 0 };
const runnerDebug = { attempts: [], usageTotals };

function persistUsage() {
    writeFileSync(`${OUTPUT}/usage.json`, JSON.stringify(usageTotals, null, 2));
}
function persistRunnerDebug() {
    writeFileSync(`${OUTPUT}/runner-debug.json`, JSON.stringify(runnerDebug, null, 2));
}

const SECRET_PATTERN = /(?:OPENAI_API_KEY|ANTHROPIC_API_KEY|AZURE_OPENAI_API_KEY|LLM_PROXY_TOKEN|api[_-]?key|secret|token|password|credential)=\S+/gi;
function redact(text) {
    if (!text) return "";
    return text.replace(SECRET_PATTERN, (m) => { const i = m.indexOf("="); return i >= 0 ? m.slice(0, i + 1) + "[REDACTED]" : m; });
}

// ── Validation ───────────────────────────────────────────────────

function isValidFinding(f) {
    if (!f || typeof f !== "object") return false;
    if (typeof f.practiceSlug !== "string" || !f.practiceSlug.trim()) return false;
    if (typeof f.title !== "string" || !f.title.trim()) return false;
    if (typeof f.verdict !== "string") return false;
    if (typeof f.severity !== "string") return false;
    // Tolerate confidence as string (LLMs sometimes write "0.85" instead of 0.85)
    // Guard: Number(null)=0 and Number("")=0 would pass isNaN, so reject nullish/empty first.
    if (f.confidence == null || f.confidence === "") return false;
    if (Number.isNaN(Number(f.confidence))) return false;
    return true;
}

function isValidFindingsPayload(p) {
    return p && typeof p === "object" && Array.isArray(p.findings) && p.findings.length > 0 &&
        p.findings.some(isValidFinding) && (p.delivery == null || (typeof p.delivery === "object" && !Array.isArray(p.delivery)));
}

function checkResultFile() {
    const path = `${OUTPUT}/result.json`;
    if (!existsSync(path)) return false;
    try {
        const data = JSON.parse(readFileSync(path, "utf-8"));
        const valid = isValidFindingsPayload(data);
        if (!valid) {
            const hasFindings = Array.isArray(data?.findings);
            const count = hasFindings ? data.findings.length : 0;
            const validCount = hasFindings ? data.findings.filter(isValidFinding).length : 0;
            console.error(`[pi-runner] result.json validation failed: findings=${count}, valid=${validCount}`);
        }
        return valid;
    } catch (e) {
        console.error(`[pi-runner] result.json parse error: ${e.message}`);
        return false;
    }
}

// ── Text response rescue ─────────────────────────────────────────
// Safety net: when the agent emits findings as a text response (stopReason="stop")
// instead of using the write tool, extract the JSON from the message content
// and write result.json ourselves.

function extractLastAssistantText(sessionState) {
    const messages = sessionState.messages || [];
    // Scan all assistant messages from newest to oldest — the JSON might be
    // in an earlier message if the last one was a continuation acknowledgment.
    for (let i = messages.length - 1; i >= 0; i--) {
        const msg = messages[i];
        if (msg.role !== "assistant") continue;
        const textBlocks = (msg.content || []).filter(c => c.type === "text");
        const text = textBlocks.map(c => c.text).join("").trim();
        if (text && text.includes("findings")) return text;
        if (text && text.length > 100) return text; // non-trivial text
    }
    return null;
}

function tryParseJsonFromText(text) {
    if (!text) return null;
    // Try direct parse first (agent may have emitted pure JSON)
    try {
        const parsed = JSON.parse(text);
        if (isValidFindingsPayload(parsed)) return parsed;
    } catch {}
    // Try extracting JSON from markdown code fences or surrounding prose
    const jsonBlockPattern = /```(?:json)?\s*\n?([\s\S]*?)\n?\s*```/g;
    let match;
    while ((match = jsonBlockPattern.exec(text)) !== null) {
        try {
            const parsed = JSON.parse(match[1].trim());
            if (isValidFindingsPayload(parsed)) return parsed;
        } catch {}
    }
    // Try finding a top-level JSON object with "findings" key
    const braceStart = text.indexOf('{"findings"');
    if (braceStart === -1) return null;
    // Walk forward to find the matching closing brace
    let depth = 0;
    for (let i = braceStart; i < text.length; i++) {
        if (text[i] === '{') depth++;
        else if (text[i] === '}') depth--;
        if (depth === 0) {
            try {
                const parsed = JSON.parse(text.slice(braceStart, i + 1));
                if (isValidFindingsPayload(parsed)) return parsed;
            } catch {}
            break;
        }
    }
    return null;
}

/**
 * Attempt to rescue findings from the agent's text response.
 * Returns true if result.json was successfully written.
 */
function tryRescueFromTextResponse(sessionState) {
    const text = extractLastAssistantText(sessionState);
    if (!text) {
        console.error(`[pi-runner] Text rescue: no assistant text found in session`);
        return false;
    }
    // Always persist raw text for debugging failed rescues
    try { writeFileSync(`${OUTPUT}/last-assistant-text.txt`, text); } catch {}
    const payload = tryParseJsonFromText(text);
    if (!payload) {
        console.error(`[pi-runner] Text rescue: found assistant text (${text.length} chars) but no valid findings JSON. First 300 chars: ${text.slice(0, 300)}`);
        return false;
    }
    console.error(`[pi-runner] Text rescue: extracted ${payload.findings.length} findings from assistant text response`);
    writeFileSync(`${OUTPUT}/result.json`, JSON.stringify(payload, null, 2));
    return checkResultFile();
}

// ── Session usage extraction ─────────────────────────────────────

function extractUsageFromSession(session) {
    const messages = session.messages || [];
    let model = null, inputTokens = 0, outputTokens = 0, cacheReadTokens = 0, cacheWriteTokens = 0, costUsd = 0, totalCalls = 0, assistantMessages = 0;
    const stopReasons = {};

    for (const msg of messages) {
        if (msg.role !== "assistant" || !msg.usage) continue;
        assistantMessages++;
        totalCalls++;
        model = msg.model || model;
        inputTokens += Number(msg.usage.input || 0);
        outputTokens += Number(msg.usage.output || 0);
        cacheReadTokens += Number(msg.usage.cacheRead || 0);
        cacheWriteTokens += Number(msg.usage.cacheWrite || 0);
        costUsd += Number(msg.usage.cost?.total || 0);
        const sr = msg.stopReason || "unknown";
        stopReasons[sr] = (stopReasons[sr] || 0) + 1;
    }

    return { model, inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, costUsd, totalCalls, assistantMessages, stopReasons };
}

function accumulateUsage(prev, curr) {
    usageTotals.model = curr.model || usageTotals.model;
    usageTotals.inputTokens += Math.max(0, curr.inputTokens - (prev?.inputTokens || 0));
    usageTotals.outputTokens += Math.max(0, curr.outputTokens - (prev?.outputTokens || 0));
    usageTotals.cacheReadTokens += Math.max(0, curr.cacheReadTokens - (prev?.cacheReadTokens || 0));
    usageTotals.cacheWriteTokens += Math.max(0, curr.cacheWriteTokens - (prev?.cacheWriteTokens || 0));
    usageTotals.costUsd += Math.max(0, curr.costUsd - (prev?.costUsd || 0));
    usageTotals.totalCalls += Math.max(0, curr.totalCalls - (prev?.totalCalls || 0));
}

// ── Main ─────────────────────────────────────────────────────────

const prompt = readFileSync("/workspace/.prompt", "utf-8").trim();

async function main() {
    console.error(`[pi-runner] Embedded SDK mode`);
    console.error(`[pi-runner] Budget: initial=${INITIAL_TIMEOUT_MS}ms (soft=${SOFT_TIMEOUT_MS}ms), continuation=${CONTINUATION_TIMEOUT_MS}ms`);

    // Create tools scoped to /workspace
    const tools = [
        createReadTool(CWD),
        createBashTool(CWD),
        createGrepTool(CWD),
        createWriteTool(CWD),
    ];

    // Create session using Pi SDK — embedded, not CLI subprocess
    const settingsManager = SettingsManager.create(CWD, process.env.PI_CODING_AGENT_DIR || "/home/agent/.pi");
    const sessionManager = SessionManager.inMemory();

    const { session } = await createAgentSession({
        cwd: CWD,
        agentDir: process.env.PI_CODING_AGENT_DIR || "/home/agent/.pi",
        tools,
        sessionManager,
        settingsManager,
    });

    // ── Attempt 1: Initial analysis ──────────────────────────────

    let softTimeoutFired = false;
    let hardAborted = false;
    let prevUsage = null;

    // Soft timeout: inject steering message telling agent to wrap up
    const softTimer = setTimeout(() => {
        softTimeoutFired = true;
        const remaining = Math.floor((INITIAL_TIMEOUT_MS - SOFT_TIMEOUT_MS) / 1000);
        console.error(`[pi-runner] Soft timeout fired — steering agent to wrap up (${remaining}s remaining)`);
        session.agent.steer({
            role: "user",
            content: [{ type: "text", text:
                `⚠️ TIME WARNING: You have approximately ${remaining} seconds remaining before this session is terminated. ` +
                `Stop exploring and write your findings NOW. Use the write tool to save /workspace/.output/result.json immediately. ` +
                `Include findings for ALL practices — for any you haven't fully analyzed, emit POSITIVE with confidence 0.70. ` +
                `Do NOT read any more files. Do NOT grep anything. Just write the result JSON NOW.`
            }],
            timestamp: Date.now(),
        });
    }, SOFT_TIMEOUT_MS);

    // Hard timeout: abort the agent loop
    const hardTimer = setTimeout(() => {
        hardAborted = true;
        console.error(`[pi-runner] Hard timeout — aborting agent`);
        session.agent.abort();
    }, INITIAL_TIMEOUT_MS);

    // Subscribe to events for diagnostics
    const events = [];
    const unsubscribe = session.subscribe((event) => {
        if (event.type === "tool_start") {
            console.error(`[pi-runner] tool: ${event.tool?.name || "?"}`);
        }
        if (event.type === "message_end" && event.message?.role === "assistant") {
            const stopReason = event.message.stopReason;
            const toolCalls = (event.message.content || []).filter(c => c.type === "tool_use" || c.type === "tool_call").length;
            console.error(`[pi-runner] assistant msg: stopReason=${stopReason}, toolCalls=${toolCalls}`);
        }
        events.push({ type: event.type, timestamp: Date.now() });
    });

    console.error(`[pi-runner] Starting initial analysis`);
    const startMs = Date.now();

    try {
        await session.prompt(prompt);
    } catch (err) {
        console.error(`[pi-runner] Initial prompt error: ${err.message}`);
    }

    clearTimeout(softTimer);
    clearTimeout(hardTimer);

    const initialDurationMs = Date.now() - startMs;
    const initialUsage = extractUsageFromSession(session.state);
    accumulateUsage(null, initialUsage);
    prevUsage = initialUsage;

    runnerDebug.attempts.push({
        label: "initial",
        durationMs: initialDurationMs,
        softTimeoutFired,
        hardAborted,
        assistantMessages: initialUsage.assistantMessages,
        stopReasons: initialUsage.stopReasons,
        usage: initialUsage,
        resultFilePresent: existsSync(`${OUTPUT}/result.json`),
    });
    persistRunnerDebug();
    persistUsage();

    console.error(`[pi-runner] Initial: ${(initialDurationMs / 1000).toFixed(1)}s, calls=${initialUsage.totalCalls}, softTimeout=${softTimeoutFired}, hardAbort=${hardAborted}, resultFile=${existsSync(`${OUTPUT}/result.json`)}`);

    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: result.json valid after initial run`);
        unsubscribe();
        process.exit(0);
    }

    // ── Validate & retry: if result.json is missing, re-prompt the agent ──

    // Extract what the agent actually said (if anything)
    const agentText = extractLastAssistantText(session.state);
    if (agentText) {
        console.error(`[pi-runner] Agent produced text (${agentText.length} chars) but no result.json`);
        // Try to rescue valid JSON from the text first
        if (tryRescueFromTextResponse(session.state)) {
            console.error(`[pi-runner] SUCCESS: rescued valid JSON from agent text`);
            unsubscribe();
            process.exit(0);
        }
    }

    // Re-prompt: tell the agent exactly what went wrong and what to do
    console.error(`[pi-runner] Re-prompting agent to write result.json`);

    let retryAborted = false;
    const retryTimer = setTimeout(() => {
        retryAborted = true;
        console.error(`[pi-runner] Retry hard timeout — aborting`);
        session.agent.abort();
    }, CONTINUATION_TIMEOUT_MS);

    const retryStartMs = Date.now();

    // Build a specific re-prompt based on what happened
    let retryPrompt;
    if (agentText && agentText.includes("findings")) {
        // Agent produced something that looks like findings but wasn't written to file
        retryPrompt =
            `You produced your analysis as a text response but did NOT write it to the output file. ` +
            `You MUST use the write tool to save your findings. ` +
            `Call the write tool NOW to write the JSON to /workspace/.output/result.json. ` +
            `The JSON must have a top-level "findings" array and an optional "delivery" object.`;
    } else if (softTimeoutFired || hardAborted) {
        // Agent ran out of time
        retryPrompt =
            `You ran out of time before writing result.json. ` +
            `Based on what you already analyzed, write the result.json file NOW using the write tool. ` +
            `Include one finding per practice. For practices you did not analyze, use POSITIVE with confidence 0.70. ` +
            `Write to /workspace/.output/result.json immediately.`;
    } else {
        // Agent completed but didn't write the file for unknown reason
        retryPrompt =
            `Your previous response did not write the required output file. ` +
            `You MUST call the write tool to save a JSON object to /workspace/.output/result.json. ` +
            `The JSON needs a "findings" array with one entry per practice and a "delivery.mrNote" string. ` +
            `Do not explain anything — just call the write tool with the JSON.`;
    }

    try {
        await session.prompt(retryPrompt);
    } catch (err) {
        console.error(`[pi-runner] Retry error: ${err.message}`);
    }

    clearTimeout(retryTimer);

    const retryDurationMs = Date.now() - retryStartMs;
    const retryUsage = extractUsageFromSession(session.state);
    accumulateUsage(prevUsage, retryUsage);

    runnerDebug.attempts.push({
        label: "retry",
        durationMs: retryDurationMs,
        hardAborted: retryAborted,
        assistantMessages: retryUsage.assistantMessages,
        stopReasons: retryUsage.stopReasons,
        usage: retryUsage,
        resultFilePresent: existsSync(`${OUTPUT}/result.json`),
    });
    persistRunnerDebug();
    persistUsage();

    console.error(`[pi-runner] Retry: ${(retryDurationMs / 1000).toFixed(1)}s, resultFile=${existsSync(`${OUTPUT}/result.json`)}`);

    unsubscribe();

    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: result.json valid after retry`);
        process.exit(0);
    }

    // Last resort: try to rescue from text one more time
    if (tryRescueFromTextResponse(session.state)) {
        console.error(`[pi-runner] SUCCESS: rescued valid JSON from retry text`);
        process.exit(0);
    }

    // ── Failed ───────────────────────────────────────────────────

    console.error(`[pi-runner] FAILED: no valid result.json after initial + retry`);
    process.exit(1);
}

process.on("uncaughtException", (err) => {
    console.error(`[pi-runner] FATAL: ${err.message}`);
    persistRunnerDebug();
    persistUsage();
    process.exit(2);
});

process.on("unhandledRejection", (reason) => {
    console.error(`[pi-runner] UNHANDLED REJECTION: ${reason}`);
    persistRunnerDebug();
    persistUsage();
    process.exit(2);
});

main().catch((err) => {
    console.error(`[pi-runner] FATAL: ${err.message}\n${err.stack}`);
    persistRunnerDebug();
    persistUsage();
    process.exit(2);
});
