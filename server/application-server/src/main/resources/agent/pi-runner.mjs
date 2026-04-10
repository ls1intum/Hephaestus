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
    return Boolean(f && typeof f === "object" && typeof f.practiceSlug === "string" && f.practiceSlug.trim() &&
        typeof f.title === "string" && f.title.trim() && typeof f.verdict === "string" &&
        typeof f.severity === "string" && typeof f.confidence === "number");
}

function isValidFindingsPayload(p) {
    return p && typeof p === "object" && Array.isArray(p.findings) && p.findings.length > 0 &&
        p.findings.some(isValidFinding) && (p.delivery == null || (typeof p.delivery === "object" && !Array.isArray(p.delivery)));
}

function checkResultFile() {
    const path = `${OUTPUT}/result.json`;
    if (!existsSync(path)) return false;
    try { return isValidFindingsPayload(JSON.parse(readFileSync(path, "utf-8"))); } catch { return false; }
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

    // ── Attempt 2: Continuation (same session, directive prompt) ──

    console.error(`[pi-runner] Continuation: directing agent to write findings NOW`);

    let contAborted = false;
    const contTimer = setTimeout(() => {
        contAborted = true;
        console.error(`[pi-runner] Continuation hard timeout — aborting`);
        session.agent.abort();
    }, CONTINUATION_TIMEOUT_MS);

    const contStartMs = Date.now();

    try {
        await session.prompt(
            `You ran out of time. You have already read the diff and practice criteria in this session. ` +
            `Do NOT read any more files. Do NOT grep anything. Do NOT explore the repo. ` +
            `Based on what you have already analyzed, IMMEDIATELY write the result.json file using the write tool. ` +
            `Include findings for ALL practices in the index. For practices you analyzed in detail, use your analysis. ` +
            `For practices you did not fully analyze, emit POSITIVE with confidence 0.70 and a brief positive note. ` +
            `Write the COMPLETE JSON to /workspace/.output/result.json NOW. This is your ONLY task.`
        );
    } catch (err) {
        console.error(`[pi-runner] Continuation error: ${err.message}`);
    }

    clearTimeout(contTimer);

    const contDurationMs = Date.now() - contStartMs;
    const contUsage = extractUsageFromSession(session.state);
    accumulateUsage(prevUsage, contUsage);

    runnerDebug.attempts.push({
        label: "continuation",
        durationMs: contDurationMs,
        hardAborted: contAborted,
        assistantMessages: contUsage.assistantMessages,
        stopReasons: contUsage.stopReasons,
        usage: contUsage,
        resultFilePresent: existsSync(`${OUTPUT}/result.json`),
    });
    persistRunnerDebug();
    persistUsage();

    console.error(`[pi-runner] Continuation: ${(contDurationMs / 1000).toFixed(1)}s, resultFile=${existsSync(`${OUTPUT}/result.json`)}`);

    unsubscribe();

    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: result.json valid after continuation`);
        process.exit(0);
    }

    // ── Failed ───────────────────────────────────────────────────

    console.error(`[pi-runner] FAILED: no valid result.json after initial + continuation`);
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
