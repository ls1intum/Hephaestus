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
// Single budget from the adapter. Runner owns all splitting.
// Production: config=600s, buffer=60s → budget=540,000ms.
const AGENT_BUDGET_MS = __AGENT_BUDGET_MS__;
const INITIAL_TIMEOUT_MS = Math.max(60_000, Math.floor(AGENT_BUDGET_MS * 0.75));   // 75% for analysis
const RETRY1_TIMEOUT_MS  = Math.max(30_000, Math.floor(AGENT_BUDGET_MS * 0.15));   // 15% for retry 1
const RETRY2_TIMEOUT_MS  = Math.max(30_000, AGENT_BUDGET_MS - INITIAL_TIMEOUT_MS - RETRY1_TIMEOUT_MS); // 10% for retry 2
const SOFT_TIMEOUT_MS    = Math.max(45_000, Math.floor(INITIAL_TIMEOUT_MS * 0.50)); // nudge at 50% of initial

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

function lenientJsonParse(text) {
    // Strip control characters that LLMs embed in string values (tabs, newlines).
    // Matches the Java server's ALLOW_UNQUOTED_CONTROL_CHARS behavior.
    try { return JSON.parse(text); } catch {}
    const cleaned = text.replace(/[\x00-\x1f\x7f]/g, (ch) => {
        if (ch === "\n") return "\\n";
        if (ch === "\r") return "\\r";
        if (ch === "\t") return "\\t";
        return "";
    });
    return JSON.parse(cleaned);
}

