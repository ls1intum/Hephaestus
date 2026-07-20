// Pi SDK runner — embedded in-process; persists findings via custom tools.

import { existsSync, mkdirSync, readFileSync, writeFileSync } from "fs";

import {
    AuthStorage,
    createAgentSession,
    ModelRegistry,
    SessionManager,
    SettingsManager,
    defineTool,
} from "@earendil-works/pi-coding-agent";

import { dedupeKeyForFinding, normalizeFinding } from "./pi-finding-normalize.mjs";
import { loadProviderConfig, registerHephaestusProvider } from "./pi-provider.mjs";

const OUTPUT = "/workspace/out";
const CWD = "/workspace";
const RESULT_PATH = `${OUTPUT}/result.json`;
const REVIEW_STATE_PATH = `${OUTPUT}/review-state.json`;
const AGENT_BUDGET_MS = Number(process.env.AGENT_BUDGET_MS);
if (!Number.isFinite(AGENT_BUDGET_MS) || AGENT_BUDGET_MS <= 0) {
    throw new Error(`AGENT_BUDGET_MS env var is required and must be a positive number, got: ${process.env.AGENT_BUDGET_MS}`);
}
const AGENT_DIR = process.env.PI_CODING_AGENT_DIR;
if (!AGENT_DIR) {
    throw new Error("PI_CODING_AGENT_DIR env var is required");
}
// 85% initial / 15% retry; soft nudge at 50% of initial.
const INITIAL_TIMEOUT_MS = Math.max(60_000, Math.floor(AGENT_BUDGET_MS * 0.85));
const RETRY_TIMEOUT_MS = Math.max(30_000, AGENT_BUDGET_MS - INITIAL_TIMEOUT_MS);
const SOFT_TIMEOUT_MS = Math.max(45_000, Math.floor(INITIAL_TIMEOUT_MS * 0.5));

// Watchdog: hard exit if an SDK abort hangs past the budget.
setTimeout(() => {
    console.error(`[pi-runner] Watchdog: ${AGENT_BUDGET_MS + 30_000}ms elapsed, hard-exiting`);
    try {
        writeFileSync(`${OUTPUT}/watchdog-killed.json`, JSON.stringify({
            budgetMs: AGENT_BUDGET_MS,
            elapsedMs: AGENT_BUDGET_MS + 30_000,
            reason: "runtime exceeded budget + 30s grace, hard-killed by watchdog",
        }));
    } catch {
        /* best-effort — already exiting */
    }
    process.exit(3);
}, AGENT_BUDGET_MS + 30_000).unref();

mkdirSync(OUTPUT, { recursive: true });

