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
    defineTool,
    createReadTool,
    createBashTool,
    createGrepTool,
} from "@mariozechner/pi-coding-agent";

const OUTPUT = "/workspace/.output";
const CWD = "/workspace";
const RESULT_PATH = `${OUTPUT}/result.json`;
const REVIEW_STATE_PATH = `${OUTPUT}/review-state.json`;
// Single budget from the adapter. Runner owns all splitting.
// Production: config=600s, buffer=60s → budget=540,000ms.
const AGENT_BUDGET_MS = __AGENT_BUDGET_MS__;
const INITIAL_TIMEOUT_MS = Math.max(60_000, Math.floor(AGENT_BUDGET_MS * 0.75)); // 75% for analysis
const RETRY1_TIMEOUT_MS = Math.max(30_000, Math.floor(AGENT_BUDGET_MS * 0.15)); // 15% for retry 1
const RETRY2_TIMEOUT_MS = Math.max(30_000, AGENT_BUDGET_MS - INITIAL_TIMEOUT_MS - RETRY1_TIMEOUT_MS); // 10% for retry 2
const SOFT_TIMEOUT_MS = Math.max(45_000, Math.floor(INITIAL_TIMEOUT_MS * 0.5)); // nudge at 50% of initial

mkdirSync(OUTPUT, { recursive: true });

// ── Usage tracking ───────────────────────────────────────────────

const usageTotals = {
    model: null,
    inputTokens: 0,
    outputTokens: 0,
    cacheReadTokens: 0,
    cacheWriteTokens: 0,
    costUsd: 0,
    totalCalls: 0,
};
const runnerDebug = { attempts: [], usageTotals };
const reviewState = {
    findings: [],
    findingKeys: [],
    delivery: { mrNote: null },
};
const verdictSchema = { type: "string", enum: ["POSITIVE", "NEGATIVE", "NOT_APPLICABLE"] };
const severitySchema = { type: "string", enum: ["CRITICAL", "MAJOR", "MINOR", "INFO"] };
const evidenceSchema = {
    type: "object",
    additionalProperties: false,
    required: ["locations", "snippets"],
    properties: {
        locations: {
            type: "array",
            items: {
                type: "object",
                additionalProperties: false,
                required: ["path", "startLine", "endLine"],
                properties: {
                    path: { type: "string", minLength: 1 },
                    startLine: { type: "integer", minimum: 1 },
                    endLine: { type: "integer", minimum: 1 },
                },
            },
        },
        snippets: { type: "array", items: { type: "string" } },
    },
};
const diffNoteSchema = {
    type: "object",
    additionalProperties: false,
    required: ["filePath", "startLine", "endLine", "body"],
    properties: {
        filePath: { type: "string", minLength: 1 },
        startLine: { type: "integer", minimum: 1 },
        endLine: { type: "integer", minimum: 1 },
        body: { type: "string", minLength: 1 },
    },
};
const findingSchema = {
    type: "object",
    additionalProperties: false,
    required: ["practiceSlug", "title", "verdict", "severity", "confidence", "evidence", "reasoning", "guidance"],
    properties: {
        practiceSlug: { type: "string", minLength: 1 },
        title: { type: "string", minLength: 1, maxLength: 120 },
        verdict: verdictSchema,
        severity: severitySchema,
        confidence: { type: "number", minimum: 0, maximum: 1 },
        evidence: evidenceSchema,
        reasoning: { type: "string", minLength: 1 },
        guidance: { type: "string", minLength: 1 },
        suggestedDiffNotes: { type: "array", items: diffNoteSchema },
    },
};

function persistUsage() {
    writeFileSync(`${OUTPUT}/usage.json`, JSON.stringify(usageTotals, null, 2));
}
function persistRunnerDebug() {
    writeFileSync(`${OUTPUT}/runner-debug.json`, JSON.stringify(runnerDebug, null, 2));
}
function persistReviewState() {
    writeFileSync(
        REVIEW_STATE_PATH,
        JSON.stringify(
            {
                findings: reviewState.findings,
                delivery: reviewState.delivery,
            },
            null,
            2,
        ),
    );
}