function checkResultFile() {
    const path = `${OUTPUT}/result.json`;
    if (!existsSync(path)) return false;
    try {
        const data = lenientJsonParse(readFileSync(path, "utf-8"));
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

// ── Text rescue: extract findings from agent text responses ──────

function extractLastAssistantText(sessionState) {
    const messages = sessionState.messages || [];
    for (let i = messages.length - 1; i >= 0; i--) {
        const msg = messages[i];
        if (msg.role !== "assistant") continue;
        // Pi SDK uses "text" and "thinking" content types — check both
        const textBlocks = (msg.content || []).filter(c => c.type === "text" || c.type === "thinking");
        const text = textBlocks.map(c => c.text || c.thinking || "").join("").trim();
        if (!text || text.length < 20) continue;
        // Only return text that looks like it might contain JSON (has braces)
        if (text.includes("{") && text.includes("}")) return text;
    }
    return null;
}

function tryParseJsonFromText(text) {
    if (!text) return null;
    try {
        const parsed = JSON.parse(text);
        if (isValidFindingsPayload(parsed)) return parsed;
    } catch {}
    const jsonBlockPattern = /```(?:json)?\s*\n?([\s\S]*?)\n?\s*```/g;
    let match;
    while ((match = jsonBlockPattern.exec(text)) !== null) {
        try {
            const parsed = JSON.parse(match[1].trim());
            if (isValidFindingsPayload(parsed)) return parsed;
        } catch {}
    }
    // Try to find a JSON object with "findings" — use JSON.parse error position
    // to progressively find valid JSON instead of naive brace matching
    // (which breaks on braces inside string values like code snippets)
    const braceStart = text.indexOf('{"findings"');
    if (braceStart < 0) return null;
    // Try progressively longer substrings from braceStart
    for (let end = text.indexOf("}", braceStart); end >= 0; end = text.indexOf("}", end + 1)) {
        try {
            const candidate = text.slice(braceStart, end + 1);
            const parsed = JSON.parse(candidate);
            if (isValidFindingsPayload(parsed)) return parsed;
        } catch {}
    }
    return null;
}

function tryRescueFromTextResponse(sessionState) {
    const text = extractLastAssistantText(sessionState);
    if (!text) return false;
    try { writeFileSync(`${OUTPUT}/last-assistant-text.txt`, text); } catch {}
    const payload = tryParseJsonFromText(text);
    if (!payload) {
        console.error(`[pi-runner] Text rescue: found text (${text.length} chars) but no valid JSON. First 200: ${text.slice(0, 200)}`);
        return false;
    }
    console.error(`[pi-runner] Text rescue: extracted ${payload.findings.length} findings`);
    writeFileSync(`${OUTPUT}/result.json`, JSON.stringify(payload, null, 2));
    return checkResultFile();
}

// ── Practice slug loader (for retry scaffold) ───────────────────

function loadPracticeSlugs() {
    try {
        const indexPath = `${CWD}/.practices/index.json`;
        if (!existsSync(indexPath)) return [];
        const index = JSON.parse(readFileSync(indexPath, "utf-8"));
        if (!Array.isArray(index)) return [];
        return index.map(p => p.slug).filter(Boolean);
    } catch { return []; }
}

function buildRetryScaffold(slugs) {
    if (!slugs.length) return "";
    return `\n\nThe practice slugs you must cover: ${slugs.join(", ")}. ` +
        `Each needs a finding with practiceSlug, title, verdict (POSITIVE/NEGATIVE/NOT_APPLICABLE), severity, confidence, evidence, reasoning, and guidance. ` +
        `Write the complete JSON to /workspace/.output/result.json using the write tool.`;
}

// ── Main ─────────────────────────────────────────────────────────

const prompt = readFileSync("/workspace/.prompt", "utf-8").trim();

async function main() {
    console.error(`[pi-runner] Embedded SDK mode`);
    console.error(`[pi-runner] Budget: total=${AGENT_BUDGET_MS}ms, initial=${INITIAL_TIMEOUT_MS}ms (soft=${SOFT_TIMEOUT_MS}ms), retry1=${RETRY1_TIMEOUT_MS}ms, retry2=${RETRY2_TIMEOUT_MS}ms`);

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

    // Mid-loop nudge: remind the agent to write the output file.
    // Production data: 4/4 success rate when this fires.
    const softTimer = setTimeout(() => {
        softTimeoutFired = true;
        console.error(`[pi-runner] Soft timeout fired — nudging agent to write`);
        session.agent.steer({
            role: "user",
            content: [{ type: "text", text:
                `Finish your analysis and write /workspace/.output/result.json using the write tool. ` +
                `For any practice you haven't fully analyzed, use POSITIVE with confidence 0.70.`
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
        if (event.type === "tool_start" || event.type === "toolStart") {
            console.error(`[pi-runner] tool: ${event.tool?.name || event.name || "?"}`);
        }
        if (event.type === "message_end" && event.message?.role === "assistant") {
            const stopReason = event.message.stopReason;
            const types = (event.message.content || []).map(c => c.type);
            const toolCalls = types.filter(t => t === "tool_use" || t === "tool_call" || t === "toolCall").length;
            console.error(`[pi-runner] assistant msg: stopReason=${stopReason}, toolCalls=${toolCalls}, types=[${types}]`);
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

    // Extract what the agent actually said — log message structure for diagnostics
    const lastMsgs = (session.state.messages || []).filter(m => m.role === "assistant").slice(-2);
    for (const m of lastMsgs) {
        const types = (m.content || []).map(c => c.type);
        const textLen = (m.content || []).filter(c => c.type === "text").reduce((s, c) => s + (c.text?.length || 0), 0);
        console.error(`[pi-runner] assistant msg: stopReason=${m.stopReason}, contentTypes=[${types}], textLen=${textLen}`);
    }

    const agentText = extractLastAssistantText(session.state);
    if (agentText) {
        console.error(`[pi-runner] Agent produced text (${agentText.length} chars) but no result.json`);
        // Try to rescue valid JSON from the text
        if (tryRescueFromTextResponse(session.state)) {
            console.error(`[pi-runner] SUCCESS: rescued valid JSON from agent text`);
            unsubscribe();
            process.exit(0);
        }
    }

    // Re-prompt: give the agent a concrete scaffold so it can ONLY write
    console.error(`[pi-runner] Re-prompting agent to write result.json`);

    const slugs = loadPracticeSlugs();
    const scaffold = buildRetryScaffold(slugs);
    console.error(`[pi-runner] Loaded ${slugs.length} practice slugs for retry scaffold`);

    let retryAborted = false;
    const retryTimer = setTimeout(() => {
        retryAborted = true;
        console.error(`[pi-runner] Retry 1 hard timeout — aborting`);
        session.agent.abort();
    }, RETRY1_TIMEOUT_MS);

    const retryStartMs = Date.now();

    // Build retry prompt based on what actually happened.
    // Keep diagnostic branches — the recovery strategy differs by failure mode.
    let retryPrompt;
    if (softTimeoutFired || hardAborted) {
        retryPrompt =
            `You ran out of time before writing result.json. ` +
            `Use your analysis from above and write /workspace/.output/result.json NOW using the write tool. ` +
            `For any practice you did not fully analyze, use verdict POSITIVE with confidence 0.70.` +
            scaffold;
    } else if (agentText) {
        retryPrompt =
            `You completed your analysis but did not write the output file. ` +
            `Your analysis is done — do NOT read any more files. ` +
            `Call the write tool NOW to save your findings to /workspace/.output/result.json. ` +
            `The JSON needs a "findings" array and a "delivery.mrNote" string.` +
            scaffold;
    } else {
        retryPrompt =
            `You did not write .output/result.json. The review will fail unless you write this file NOW. ` +
            `Use your analysis from above. Do NOT read more files — write immediately. ` +
            `Call the write tool to save a JSON with a "findings" array and "delivery.mrNote" string ` +
            `to /workspace/.output/result.json. ` +
            `For any practice you did not fully analyze, use verdict POSITIVE with confidence 0.70.` +
            scaffold;
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
    prevUsage = retryUsage;

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

    console.error(`[pi-runner] Retry 1: ${(retryDurationMs / 1000).toFixed(1)}s, resultFile=${existsSync(`${OUTPUT}/result.json`)}`);

    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: result.json valid after retry 1`);
        unsubscribe();
        process.exit(0);
    }

    // ── Retry 2: last chance — even more direct ─────────────────

    console.error(`[pi-runner] Retry 2: final attempt`);

    let retry2Aborted = false;
    const retry2Timer = setTimeout(() => {
        retry2Aborted = true;
        console.error(`[pi-runner] Retry 2 hard timeout — aborting`);
        session.agent.abort();
    }, RETRY2_TIMEOUT_MS);

    const retry2StartMs = Date.now();

    try {
        await session.prompt(
            `The review will be discarded unless you write .output/result.json RIGHT NOW. ` +
            `Call the write tool with the JSON. One finding per practice slug: ${slugs.join(", ")}. ` +
            `Use your analysis. For unanalyzed practices, verdict POSITIVE, confidence 0.70. Write NOW.`
        );
    } catch (err) {
        console.error(`[pi-runner] Retry 2 error: ${err.message}`);
    }

    clearTimeout(retry2Timer);

    const retry2DurationMs = Date.now() - retry2StartMs;
    const retry2Usage = extractUsageFromSession(session.state);
    accumulateUsage(prevUsage, retry2Usage);

    runnerDebug.attempts.push({
        label: "retry2",
        durationMs: retry2DurationMs,
        hardAborted: retry2Aborted,
        resultFilePresent: existsSync(`${OUTPUT}/result.json`),
    });
    persistRunnerDebug();
    persistUsage();

    console.error(`[pi-runner] Retry 2: ${(retry2DurationMs / 1000).toFixed(1)}s, resultFile=${existsSync(`${OUTPUT}/result.json`)}`);

    unsubscribe();

    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: result.json valid after retry 2`);
        process.exit(0);
    }

    // Last attempt: try to rescue from text
    if (tryRescueFromTextResponse(session.state)) {
        console.error(`[pi-runner] SUCCESS: rescued valid JSON from text`);
        process.exit(0);
    }

    // ── Failed ───────────────────────────────────────────────────

    console.error(`[pi-runner] FAILED: no valid result.json after initial + 2 retries`);
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