const usageTotals = {
    model: null,
    inputTokens: 0,
    outputTokens: 0,
    reasoningTokens: 0,
    cacheReadTokens: 0,
    cacheWriteTokens: 0,
    costUsd: 0,
    totalCalls: 0,
};
const runnerDebug = { attempts: [], usageTotals };
const reviewState = {
    findings: [],
    findingKeys: [],
};
const presenceSchema = { type: "string", enum: ["PRESENT", "ABSENT", "NOT_APPLICABLE"] };
const assessmentSchema = { type: "string", enum: ["GOOD", "BAD"] };
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
                // endLine is optional (a single-line note omits it; normalizers + the Java parser treat it as
                // optional). Keeping it REQUIRED would reject the whole tool call at the SDK boundary.
                required: ["path", "startLine"],
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
    // endLine is optional (single-line suggestion); normalizers + the Java parser treat it as optional, so
    // keeping it REQUIRED would reject an otherwise-valid single-line note at the SDK boundary.
    required: ["filePath", "startLine", "body"],
    properties: {
        filePath: { type: "string", minLength: 1 },
        startLine: { type: "integer", minimum: 1 },
        endLine: { type: "integer", minimum: 1 },
        body: { type: "string", minLength: 1 },
    },
};
// `assessment` is REQUIRED unless presence=NOT_APPLICABLE. JSON Schema cannot express that
// conditional cleanly across all validators the SDK may use, so we keep it out of `required`
// here and enforce the (presence, assessment) coupling in normalizeFinding().
const findingSchema = {
    type: "object",
    additionalProperties: false,
    required: ["practiceSlug", "title", "presence", "confidence", "evidence", "reasoning", "guidance"],
    properties: {
        practiceSlug: { type: "string", minLength: 1 },
        title: { type: "string", minLength: 1, maxLength: 120 },
        presence: presenceSchema,
        assessment: assessmentSchema,
        severity: severitySchema,
        // Upper bound is 100, not 1, so a model emitting percentage-style confidence (e.g. 85)
        // is not rejected at the SDK boundary; normalizeFinding rescales (1,100] -> /100 to
        // mirror the Java consumer PracticeDetectionResultParser.parseConfidence.
        confidence: { type: "number", minimum: 0, maximum: 100 },
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
        JSON.stringify({ findings: reviewState.findings }, null, 2),
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


function isValidFinding(f) {
    if (!f || typeof f !== "object") return false;
    if (typeof f.practiceSlug !== "string" || !f.practiceSlug.trim()) return false;
    if (typeof f.title !== "string" || !f.title.trim()) return false;
    if (typeof f.presence !== "string") return false;
    // assessment is required unless presence=NOT_APPLICABLE (which has no valence).
    if (f.presence !== "NOT_APPLICABLE" && typeof f.assessment !== "string") return false;
    // Number(null) === 0 — reject nullish before isNaN check.
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
        p.findings.some(isValidFinding)
    );
}

function lenientJsonParse(text) {
    // Strip C0 + DEL control chars (mirrors Java ALLOW_UNESCAPED_CONTROL_CHARS).
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
    if (reviewState.findings.length === 0) return false;
    writeFileSync(RESULT_PATH, JSON.stringify({ findings: reviewState.findings }, null, 2));
    return true;
}

function hasPersistedReviewState() {
    return reviewState.findings.length > 0;
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
        const negativeCount = params.finding.assessment === "BAD" ? 1 : 0;
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

function extractUsageFromSession(session) {
    const messages = session.messages || [];
    let model = null,
        inputTokens = 0,
        outputTokens = 0,
        reasoningTokens = 0,
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
        // Responses-path shape (output_tokens_details.reasoning_tokens) surfaced by the SDK as
        // usage.reasoning when the upstream model reports it (e.g. o-series/gpt-5 reasoning models);
        // absent for chat/completions-only models, so this stays 0 for those.
        reasoningTokens += Number(msg.usage.reasoning || msg.usage.reasoningTokens || 0);
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
        reasoningTokens,
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
    usageTotals.reasoningTokens += Math.max(0, curr.reasoningTokens - (prev?.reasoningTokens || 0));
    usageTotals.cacheReadTokens += Math.max(0, curr.cacheReadTokens - (prev?.cacheReadTokens || 0));
    usageTotals.cacheWriteTokens += Math.max(0, curr.cacheWriteTokens - (prev?.cacheWriteTokens || 0));
    usageTotals.costUsd += Math.max(0, curr.costUsd - (prev?.costUsd || 0));
    usageTotals.totalCalls += Math.max(0, curr.totalCalls - (prev?.totalCalls || 0));
}


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

// Mirror PracticeDetectionResultParser.MAX_RAW_OUTPUT_LENGTH on the Java side: a well-formed agent
// rawOutput never approaches this, so a larger blob is junk and the brace-scan below would burn the
// remaining grace window parsing growing slices for nothing.
const MAX_RESCUE_TEXT_LENGTH = 1_000_000;

function tryParseJsonFromText(text) {
    if (!text) return null;
    if (text.length > MAX_RESCUE_TEXT_LENGTH) return null;
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
    // Find {"findings": ... } object (tolerates whitespace).
    const findingsMatch = text.match(/\{\s*"findings"/);
    if (!findingsMatch || findingsMatch.index === undefined) return null;
    const braceStart = findingsMatch.index;
    // Cap the closing-brace scan: a valid payload's outermost `}` is found within the first few
    // candidates, so an unbounded walk over a brace-heavy blob is pure waste (mirrors the Java twin
    // extractJsonFromText, which caps at a small fixed number of attempts).
    let attempts = 0;
    for (let end = text.indexOf("}", braceStart); end >= 0 && attempts < 256; end = text.indexOf("}", end + 1)) {
        attempts++;
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


function chunkArray(arr, size) {
    const out = [];
    for (let i = 0; i < arr.length; i += size) {
        out.push(arr.slice(i, i + size));
    }
    return out;
}

// Group practice slugs by their area (from index.json), preserving order. A area forms one coherent,
// focused evaluation; ungrouped practices fall back to their own one-practice group.
function loadPracticeGroups() {
    try {
        const indexPath = `${CWD}/inputs/practices/index.json`;
        if (!existsSync(indexPath)) return [];
        const index = JSON.parse(readFileSync(indexPath, "utf-8"));
        if (!Array.isArray(index)) return [];
        const byArea = new Map();
        for (const p of index) {
            if (!p.slug) continue;
            const area = p.area || p.slug;
            if (!byArea.has(area)) byArea.set(area, []);
            byArea.get(area).push(p.slug);
        }
        return [...byArea.entries()].map(([area, slugs]) => ({ area, slugs }));
    } catch {
        return [];
    }
}

function loadPracticeSlugs() {
    try {
        const indexPath = `${CWD}/inputs/practices/index.json`;
        if (!existsSync(indexPath)) return [];
        const index = JSON.parse(readFileSync(indexPath, "utf-8"));
        if (!Array.isArray(index)) return [];
        return index.map((p) => p.slug).filter(Boolean);
    } catch {
        return [];
    }
}

// Shared persist-discipline tail — reused by the soft-timeout steer and every retry branch so the wording
// cannot drift between the sites that emit it.
const PERSIST_DISCIPLINE =
    `There is no target count and no quota. ` +
    `For a GOOD (strength) finding or a NOT_APPLICABLE finding, guidance can simply be "No change needed." ` +
    `Only keep GOOD findings that add real review value. ` +
    `Do not add derivative low-signal findings when a stronger finding already covers the problem. ` +
    `Use tools only from this point onward. Do not write planning prose or plain-text commentary.`;

function buildRetryScaffold(slugs) {
    if (!slugs.length) return "";
    return (
        `\n\nThe practice slugs you must cover: ${slugs.join(", ")}. ` +
        `Persist every justified finding with report_finding, one finding per call. ` +
        `There is no target count and no quota. ` +
        `Only report GOOD findings that add real review value. ` +
        `Do not emit derivative low-signal findings when a stronger root-cause finding already covers the problem.`
    );
}


// Task envelope: /workspace/task.json (TaskEnvelope<PracticeReviewTask>).
// Exit 42 on schema-version mismatch or unknown kind so the executor can log
// envelope/image drift distinctly from agent failures.
const ENVELOPE_MISMATCH_EXIT = 42;
const SUPPORTED_SCHEMA_VERSION = 1;
const SUPPORTED_KIND = "practice_review";
const TASK_PATH = "/workspace/task.json";

function readTaskEnvelope() {
    let raw;
    try {
        raw = readFileSync(TASK_PATH, "utf-8");
    } catch (err) {
        console.error(`[pi-runner] Failed to read ${TASK_PATH}: ${err.message}`);
        process.exit(ENVELOPE_MISMATCH_EXIT);
    }
    let envelope;
    try {
        envelope = JSON.parse(raw);
    } catch (err) {
        console.error(`[pi-runner] Failed to parse ${TASK_PATH}: ${err.message}`);
        process.exit(ENVELOPE_MISMATCH_EXIT);
    }
    if (envelope?.schemaVersion !== SUPPORTED_SCHEMA_VERSION) {
        console.error(
            `[pi-runner] Unsupported schemaVersion: got ${envelope?.schemaVersion}, expected ${SUPPORTED_SCHEMA_VERSION}. ` +
                `Server/image version drift — rebuild the agent-pi image or roll back the server.`,
        );
        process.exit(ENVELOPE_MISMATCH_EXIT);
    }
    if (envelope?.task?.kind !== SUPPORTED_KIND) {
        console.error(
            `[pi-runner] Unknown task kind: got "${envelope?.task?.kind}", expected "${SUPPORTED_KIND}". ` +
                `This runner only handles practice_review tasks.`,
        );
        process.exit(ENVELOPE_MISMATCH_EXIT);
    }
    if (typeof envelope.task.prompt !== "string" || envelope.task.prompt.trim() === "") {
        console.error(`[pi-runner] task.prompt is missing or blank in ${TASK_PATH}`);
        process.exit(ENVELOPE_MISMATCH_EXIT);
    }
    return envelope;
}

const taskEnvelope = readTaskEnvelope();
const prompt = taskEnvelope.task.prompt.trim();
console.error(
    `[pi-runner] Task envelope loaded: kind=${taskEnvelope.task.kind}, ` +
        `jobId=${taskEnvelope.jobId}, workspaceId=${taskEnvelope.workspaceId}, ` +
        `repository=${taskEnvelope.task.repositoryFullName ?? "?"}, ` +
        `prNumber=${taskEnvelope.task.pullRequestNumber ?? "?"}`,
);

async function main() {
    console.error(`[pi-runner] Embedded SDK mode`);
    console.error(
        `[pi-runner] Budget: total=${AGENT_BUDGET_MS}ms, initial=${INITIAL_TIMEOUT_MS}ms (soft=${SOFT_TIMEOUT_MS}ms), retry=${RETRY_TIMEOUT_MS}ms`,
    );

    // `tools` is an allowlist of tool *names* (Pi 0.74+ filters customTools through the same
    // allowlist), so both built-in and custom tool names must appear here. Edit/write are omitted
    // — findings are persisted only via report_finding.
    const settingsManager = SettingsManager.create(CWD, AGENT_DIR);
    const sessionManager = SessionManager.inMemory();
    const authStorage = AuthStorage.create();
    const modelRegistry = ModelRegistry.create(authStorage);

    // Pi 0.74.x bug: createAgentSession.findInitialModel runs before the extension runner drains
    // pending registrations into the model registry. Register the hephaestus provider directly
    // here so the session resolves a real model on first prompt. Config (protocol/model/capability)
    // comes from the server-written pi-provider.json; baseUrl/token come from the sandbox env
    // (#1368 slice 5 — shared with pi-mentor-runner.mjs via pi-provider.mjs).
    const providerConfig = loadProviderConfig(CWD);
    const registered = registerHephaestusProvider(modelRegistry, providerConfig);
    if (registered) {
        console.error(
            `[pi-runner] registered hephaestus provider: apiProtocol=${providerConfig.apiProtocol} model=${providerConfig.modelId}`,
        );
    } else {
        console.error(`[pi-runner] hephaestus provider NOT registered — missing pi-provider.json or proxy env vars`);
    }

    const { session, extensionsResult } = await createAgentSession({
        cwd: CWD,
        agentDir: AGENT_DIR,
        tools: ["read", "bash", "grep", "report_finding"],
        customTools: [reportFindingTool],
        sessionManager,
        settingsManager,
        authStorage,
        modelRegistry,
    });
    // Extension load failures are silent in Pi — surface them so the agent doesn't fall through
    // to a built-in provider's default endpoint (e.g. api.openai.com).
    if (extensionsResult?.extensions?.length) {
        for (const ext of extensionsResult.extensions) {
            console.error(`[pi-runner] extension loaded: ${ext.path}`);
        }
    }
    if (extensionsResult?.errors?.length) {
        for (const err of extensionsResult.errors) {
            console.error(`[pi-runner] extension error: ${err?.path}: ${err?.error}`);
        }
    }

    // ── Attempt 1: Initial analysis ──────────────────────────────

    let softTimeoutFired = false;
    let hardAborted = false;
    let prevUsage = null;

    // Soft nudge: steer the agent to persist findings before the hard timeout aborts.
    const softTimer = setTimeout(() => {
        softTimeoutFired = true;
        console.error(`[pi-runner] Soft timeout fired — nudging agent to persist review state`);
        const steerMessage =
            `Stop analyzing and persist output now. ` +
            `Use report_finding immediately for any finding you already have, one finding per call. ` +
            PERSIST_DISCIPLINE;
        session.steer(steerMessage).catch((err) => console.error(`[pi-runner] steer failed: ${err.message}`));
    }, SOFT_TIMEOUT_MS);

    const hardTimer = setTimeout(() => {
        hardAborted = true;
        console.error(`[pi-runner] Hard timeout — aborting agent`);
        session.abort().catch((err) => console.error(`[pi-runner] abort failed: ${err.message}`));
    }, INITIAL_TIMEOUT_MS);

    const events = [];
    const unsubscribe = session.subscribe((event) => {
        if (event.type === "tool_execution_start") {
            console.error(`[pi-runner] tool: ${event.toolName ?? "?"}`);
        }
        if (event.type === "message_end" && event.message?.role === "assistant") {
            const stopReason = event.message.stopReason;
            const types = (event.message.content || []).map((c) => c.type);
            const toolCalls = types.filter((t) => t === "tool_use" || t === "tool_call").length;
            const errMsg = event.message.errorMessage;
            console.error(
                `[pi-runner] assistant msg: stopReason=${stopReason}, toolCalls=${toolCalls}, types=[${types}]` +
                    (errMsg ? `, errorMessage=${redact(errMsg)}` : ""),
            );
        }
        events.push({ type: event.type, timestamp: Date.now() });
    });

    console.error(`[pi-runner] Starting initial analysis`);
    const startMs = Date.now();

    // Fan-out: a single agent turn cannot reliably evaluate many practices — on a large diff it runs out
    // of budget and skips most, and a long all-criteria bundle mid-context degrades recall. Instead we keep
    // ONE session (it reads the diff once) and drive it through the practices in focused turns, ONE PER AREA
    // (a coherent 2-4 practice group); each turn reads only that area's per-practice criteria. report_finding
    // accumulates across turns. A coverage gate then re-prompts any practice no turn reported, so every active
    // practice gets a finding. The overall hard timeout + watchdog bound total time; turns stop when it aborts.
    const allSlugs = loadPracticeSlugs();
    const batchSize = Number(process.env.PI_PRACTICE_BATCH_SIZE) || 6;
    const groups = loadPracticeGroups();
    const batches = [];
    if (groups.length > 0) {
        // One batch per area; sub-chunk a area that exceeds batchSize so context stays bounded.
        for (const g of groups) {
            for (const chunk of chunkArray(g.slugs, batchSize)) batches.push(chunk);
        }
    } else {
        batches.push(...(allSlugs.length > batchSize ? chunkArray(allSlugs, batchSize) : [allSlugs]));
    }
    console.error(
        `[pi-runner] Fan-out: ${allSlugs.length} practices in ${groups.length || "?"} areas -> ${batches.length} focused turn(s)`,
    );

    try {
        for (let bi = 0; bi < batches.length; bi++) {
            if (hardAborted) {
                console.error(`[pi-runner] Hard abort fired — stopping turn loop at ${bi}/${batches.length}`);
                break;
            }
            const batch = batches[bi];
            const readHint = `Read inputs/practices/${batch.length === 1 ? `${batch[0]}.md` : "<slug>.md for each"}`;
            const batchPrompt =
                bi === 0
                    ? `${prompt}\n\n## Scope for this turn\n${readHint} and evaluate ONLY these practices, persisting each with report_finding (one call per finding): ${batch.join(", ")}.`
                    : `Continue the SAME review. Using the diff and context you ALREADY read (do NOT re-read the diff), ${readHint} and evaluate ONLY these practices, persisting each with report_finding (one call per finding): ${batch.join(", ")}.`;
            try {
                await session.prompt(batchPrompt);
            } catch (err) {
                console.error(`[pi-runner] turn ${bi + 1}/${batches.length} prompt error: ${err.message}`);
                if (hardAborted) break;
            }
            console.error(`[pi-runner] turn ${bi + 1}/${batches.length} complete (slugs=${batch.length})`);
        }

        // Coverage gate: every active practice must get a finding. Re-prompt the ones no turn reported.
        if (!hardAborted && allSlugs.length > 0) {
            const covered = new Set(reviewState.findings.map((f) => f.practiceSlug).filter(Boolean));
            const missing = allSlugs.filter((s) => !covered.has(s));
            if (missing.length > 0) {
                console.error(`[pi-runner] Coverage gate: ${missing.length} unreported -> ${missing.join(", ")}`);
                const gatePrompt =
                    `Coverage check. You have NOT yet reported a finding for these practices: ${missing.join(", ")}. ` +
                    `Read inputs/practices/<slug>.md for each and evaluate it against the SAME diff/context you already read ` +
                    `(do NOT re-read the diff). Persist a finding for EVERY one with report_finding, one call per finding ` +
                    `— set presence (PRESENT/ABSENT/NOT_APPLICABLE) and, unless NOT_APPLICABLE, assessment (GOOD/BAD).`;
                try {
                    await session.prompt(gatePrompt);
                } catch (err) {
                    console.error(`[pi-runner] coverage-gate prompt error: ${err.message}`);
                }
                const stillMissing = allSlugs.filter(
                    (s) => !new Set(reviewState.findings.map((f) => f.practiceSlug)).has(s),
                );
                console.error(
                    `[pi-runner] Coverage gate done: ${allSlugs.length - stillMissing.length}/${allSlugs.length} practices covered`,
                );
            } else {
                console.error(`[pi-runner] Coverage gate: all ${allSlugs.length} practices already reported`);
            }
        }
    } finally {
        clearTimeout(softTimer);
        clearTimeout(hardTimer);
    }

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
        if (tryRescueFromTextResponse(session.state)) {
            console.error(`[pi-runner] SUCCESS: rescued valid JSON from agent text`);
            unsubscribe();
            process.exit(0);
        }
    }

    console.error(`[pi-runner] Re-prompting agent to persist remaining review output`);

    const slugs = loadPracticeSlugs();
    const scaffold = buildRetryScaffold(slugs);
    console.error(`[pi-runner] Loaded ${slugs.length} practice slugs for retry scaffold`);

    let retryAborted = false;
    const retryTimer = setTimeout(() => {
        retryAborted = true;
        console.error(`[pi-runner] Retry hard timeout — aborting`);
        session.abort().catch((err) => console.error(`[pi-runner] retry abort failed: ${err.message}`));
    }, RETRY_TIMEOUT_MS);

    const retryStartMs = Date.now();

    // Recovery strategy varies by failure mode (timeout vs no-persist vs nothing-said).
    let retryPrompt;
    if (softTimeoutFired || hardAborted) {
        retryPrompt =
            `You ran out of time before finalizing the review. ` +
            `Do NOT restart analysis from scratch. Do NOT read more files. ` +
            `Persist every remaining justified finding with report_finding immediately, one finding per call. ` +
            PERSIST_DISCIPLINE + ` ` +
            scaffold;
    } else if (agentText) {
        retryPrompt =
            `You completed analysis but did not persist the final review output. ` +
            `Do NOT read any more files. Persist the remaining findings with report_finding NOW, one finding per call. ` +
            PERSIST_DISCIPLINE + ` ` +
            scaffold;
    } else {
        retryPrompt =
            `You did not persist the review output. The review will fail unless you persist it NOW. ` +
            `Use your analysis from above. Do NOT read more files. Persist findings with report_finding immediately, one finding per call. ` +
            PERSIST_DISCIPLINE + ` ` +
            scaffold;
    }

    try {
        try {
            await session.prompt(retryPrompt);
        } catch (err) {
            console.error(`[pi-runner] Retry error: ${err.message}`);
        }
    } finally {
        clearTimeout(retryTimer);
    }

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
        `[pi-runner] Retry: ${(retryDurationMs / 1000).toFixed(1)}s, resultFile=${existsSync(RESULT_PATH)}, reviewState=${hasPersistedReviewState()}`,
    );

    unsubscribe();

    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: result.json valid after retry`);
        process.exit(0);
    }

    maybeWriteResultFile();
    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: composed result.json from persisted tool state after retry`);
        process.exit(0);
    }

    // Last attempt: try to rescue from text
    if (tryRescueFromTextResponse(session.state)) {
        console.error(`[pi-runner] SUCCESS: rescued valid JSON from text`);
        process.exit(0);
    }

    console.error(`[pi-runner] FAILED: no complete persisted review output after initial attempt + recovery retry`);
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