const SECRET_PATTERN =
    /(?:OPENAI_API_KEY|ANTHROPIC_API_KEY|AZURE_OPENAI_API_KEY|LLM_PROXY_TOKEN|api[_-]?key|secret|token|password|credential)=\S+/gi;
function redact(text) {
    if (!text) return "";
    return text.replace(SECRET_PATTERN, (m) => {
        const i = m.indexOf("=");
        return i >= 0 ? m.slice(0, i + 1) + "[REDACTED]" : m;
    });
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
    return (
        p &&
        typeof p === "object" &&
        Array.isArray(p.findings) &&
        p.findings.length > 0 &&
        p.findings.some(isValidFinding) &&
        (p.delivery == null || (typeof p.delivery === "object" && !Array.isArray(p.delivery)))
    );
}

function lenientJsonParse(text) {
    // Strip control characters that LLMs embed in string values (tabs, newlines).
    // Matches the Java server's ALLOW_UNQUOTED_CONTROL_CHARS behavior.
    try {
        return JSON.parse(text);
    } catch {}
    const cleaned = text.replace(new RegExp("[\\u0000-\\u001F\\u007F]", "g"), (ch) => {
        if (ch === "\n") return "\\n";
        if (ch === "\r") return "\\r";
        if (ch === "\t") return "\\t";
        return "";
    });
    return JSON.parse(cleaned);
}

function checkResultFile() {
    if (!existsSync(RESULT_PATH)) return false;
    try {
        const data = lenientJsonParse(readFileSync(RESULT_PATH, "utf-8"));
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

function maybeWriteResultFile() {
    const mrNote = typeof reviewState.delivery.mrNote === "string" ? reviewState.delivery.mrNote.trim() : "";
    if (!mrNote || reviewState.findings.length === 0) return false;
    writeFileSync(
        RESULT_PATH,
        JSON.stringify(
            {
                findings: reviewState.findings,
                delivery: { mrNote },
            },
            null,
            2,
        ),
    );
    return true;
}

function hasPersistedReviewState() {
    return reviewState.findings.length > 0 || Boolean(reviewState.delivery.mrNote?.trim());
}

function normalizeDiffNote(note) {
    if (!note || typeof note !== "object") throw new Error("diff note must be an object");
    const filePath = String(note.filePath ?? "").trim();
    const startLine = Number(note.startLine);
    const endLine = note.endLine == null ? startLine : Number(note.endLine);
    const body = String(note.body ?? "").trim();
    if (!filePath) throw new Error("diff note filePath is required");
    if (!Number.isInteger(startLine) || startLine <= 0)
        throw new Error("diff note startLine must be a positive integer");
    if (!Number.isInteger(endLine) || endLine < startLine) throw new Error("diff note endLine must be >= startLine");
    if (!body) throw new Error("diff note body is required");
    return { filePath, startLine, endLine, body };
}

function normalizeEvidence(evidence) {
    const locations = Array.isArray(evidence?.locations)
        ? evidence.locations.map((location) => {
              const path = String(location?.path ?? "").trim();
              const startLine = Number(location?.startLine);
              const endLine = location?.endLine == null ? startLine : Number(location.endLine);
              if (!path) throw new Error("evidence location path is required");
              if (!Number.isInteger(startLine) || startLine <= 0)
                  throw new Error("evidence startLine must be a positive integer");
              if (!Number.isInteger(endLine) || endLine < startLine)
                  throw new Error("evidence endLine must be >= startLine");
              return { path, startLine, endLine };
          })
        : [];
    const snippets = Array.isArray(evidence?.snippets)
        ? evidence.snippets.map((snippet) => String(snippet ?? "")).filter((snippet) => snippet.trim().length > 0)
        : [];
    return { locations, snippets };
}

function normalizeFinding(finding) {
    if (!finding || typeof finding !== "object") throw new Error("finding must be an object");
    const practiceSlug = String(finding.practiceSlug ?? "").trim();
    const title = String(finding.title ?? "").trim();
    const verdict = String(finding.verdict ?? "").trim();
    const severity = String(finding.severity ?? "").trim();
    const confidence = Number(finding.confidence);
    const reasoning = String(finding.reasoning ?? "").trim();
    const guidance = String(finding.guidance ?? "").trim();
    if (!practiceSlug) throw new Error("practiceSlug is required");
    if (!title) throw new Error("title is required");
    if (!["POSITIVE", "NEGATIVE", "NOT_APPLICABLE"].includes(verdict)) throw new Error(`invalid verdict '${verdict}'`);
    if (!["CRITICAL", "MAJOR", "MINOR", "INFO"].includes(severity)) throw new Error(`invalid severity '${severity}'`);
    if (!Number.isFinite(confidence) || confidence < 0 || confidence > 1)
        throw new Error("confidence must be between 0 and 1");
    if (!reasoning) throw new Error("reasoning is required");
    if (!guidance) throw new Error("guidance is required");
    const evidence = normalizeEvidence(finding.evidence);
    const suggestedDiffNotes = Array.isArray(finding.suggestedDiffNotes)
        ? finding.suggestedDiffNotes.map(normalizeDiffNote)
        : [];
    return { practiceSlug, title, verdict, severity, confidence, evidence, reasoning, guidance, suggestedDiffNotes };
}

function dedupeKeyForFinding(finding) {
    return JSON.stringify(finding);
}

function appendFindings(findings) {
    let inserted = 0;
    let duplicates = 0;
    const seen = new Set(reviewState.findingKeys);
    for (const rawFinding of findings) {
        const finding = normalizeFinding(rawFinding);
        const key = dedupeKeyForFinding(finding);
        if (seen.has(key)) {
            duplicates++;
            continue;
        }
        seen.add(key);
        reviewState.findingKeys.push(key);
        reviewState.findings.push(finding);
        inserted++;
    }
    persistReviewState();
    maybeWriteResultFile();
    return { inserted, duplicates };
}

const reportFindingTool = defineTool({
    name: "report_finding",
    label: "Report Finding",
    description:
        "Persist exactly one structured finding immediately so it survives retries and timeouts. Call this as soon as one finding is ready. Do not wait to batch findings.",
    parameters: {
        type: "object",
        additionalProperties: false,
        required: ["finding"],
        properties: {
            finding: findingSchema,
        },
    },
    execute: async (_toolCallId, params) => {
        const { inserted, duplicates } = appendFindings([params.finding]);
        const negativeCount = params.finding.verdict === "NEGATIVE" ? 1 : 0;
        return {
            content: [
                {
                    type: "text",
                    text: `Stored ${inserted} finding${duplicates > 0 ? ` (${duplicates} duplicate skipped)` : ""}. Negative findings in this call: ${negativeCount}.`,
                },
            ],
            details: { inserted, duplicates, totalFindings: reviewState.findings.length },
        };
    },
});

const setReviewSummaryTool = defineTool({
    name: "set_review_summary",
    label: "Set Review Summary",
    description:
        "Persist the final delivery.mrNote markdown summary for the merge request comment. Keep it concise and call this once the review findings are already persisted.",
    parameters: {
        type: "object",
        additionalProperties: false,
        required: ["mrNote"],
        properties: {
            mrNote: { type: "string", minLength: 1, maxLength: 60000 },
        },
    },
    execute: async (_toolCallId, params) => {
        reviewState.delivery.mrNote = String(params.mrNote ?? "").trim();
        persistReviewState();
        const wroteResult = maybeWriteResultFile();
        return {
            content: [
                {
                    type: "text",
                    text: wroteResult
                        ? `Stored review summary and wrote ${RESULT_PATH}.`
                        : "Stored review summary. Result file will be written after at least one finding is reported.",
                },
            ],
            details: { wroteResult, totalFindings: reviewState.findings.length },
        };
    },
});

// ── Session usage extraction ─────────────────────────────────────

function extractUsageFromSession(session) {
    const messages = session.messages || [];
    let model = null,
        inputTokens = 0,
        outputTokens = 0,
        cacheReadTokens = 0,
        cacheWriteTokens = 0,
        costUsd = 0,
        totalCalls = 0,
        assistantMessages = 0;
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

    return {
        model,
        inputTokens,
        outputTokens,
        cacheReadTokens,
        cacheWriteTokens,
        costUsd,
        totalCalls,
        assistantMessages,
        stopReasons,
    };
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
        const textBlocks = (msg.content || []).filter((c) => c.type === "text" || c.type === "thinking");
        const text = textBlocks
            .map((c) => c.text || c.thinking || "")
            .join("")
            .trim();
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
    let match = jsonBlockPattern.exec(text);
    while (match !== null) {
        try {
            const parsed = JSON.parse(match[1].trim());
            if (isValidFindingsPayload(parsed)) return parsed;
        } catch {}
        match = jsonBlockPattern.exec(text);
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
    try {
        writeFileSync(`${OUTPUT}/last-assistant-text.txt`, text);
    } catch {}
    const payload = tryParseJsonFromText(text);
    if (!payload) {
        console.error(
            `[pi-runner] Text rescue: found text (${text.length} chars) but no valid JSON. First 200: ${text.slice(0, 200)}`,
        );
        return false;
    }
    console.error(`[pi-runner] Text rescue: extracted ${payload.findings.length} findings`);
    writeFileSync(RESULT_PATH, JSON.stringify(payload, null, 2));
    return checkResultFile();
}

// ── Practice slug loader (for retry scaffold) ───────────────────

function loadPracticeSlugs() {
    try {
        const indexPath = `${CWD}/.practices/index.json`;
        if (!existsSync(indexPath)) return [];
        const index = JSON.parse(readFileSync(indexPath, "utf-8"));
        if (!Array.isArray(index)) return [];
        return index.map((p) => p.slug).filter(Boolean);
    } catch {
        return [];
    }
}

function buildRetryScaffold(slugs) {
    if (!slugs.length) return "";
    return (
        `\n\nThe practice slugs you must cover: ${slugs.join(", ")}. ` +
        `Persist every justified finding with report_finding, one finding per call. ` +
        `There is no target count and no quota. ` +
        `Only report POSITIVE findings that add real review value. ` +
        `Do not emit derivative low-signal findings when a stronger root-cause finding already covers the problem. ` +
        `Then persist the final MR summary with set_review_summary.`
    );
}

// ── Main ─────────────────────────────────────────────────────────

const prompt = readFileSync("/workspace/.prompt", "utf-8").trim();

async function main() {
    console.error(`[pi-runner] Embedded SDK mode`);
    console.error(
        `[pi-runner] Budget: total=${AGENT_BUDGET_MS}ms, initial=${INITIAL_TIMEOUT_MS}ms (soft=${SOFT_TIMEOUT_MS}ms), retry1=${RETRY1_TIMEOUT_MS}ms, retry2=${RETRY2_TIMEOUT_MS}ms`,
    );

    // Create tools scoped to /workspace
    const tools = [createReadTool(CWD), createBashTool(CWD), createGrepTool(CWD)];

    // Create session using Pi SDK — embedded, not CLI subprocess
    const settingsManager = SettingsManager.create(CWD, process.env.PI_CODING_AGENT_DIR || "/home/agent/.pi");
    const sessionManager = SessionManager.inMemory();

    const { session } = await createAgentSession({
        cwd: CWD,
        agentDir: process.env.PI_CODING_AGENT_DIR || "/home/agent/.pi",
        tools,
        customTools: [reportFindingTool, setReviewSummaryTool],
        sessionManager,
        settingsManager,
    });

    // ── Attempt 1: Initial analysis ──────────────────────────────

    let softTimeoutFired = false;
    let hardAborted = false;
    let prevUsage = null;

    // Mid-loop nudge: stop analysis early enough to persist durable review state.
    // Production data: 4/4 success rate when this fires.
    const softTimer = setTimeout(() => {
        softTimeoutFired = true;
        console.error(`[pi-runner] Soft timeout fired — nudging agent to persist review state`);
        session.agent.steer({
            role: "user",
            content: [
                {
                    type: "text",
                    text:
                        `Stop analyzing and persist output now. ` +
                        `Use report_finding immediately for any finding you already have, one finding per call. ` +
                        `There is no target count and no quota. ` +
                        `When reading files for initial context, batch independent reads and greps in parallel when the runtime supports it. ` +
                        `For POSITIVE or NOT_APPLICABLE findings, guidance can simply be \"No change needed.\" ` +
                        `Only keep POSITIVE findings that add real review value. ` +
                        `Do not add derivative low-signal findings when a stronger finding already covers the problem. ` +
                        `Then call set_review_summary exactly once. ` +
                        `Use tools only from this point onward. Do not write planning prose or plain-text commentary.`,
                },
            ],
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
            const types = (event.message.content || []).map((c) => c.type);
            const toolCalls = types.filter((t) => t === "tool_use" || t === "tool_call" || t === "toolCall").length;
            console.error(
                `[pi-runner] assistant msg: stopReason=${stopReason}, toolCalls=${toolCalls}, types=[${types}]`,
            );
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
        resultFilePresent: existsSync(RESULT_PATH),
    });
    persistRunnerDebug();
    persistUsage();

    console.error(
        `[pi-runner] Initial: ${(initialDurationMs / 1000).toFixed(1)}s, calls=${initialUsage.totalCalls}, softTimeout=${softTimeoutFired}, hardAbort=${hardAborted}, resultFile=${existsSync(RESULT_PATH)}, reviewState=${hasPersistedReviewState()}`,
    );

    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: result.json valid after initial run`);
        unsubscribe();
        process.exit(0);
    }

    maybeWriteResultFile();
    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: composed result.json from persisted tool state after initial run`);
        unsubscribe();
        process.exit(0);
    }

    // ── Validate & retry: if durable state is incomplete, re-prompt the agent ──

    // Extract what the agent actually said — log message structure for diagnostics
    const lastMsgs = (session.state.messages || []).filter((m) => m.role === "assistant").slice(-2);
    for (const m of lastMsgs) {
        const types = (m.content || []).map((c) => c.type);
        const textLen = (m.content || [])
            .filter((c) => c.type === "text")
            .reduce((s, c) => s + (c.text?.length || 0), 0);
        console.error(
            `[pi-runner] assistant msg: stopReason=${m.stopReason}, contentTypes=[${types}], textLen=${textLen}`,
        );
    }

    const agentText = extractLastAssistantText(session.state);
    if (agentText) {
        console.error(
            `[pi-runner] Agent produced text (${agentText.length} chars) but did not persist complete review output`,
        );
        // Try to rescue valid JSON from the text
        if (tryRescueFromTextResponse(session.state)) {
            console.error(`[pi-runner] SUCCESS: rescued valid JSON from agent text`);
            unsubscribe();
            process.exit(0);
        }
    }

    // Re-prompt: give the agent a concrete scaffold so it only persists remaining review state.
    console.error(`[pi-runner] Re-prompting agent to persist remaining review output`);

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
            `You ran out of time before finalizing the review. ` +
            `Do NOT restart analysis from scratch. Do NOT read more files. ` +
            `Persist every remaining justified finding with report_finding immediately, one finding per call. ` +
            `There is no target count and no quota. ` +
            `For POSITIVE or NOT_APPLICABLE findings, guidance can simply be \"No change needed.\" ` +
            `Only keep POSITIVE findings that add real review value. ` +
            `Do not add derivative low-signal findings when a stronger finding already covers the problem. ` +
            `Then call set_review_summary exactly once with the MR note. ` +
            `Use tools only from this point onward. Do not write planning prose or plain-text commentary. ` +
            scaffold;
    } else if (agentText) {
        retryPrompt =
            `You completed analysis but did not persist the final review output. ` +
            `Do NOT read any more files. Persist the remaining findings with report_finding NOW, one finding per call. ` +
            `There is no target count and no quota. ` +
            `For POSITIVE or NOT_APPLICABLE findings, guidance can simply be \"No change needed.\" ` +
            `Only keep POSITIVE findings that add real review value. ` +
            `Do not add derivative low-signal findings when a stronger finding already covers the problem. ` +
            `Then call set_review_summary exactly once with delivery.mrNote. ` +
            `Use tools only from this point onward. Do not write planning prose or plain-text commentary. ` +
            scaffold;
    } else {
        retryPrompt =
            `You did not persist the review output. The review will fail unless you persist it NOW. ` +
            `Use your analysis from above. Do NOT read more files. Persist findings with report_finding immediately, one finding per call, ` +
            `with no target count or quota, ` +
            `using \"No change needed.\" as guidance for POSITIVE or NOT_APPLICABLE findings when needed. ` +
            `Only keep POSITIVE findings that add real review value. ` +
            `Do not add derivative low-signal findings when a stronger finding already covers the problem. ` +
            `Then call set_review_summary exactly once with the MR note. ` +
            `Use tools only from this point onward. Do not write planning prose or plain-text commentary. ` +
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
        resultFilePresent: existsSync(RESULT_PATH),
    });
    persistRunnerDebug();
    persistUsage();

    console.error(
        `[pi-runner] Retry 1: ${(retryDurationMs / 1000).toFixed(1)}s, resultFile=${existsSync(RESULT_PATH)}, reviewState=${hasPersistedReviewState()}`,
    );

    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: result.json valid after retry 1`);
        unsubscribe();
        process.exit(0);
    }

    maybeWriteResultFile();
    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: composed result.json from persisted tool state after retry 1`);
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
            `The review will be discarded unless you persist the remaining output RIGHT NOW. ` +
                `Use report_finding for any findings not yet persisted, one finding per call, and set_review_summary for delivery.mrNote. ` +
                `There is no target count and no quota. ` +
                `Do NOT read more files. Use tools only. Do NOT write planning prose or plain-text commentary. ` +
                `Only keep POSITIVE findings that add real review value. ` +
                `Do not add derivative low-signal findings when a stronger finding already covers the problem. ` +
                `Guidance for POSITIVE or NOT_APPLICABLE findings can simply be \"No change needed.\" ` +
                `${slugs.length ? `Relevant practice slugs: ${slugs.join(", ")}.` : ""}`,
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
        resultFilePresent: existsSync(RESULT_PATH),
    });
    persistRunnerDebug();
    persistUsage();

    console.error(
        `[pi-runner] Retry 2: ${(retry2DurationMs / 1000).toFixed(1)}s, resultFile=${existsSync(RESULT_PATH)}, reviewState=${hasPersistedReviewState()}`,
    );

    unsubscribe();

    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: result.json valid after retry 2`);
        process.exit(0);
    }

    maybeWriteResultFile();
    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: composed result.json from persisted tool state after retry 2`);
        process.exit(0);
    }

    // Last attempt: try to rescue from text
    if (tryRescueFromTextResponse(session.state)) {
        console.error(`[pi-runner] SUCCESS: rescued valid JSON from text`);
        process.exit(0);
    }

    // ── Failed ───────────────────────────────────────────────────

    console.error(`[pi-runner] FAILED: no complete persisted review output after initial + 2 retries`);
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
