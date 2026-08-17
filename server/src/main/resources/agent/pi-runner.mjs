
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "fs";

import {
    AuthStorage,
    createAgentSession,
    ModelRegistry,
    SessionManager,
    SettingsManager,
    defineTool,
} from "@earendil-works/pi-coding-agent";

import {
    ASSESSMENT_DESCRIPTIONS,
    ASSESSMENT_VALUES,
    PRESENCE_DESCRIPTIONS,
    PRESENCE_VALUES,
    SEVERITY_DESCRIPTIONS,
    SEVERITY_VALUES,
    carriesValence,
    citationMatchesArtifact,
    dedupeKeyForObservation,
    describeVocabulary,
    normalizeObservation,
    validateEvidenceSources,
    validateSearchScope,
    validateInapplicabilityScope,
} from "./pi-observation-normalize.mjs";
import { loadProviderConfig, registerHephaestusProvider } from "./pi-provider.mjs";
import { addAssistantUsage, extractUsageFromSession, newUsageLedger } from "./pi-runner-usage.mjs";
import { deriveTimeouts } from "./pi-runner-timings.mjs";

const OUTPUT = "/workspace/out";
const CWD = "/workspace";
const RESULT_PATH = `${OUTPUT}/result.json`;
const REVIEW_STATE_PATH = `${OUTPUT}/review-state.json`;
const AGENT_BUDGET_MS = Number(process.env.AGENT_BUDGET_MS);
if (!Number.isFinite(AGENT_BUDGET_MS) || AGENT_BUDGET_MS <= 0) {
    throw new Error(
        `AGENT_BUDGET_MS env var is required and must be a positive number, got: ${process.env.AGENT_BUDGET_MS}`,
    );
}
const AGENT_DIR = process.env.PI_CODING_AGENT_DIR;
if (!AGENT_DIR) {
    throw new Error("PI_CODING_AGENT_DIR env var is required");
}
const {
    initialMs: INITIAL_TIMEOUT_MS,
    retryMs: RETRY_TIMEOUT_MS,
    softNudgeMs: SOFT_TIMEOUT_MS,
    compositionMs: COMPOSITION_TIMEOUT_MS,
} = deriveTimeouts(AGENT_BUDGET_MS, existsSync(`${CWD}/inputs/feedback-composition.json`));

setTimeout(() => {
    console.error(`[pi-runner] Watchdog: ${AGENT_BUDGET_MS + 30_000}ms elapsed, hard-exiting`);
    try {
        writeFileSync(
            `${OUTPUT}/watchdog-killed.json`,
            JSON.stringify({
                budgetMs: AGENT_BUDGET_MS,
                elapsedMs: AGENT_BUDGET_MS + 30_000,
                reason: "runtime exceeded budget + 30s grace, hard-killed by watchdog",
            }),
        );
    } catch {
        /* best-effort — already exiting */
    }
    process.exit(3);
}, AGENT_BUDGET_MS + 30_000).unref();

mkdirSync(OUTPUT, { recursive: true });

const manifest = JSON.parse(readFileSync(`${CWD}/inputs/manifest.json`, "utf8"));
const availableSourceKinds = new Set(
    manifest.sources.filter((source) => source.state.availability === "AVAILABLE").map((source) => source.kind),
);
const artifactSources = new Map(
    manifest.sources.flatMap((source) => (source.artifacts ?? []).map((artifact) => [artifact.path, source.kind])),
);
const practiceIndex = JSON.parse(readFileSync(`${CWD}/inputs/practices/index.json`, "utf8"));
const admittedPractices = new Set(practiceIndex.map((practice) => practice.slug));
// ABSENT is sound only over sources the practice declares exhaustive.
const practiceExhaustiveSources = new Map(
    practiceIndex.map((practice) => [practice.slug, new Set(practice.exhaustiveSources ?? [])]),
);

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
    observations: [],
    observationKeys: [],
};
const presenceSchema = {
    type: "string",
    enum: PRESENCE_VALUES,
    description:
        "Is the behaviour this practice names in the work? Every practice names one — read its criteria for " +
        "what the behaviour is, then pick the value whose test you can actually pass.\n" +
        describeVocabulary(PRESENCE_VALUES, PRESENCE_DESCRIPTIONS),
};
const assessmentSchema = {
    type: "string",
    enum: ASSESSMENT_VALUES,
    description:
        "Is what presence recorded good or bad FOR THE DEVELOPER? Required for PRESENT and ABSENT; omit it " +
        "entirely for NOT_APPLICABLE and INCONCLUSIVE, which assert no direction and are not quiet verdicts.\n" +
        describeVocabulary(ASSESSMENT_VALUES, ASSESSMENT_DESCRIPTIONS),
};
const severitySchema = {
    type: "string",
    enum: SEVERITY_VALUES,
    description:
        "How much the problem costs. Set it only when assessment is BAD, and read it off the practice's own " +
        "severity table keyed to the fact you quoted — never from a feeling of how bad it is, so identical " +
        "facts land in the same band every run.\n" +
        describeVocabulary(SEVERITY_VALUES, SEVERITY_DESCRIPTIONS),
};
const searchSchema = {
    type: "object",
    additionalProperties: false,
    required: ["consulted", "lookedFor", "boundary"],
    properties: {
        consulted: {
            type: "array",
            minItems: 1,
            items: { type: "string", minLength: 1 },
            description: "Evidence source kinds you actually searched, e.g. scm.review-threads.",
        },
        lookedFor: {
            type: "string",
            minLength: 1,
            description: "The concrete thing whose absence you are reporting.",
        },
        boundary: {
            type: "string",
            minLength: 1,
            description: "What this search did NOT cover, so a reader can judge how far the absence reaches.",
        },
    },
};
const inapplicabilitySchema = {
    type: "object",
    additionalProperties: false,
    required: ["consulted", "subject", "ruledOutBy"],
    properties: {
        consulted: {
            type: "array",
            minItems: 1,
            items: { type: "string", minLength: 1 },
            description: "Evidence source kinds you read to reach this conclusion, e.g. scm.pull-request.diff.",
        },
        subject: {
            type: "string",
            minLength: 1,
            description: "What this practice looks for, e.g. error handling around outbound network calls.",
        },
        ruledOutBy: {
            type: "string",
            minLength: 1,
            description:
                "The fact about THIS work that means the subject cannot occur in it, e.g. the change touches " +
                "only Markdown documentation and makes no network calls.",
        },
    },
};
const undecidabilitySchema = {
    type: "object",
    additionalProperties: false,
    required: ["openQuestion", "wouldSettleIt"],
    properties: {
        openQuestion: {
            type: "string",
            minLength: 1,
            description: "The question the evidence you actually read left open, in one sentence.",
        },
        wouldSettleIt: {
            type: "string",
            minLength: 1,
            description:
                "The EVIDENCE that would have decided it — something that already exists and you could not " +
                "read, named concretely: 'the body of issue #7', 'the test file the description says covers " +
                "this'. NOT what the author should have written: advice belongs to a later step, and " +
                "answering with it leaves nobody any wiser about which source this practice is missing.",
        },
    },
};
const evidenceSchema = {
    type: "object",
    additionalProperties: false,
    required: ["citations"],
    properties: {
        search: searchSchema,
        inapplicability: inapplicabilitySchema,
        undecidability: undecidabilitySchema,
        citations: {
            type: "array",
            minItems: 1,
            items: {
                type: "object",
                additionalProperties: false,
                required: ["sourceKind", "artifactPath", "path", "startLine", "quote"],
                properties: {
                    sourceKind: { type: "string", minLength: 1 },
                    artifactPath: { type: "string", minLength: 1 },
                    path: { type: "string", minLength: 1 },
                    side: { type: "string", enum: ["OLD", "NEW"] },
                    startLine: { type: "integer", minimum: 1 },
                    endLine: { type: "integer", minimum: 1 },
                    quote: { type: "string", minLength: 1 },
                },
            },
        },
    },
};
const observationSchema = {
    type: "object",
    additionalProperties: false,
    required: ["practiceSlug", "summary", "outcome", "evidence", "evidenceRationale"],
    properties: {
        practiceSlug: { type: "string", minLength: 1 },
        summary: { type: "string", minLength: 1, maxLength: 120 },
        outcome: {
            type: "string",
            enum: [
                "BEHAVIOR_PRESENT_GOOD",
                "BEHAVIOR_PRESENT_BAD_MINOR",
                "BEHAVIOR_PRESENT_BAD_MAJOR",
                "BEHAVIOR_PRESENT_BAD_CRITICAL",
                "BEHAVIOR_ABSENT_GOOD",
                "BEHAVIOR_ABSENT_BAD_MINOR",
                "BEHAVIOR_ABSENT_BAD_MAJOR",
                "BEHAVIOR_ABSENT_BAD_CRITICAL",
                "NO_REVIEW_OCCASION",
                "INSUFFICIENT_EVIDENCE",
            ],
            description:
                "Choose a BEHAVIOR result whenever the practice has something to judge. An absent target behaviour is BEHAVIOR_ABSENT, never NO_REVIEW_OCCASION. NO_REVIEW_OCCASION means a prerequisite situation explicitly named by the practice did not occur.",
        },
        evidence: {
            ...evidenceSchema,
            properties: {
                citations: evidenceSchema.properties.citations,
                exhaustiveSearch: searchSchema,
                exclusion: inapplicabilitySchema,
                missingEvidence: undecidabilitySchema,
            },
        },
        evidenceRationale: {
            type: "string",
            minLength: 1,
            description:
                "A concise explanation of how the cited evidence warrants this outcome. Describe evidence, " +
                "not advice, intent, confidence, or hidden chain-of-thought.",
        },
    },
};

function persistUsage() {
    writeFileSync(`${OUTPUT}/usage.json`, JSON.stringify(usageTotals, null, 2));
}
function persistRunnerDebug() {
    writeFileSync(`${OUTPUT}/runner-debug.json`, JSON.stringify(runnerDebug, null, 2));
}
function persistReviewState() {
    writeFileSync(REVIEW_STATE_PATH, JSON.stringify({ observations: reviewState.observations }, null, 2));
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

function isValidObservation(f) {
    if (!f || typeof f !== "object") return false;
    if (typeof f.practiceSlug !== "string" || !f.practiceSlug.trim()) return false;
    if (typeof f.summary !== "string" || !f.summary.trim()) return false;
    if (typeof f.presence !== "string") return false;
    if (carriesValence(f.presence) && typeof f.assessment !== "string") return false;
    return true;
}

function isValidObservationsPayload(p) {
    return (
        p &&
        typeof p === "object" &&
        Array.isArray(p.observations) &&
        p.observations.length > 0 &&
        p.observations.every(isValidObservation)
    );
}

function lenientJsonParse(text) {
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
        const valid = isValidObservationsPayload(data);
        if (!valid) {
            const hasObservations = Array.isArray(data?.observations);
            const count = hasObservations ? data.observations.length : 0;
            const validCount = hasObservations ? data.observations.filter(isValidObservation).length : 0;
            console.error(`[pi-runner] result.json validation failed: observations=${count}, valid=${validCount}`);
        }
        if (!valid) return false;
        const normalized = data.observations.map(normalizeAndValidateObservation);
        writeFileSync(RESULT_PATH, JSON.stringify({ observations: normalized }, null, 2));
        return true;
    } catch (e) {
        console.error(`[pi-runner] result.json parse error: ${e.message}`);
        return false;
    }
}

function maybeWriteResultFile() {
    if (reviewState.observations.length === 0) return false;
    writeFileSync(RESULT_PATH, JSON.stringify({ observations: reviewState.observations }, null, 2));
    return true;
}

function hasPersistedReviewState() {
    return reviewState.observations.length > 0;
}

function resolveResultFile() {
    if (checkResultFile()) return "agent";
    if (maybeWriteResultFile() && checkResultFile()) return "tool-state";
    return null;
}

function appendObservations(observations) {
    let inserted = 0;
    let duplicates = 0;
    const seen = new Set(reviewState.observationKeys);
    for (const rawObservation of observations) {
        const observation = normalizeAndValidateObservation(rawObservation);
        const key = dedupeKeyForObservation(observation);
        if (seen.has(key)) {
            duplicates++;
            continue;
        }
        seen.add(key);
        reviewState.observationKeys.push(key);
        reviewState.observations.push(observation);
        inserted++;
    }
    persistReviewState();
    maybeWriteResultFile();
    return { inserted, duplicates };
}

function normalizeAndValidateObservation(rawObservation) {
    const observation = normalizeObservation(rawObservation);
    if (!admittedPractices.has(observation.practiceSlug))
        throw new Error(`unknown practice '${observation.practiceSlug}'`);
    validateEvidenceSources(observation, availableSourceKinds, artifactSources);
    validateSearchScope(
        observation,
        practiceExhaustiveSources.get(observation.practiceSlug) ?? new Set(),
        availableSourceKinds,
    );
    validateInapplicabilityScope(observation, availableSourceKinds);
    for (const citation of observation.evidence.citations) {
        const content = readFileSync(`${CWD}/${citation.artifactPath}`, "utf8");
        if (!citationMatchesArtifact(citation, content)) {
            throw new Error(`citation does not match artifact location '${citation.artifactPath}'`);
        }
    }
    return observation;
}

let measurementClosed = false;

const reportObservationTool = defineTool({
    name: "report_observation",
    label: "Report Observation",
    description:
        "Persist exactly one structured observation immediately so it survives retries and timeouts. Call this as soon as one observation is ready. Do not wait to batch observations.",
    parameters: {
        type: "object",
        additionalProperties: false,
        required: ["observation"],
        properties: {
            observation: observationSchema,
        },
    },
    execute: async (_toolCallId, params) => {
        if (measurementClosed) {
            return {
                content: [{ type: "text", text: "Measurement is closed; this turn may only compose feedback." }],
                details: { inserted: 0, measurementClosed: true },
            };
        }
        const { inserted, duplicates } = appendObservations([params.observation]);
        const negativeCount = params.observation.assessment === "BAD" ? 1 : 0;
        return {
            content: [
                {
                    type: "text",
                    text: `Stored ${inserted} observation${duplicates > 0 ? ` (${duplicates} duplicate skipped)` : ""}. Negative observations in this call: ${negativeCount}.`,
                },
            ],
            details: { inserted, duplicates, totalObservations: reviewState.observations.length },
        };
    },
});

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
        const textBlocks = (msg.content || []).filter((c) => c.type === "text" || c.type === "thinking");
        const text = textBlocks
            .map((c) => c.text || c.thinking || "")
            .join("")
            .trim();
        if (!text || text.length < 20) continue;
        if (text.includes("{") && text.includes("}")) return text;
    }
    return null;
}

// Keep the recovery scan bounded consistently with PracticeDetectionResultParser.
const MAX_RESCUE_TEXT_LENGTH = 1_000_000;

function tryParseJsonFromText(text) {
    if (!text) return null;
    if (text.length > MAX_RESCUE_TEXT_LENGTH) return null;
    try {
        const parsed = JSON.parse(text);
        if (isValidObservationsPayload(parsed)) return parsed;
    } catch {}
    const jsonBlockPattern = /```(?:json)?\s*\n?([\s\S]*?)\n?\s*```/g;
    let match = jsonBlockPattern.exec(text);
    while (match !== null) {
        try {
            const parsed = JSON.parse(match[1].trim());
            if (isValidObservationsPayload(parsed)) return parsed;
        } catch {}
        match = jsonBlockPattern.exec(text);
    }
    const observationsMatch = text.match(/\{\s*"observations"/);
    if (!observationsMatch || observationsMatch.index === undefined) return null;
    const braceStart = observationsMatch.index;
    // Bound brace-heavy recovery input consistently with the Java parser.
    let attempts = 0;
    for (let end = text.indexOf("}", braceStart); end >= 0 && attempts < 256; end = text.indexOf("}", end + 1)) {
        attempts++;
        try {
            const candidate = text.slice(braceStart, end + 1);
            const parsed = JSON.parse(candidate);
            if (isValidObservationsPayload(parsed)) return parsed;
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
    console.error(`[pi-runner] Text rescue: extracted ${payload.observations.length} observations`);
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

const PERSIST_DISCIPLINE =
    `There is no target count and no quota. ` +
    `Record what you saw; you are not asked for a next step, so do not write one. ` +
    `Only keep GOOD observations that add real review value. ` +
    `Do not add derivative low-signal observations when a stronger observation already covers the problem. ` +
    `Use tools only from this point onward. Do not write planning prose or plain-text commentary.`;

function buildRetryScaffold(slugs) {
    if (!slugs.length) return "";
    return (
        `\n\nThe practice slugs you must cover: ${slugs.join(", ")}. ` +
        `Persist every justified observation with report_observation, one observation per call. ` +
        `There is no target count and no quota. ` +
        `Only report GOOD observations that add real review value. ` +
        `Do not emit derivative low-signal observations when a stronger root-cause observation already covers the problem.`
    );
}

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

const COMPOSITION_REQUEST_PATH = `${CWD}/inputs/feedback-composition.json`;
const FEEDBACK_PATH = `${OUTPUT}/feedback.json`;
const COMPOSER_PROMPT_PATH = `${CWD}/feedback-composer.md`;
const OBSERVATION_HISTORY_PATH = `${CWD}/inputs/history/observations.json`;
const PREPARED_FEEDBACK_PATH = `${CWD}/inputs/history/prepared.json`;
const COMPOSITION_OBSERVATIONS_PATH = `${CWD}/work/composition/observations.json`;
let compositionAdmitted = false;
let admissionDigest = null;
const CHANNELS = ["IN_CONTEXT", "IN_APP", "IN_CHAT"];
const ACTIONS = ["NEW", "SUPERSEDE", "WITHHOLD"];
const WITHHOLD_REASONS = ["NO_MATERIAL_CHANGE", "ALREADY_SAID", "BELOW_BAR"];

// Echo the exact composition inputs so Java validates references against the same snapshot.
const composedFeedback = { admissionDigest: null, observations: [], preparedThreadKeys: [], units: [] };

function loadCompositionRequest() {
    try {
        if (!existsSync(COMPOSITION_REQUEST_PATH)) return null;
        const request = JSON.parse(readFileSync(COMPOSITION_REQUEST_PATH, "utf8"));
        if (!request || request.enabled !== true) return null;
        const channels = {};
        for (const channel of CHANNELS) {
            const bounds = request.channels?.[channel];
            channels[channel] = {
                enabled: bounds?.enabled === true,
                maxUnits: Math.max(0, Math.min(Number(bounds?.maxUnits) || 0, 10)),
            };
        }
        if (!CHANNELS.some((channel) => channels[channel].enabled && channels[channel].maxUnits > 0)) return null;
        const inContextPlacementKinds = (request.inContextPlacementKinds ?? []).filter((kind) =>
            ["DIFF", "ARTIFACT"].includes(kind),
        );
        if (channels.IN_CONTEXT.enabled && inContextPlacementKinds.length === 0) return null;
        return {
            channels,
            inContextPlacementKinds,
            minDistinctArtifacts: Math.max(2, Number(request.minDistinctArtifacts) || 2),
        };
    } catch (e) {
        console.error(`[pi-runner] composition request unreadable: ${e.message}`);
        return null;
    }
}

// Longitudinal feedback may reference practices found only in this developer's history.
function composablePracticeSlugs() {
    const slugs = new Set(admittedPractices);
    try {
        if (existsSync(OBSERVATION_HISTORY_PATH)) {
            const history = JSON.parse(readFileSync(OBSERVATION_HISTORY_PATH, "utf8"));
            for (const entry of history?.observations ?? []) {
                if (typeof entry?.practiceSlug === "string" && entry.practiceSlug.trim()) {
                    slugs.add(entry.practiceSlug);
                }
            }
        }
    } catch (e) {
        console.error(`[pi-runner] observation history unreadable for composition: ${e.message}`);
    }
    return [...slugs].sort();
}

// Supersession is limited to unread thread keys present in this snapshot.
function stagedPreparedThreadKeys() {
    try {
        if (!existsSync(PREPARED_FEEDBACK_PATH)) return [];
        const prepared = JSON.parse(readFileSync(PREPARED_FEEDBACK_PATH, "utf8"));
        return [
            ...new Set(
                (prepared?.prepared ?? [])
                    .map((entry) => entry?.threadKey)
                    .filter((key) => typeof key === "string" && key.trim().length > 0),
            ),
        ];
    } catch (e) {
        console.error(`[pi-runner] prepared feedback unreadable for composition: ${e.message}`);
        return [];
    }
}

// Inline placement requires a citation inside the current diff.
function leanObservations(observations) {
    return observations.map((observation) => ({
        id: observation.id,
        practiceSlug: observation.practiceSlug,
        assessment: observation.assessment,
        severity: observation.severity,
        anchorable: observation.anchorable,
        citations: observation.citations.map((citation) => ({
            index: citation.index,
            sourceKind: citation.sourceKind,
            path: citation.path,
            side: citation.side,
            startLine: citation.startLine,
            endLine: citation.endLine,
            anchorable: citation.anchorable,
        })),
    }));
}

function persistComposedFeedback() {
    writeFileSync(FEEDBACK_PATH, JSON.stringify(composedFeedback, null, 2));
}

// The composer references admitted observations; it cannot author verdicts, citations, or locations.
function buildFeedbackTool(practiceSlugs, request, observations, preparedThreadKeys) {
    const enabledChannels = CHANNELS.filter((channel) => request.channels[channel].enabled);
    const placementKinds = request.inContextPlacementKinds || [];
    const usedPerChannel = Object.fromEntries(CHANNELS.map((channel) => [channel, 0]));
    const seen = new Set();
    const refuse = (text) => ({ content: [{ type: "text", text }], details: { stored: 0 } });

    return defineTool({
        name: "report_feedback",
        label: "Report Feedback",
        description:
            "Persist exactly one feedback unit for one channel. Call it as soon as one unit is ready, one call " +
            "per unit. This is an intervention, not a measurement: it takes no presence, assessment, severity " +
            "or confidence, and no citation you typed yourself.",
        parameters: {
            type: "object",
            additionalProperties: false,
            required: ["unit"],
            properties: {
                unit: {
                    type: "object",
                    additionalProperties: false,
                    required: ["channel", "practiceSlug", "basedOn", "action"],
                    properties: {
                        channel: {
                            type: "string",
                            enum: enabledChannels,
                            description: "Which surface this unit is for. Each has its own level and its own rules.",
                        },
                        practiceSlug: {
                            type: "string",
                            enum: practiceSlugs,
                            description: "The practice this unit is about. One unit per practice per channel.",
                        },
                        basedOn: {
                            type: "array",
                            minItems: 1,
                            items: { type: "string", minLength: 1 },
                            description:
                                "What this rests on: ids from this run's observations, and/or 'prior:<practiceSlug>' " +
                                "for a claim that rests on the staged record rather than on this run.",
                        },
                        action: {
                            type: "string",
                            enum: ACTIONS,
                            description:
                                "NEW to say something; SUPERSEDE to replace a message that is queued and unread; " +
                                "WITHHOLD to record, with a reason, that you decided to stay quiet.",
                        },
                        supersedesThreadKey: {
                            type: "string",
                            maxLength: 64,
                            description:
                                "Required for SUPERSEDE: the threadKey of an entry in inputs/history/prepared.json. " +
                                "You may not name a key that is not in that file.",
                        },
                        withholdReason: { type: "string", enum: WITHHOLD_REASONS },
                        title: {
                            type: "string",
                            maxLength: 255,
                            description: "Names the issue in a few words. Never names the person.",
                        },
                        body: {
                            type: "string",
                            maxLength: 8000,
                            description:
                                "IN_APP only: begin with what delta.json says changed, then name the cross-artifact work pattern; never quote a line. Markdown, read verbatim.",
                        },
                        nextStep: {
                            type: "string",
                            maxLength: 2000,
                            description:
                                "IN_CONTEXT: one edit before merging. IN_APP: one repeatable habit for the next piece of work. Name the missing decision, not a heading/template unless the practice requires one; never provide paste-ready prose.",
                        },
                        notes: {
                            type: "object",
                            additionalProperties: false,
                            required: ["situation", "capability", "evidenceSummary", "inConversationSignal"],
                            description:
                                "IN_CHAT only. Notes TO the mentor, which composes the whole turn itself, later, " +
                                "with the live conversation in front of it. Write what it needs to know, never a " +
                                "sentence for it to say: anything phrased as a line of dialogue will be spoken, and " +
                                "will sound like a script.",
                            properties: {
                                situation: {
                                    type: "string",
                                    maxLength: 4000,
                                    description:
                                        "What you saw: factual, specific, the artifacts named. Your words about them, " +
                                        "not words for them - third person, never addressed to the developer as 'you', " +
                                        "and never a judgement of the person.",
                                },
                                capability: {
                                    type: "string",
                                    maxLength: 2000,
                                    description:
                                        "The understanding or self-check this conversation should support. State the capability, not a solution such as a required heading/template, and not a question, script, diagnosis, or fixed tactic.",
                                },
                                evidenceSummary: {
                                    type: "string",
                                    maxLength: 4000,
                                    description:
                                        "A concise account of the artifacts and observations that ground this note. " +
                                        "Summarise rather than inventing a quote; the original observation evidence is " +
                                        "staged separately for the mentor to inspect.",
                                },
                                inConversationSignal: {
                                    type: "string",
                                    maxLength: 2000,
                                    description:
                                        "A sign detectable before the conversation ends: a distinction, decision, question, or self-check the developer can articulate. Not a promise, future artifact, message text, or compliance target.",
                                },
                            },
                        },
                        placement: {
                            description:
                                "IN_CONTEXT only. DIFF places a note at one verified observation citation. " +
                                "ARTIFACT places it in the issue or change summary without inventing a line.",
                            oneOf: placementKinds.map((kind) =>
                                kind === "DIFF"
                                    ? {
                                          type: "object",
                                          additionalProperties: false,
                                          required: ["kind", "observationId", "citationIndex"],
                                          properties: {
                                              kind: { type: "string", enum: ["DIFF"] },
                                              observationId: { type: "string", minLength: 1 },
                                              citationIndex: { type: "integer", minimum: 0 },
                                          },
                                      }
                                    : {
                                          type: "object",
                                          additionalProperties: false,
                                          required: ["kind"],
                                          properties: { kind: { type: "string", enum: ["ARTIFACT"] } },
                                      },
                            ),
                        },
                    },
                },
            },
        },
        execute: async (_toolCallId, params) => {
            if (!compositionAdmitted) {
                return refuse("Feedback composition opens only after Java admits the completed observations.");
            }
            const unit = params.unit;
            const observationsById = new Map(observations.map((observation) => [observation.id, observation]));
            const bounds = request.channels[unit.channel];
            if (!bounds?.enabled) {
                return refuse(`${unit.channel} is not a lane this run may write for; skipped.`);
            }
            const key = `${unit.channel}:${unit.practiceSlug}`;
            if (seen.has(key)) {
                return refuse(`Already have a ${unit.channel} unit for ${unit.practiceSlug}; skipped.`);
            }
            if (usedPerChannel[unit.channel] >= bounds.maxUnits) {
                return refuse(`${unit.channel} cap of ${bounds.maxUnits} reached; skipped.`);
            }
            const rejection = validateUnit(unit, observationsById, preparedThreadKeys, placementKinds);
            if (rejection) {
                return refuse(rejection);
            }
            seen.add(key);
            usedPerChannel[unit.channel]++;
            composedFeedback.units.push(unit);
            persistComposedFeedback();
            return {
                content: [
                    {
                        type: "text",
                        text: `Stored a ${unit.channel} unit for ${unit.practiceSlug} (${unit.action}). ${usedPerChannel[unit.channel]}/${bounds.maxUnits} used on that lane.`,
                    },
                ],
                details: { stored: 1, channel: unit.channel, total: composedFeedback.units.length },
            };
        },
    });
}

// Enforce snapshot-dependent constraints here for fast model correction; Java rechecks them.
function validateUnit(unit, observationsById, preparedThreadKeys, placementKinds) {
    const invalidEvidence = unit.basedOn.find((reference) => {
        if (reference === `prior:${unit.practiceSlug}`) return false;
        return observationsById.get(reference)?.practiceSlug !== unit.practiceSlug;
    });
    if (invalidEvidence) {
        return `Evidence '${invalidEvidence}' does not name an admitted observation for ${unit.practiceSlug}; skipped.`;
    }
    if (unit.action === "WITHHOLD") {
        if (!unit.withholdReason) return "WITHHOLD needs a withholdReason; skipped.";
        return null;
    }
    if (!unit.title?.trim()) return "A unit that is not a WITHHOLD needs a title; skipped.";
    if (unit.action === "SUPERSEDE") {
        if (!unit.supersedesThreadKey) return "SUPERSEDE needs a supersedesThreadKey; skipped.";
        if (!preparedThreadKeys.includes(unit.supersedesThreadKey)) {
            return `No queued message has threadKey '${unit.supersedesThreadKey}'; it must come from inputs/history/prepared.json. Skipped.`;
        }
    }
    if (unit.channel === "IN_CHAT") {
        if (unit.body || unit.nextStep) {
            return "IN_CHAT takes notes{situation,capability,evidenceSummary,inConversationSignal}, not body/nextStep - nothing on this lane is read out; skipped.";
        }
        if (!unit.notes?.situation?.trim()) return "IN_CHAT needs notes.situation; skipped.";
        if (!unit.notes?.capability?.trim()) return "IN_CHAT needs notes.capability; skipped.";
        if (!unit.notes?.evidenceSummary?.trim()) return "IN_CHAT needs notes.evidenceSummary; skipped.";
        if (!unit.notes?.inConversationSignal?.trim()) return "IN_CHAT needs notes.inConversationSignal; skipped.";
        if (unit.placement) return "Only IN_CONTEXT units may carry a placement; skipped.";
        return null;
    }
    if (unit.notes) return "Only IN_CHAT units may carry a notes block; skipped.";
    if (!unit.nextStep?.trim()) return `${unit.channel} needs a nextStep; skipped.`;
    if (unit.channel === "IN_APP") {
        if (!unit.body?.trim()) return "IN_APP needs a body; skipped.";
        if (unit.placement) return "Only IN_CONTEXT units may carry a placement; skipped.";
        const normalizedBody = normalizeQuotedText(unit.body);
        const repeatsCurrentEvidence = unit.basedOn.some((id) =>
            (observationsById.get(id)?.citations ?? []).some((citation) => {
                const quote = normalizeQuotedText(citation.quote ?? "");
                return quote.length >= 12 && normalizedBody.includes(quote);
            }),
        );
        if (repeatsCurrentEvidence) {
            return "IN_APP describes a cross-artifact pattern; do not copy a current artifact quote into it. Skipped.";
        }
        return null;
    }
    if (unit.body) return "IN_CONTEXT takes title, placement, and nextStep only; skipped.";
    if (!unit.placement) return "IN_CONTEXT needs a DIFF or ARTIFACT placement; skipped.";
    if (!placementKinds.includes(unit.placement.kind)) {
        return `${unit.placement.kind} placement is unavailable on this artifact; skipped.`;
    }
    if (unit.placement.kind === "ARTIFACT") {
        if (unit.placement.observationId != null || unit.placement.citationIndex != null) {
            return "ARTIFACT placement takes no observationId or citationIndex; skipped.";
        }
        const grounded = unit.basedOn.some((id) => observationsById.get(id)?.practiceSlug === unit.practiceSlug);
        if (!grounded) {
            return "ARTIFACT placement must be based on a current observation for this practice; skipped.";
        }
        return null;
    }
    if (unit.placement.kind !== "DIFF") return "Unknown IN_CONTEXT placement kind; skipped.";
    if (!unit.placement.observationId || !Number.isInteger(unit.placement.citationIndex)) {
        return "DIFF placement needs observationId and citationIndex; skipped.";
    }
    const observation = observationsById.get(unit.placement.observationId);
    if (!observation) return `No observation '${unit.placement.observationId}' in this run; skipped.`;
    const citation = observation.citations[unit.placement.citationIndex];
    if (!citation) return `Observation '${observation.id}' has no citation ${unit.placement.citationIndex}; skipped.`;
    if (!citation.anchorable) {
        return `Citation ${unit.placement.citationIndex} of '${observation.id}' is not on this change's diff, so no note can be placed on it. Skipped.`;
    }
    return null;
}

function normalizeQuotedText(value) {
    return value
        .normalize("NFKC")
        .replace(/[“”„‟]/g, '"')
        .replace(/[‘’‚‛]/g, "'")
        .replace(/\s+/g, " ")
        .trim()
        .toLowerCase();
}

function buildCompositionTurn(request, observations) {
    const lanes = CHANNELS.filter((channel) => request.channels[channel].enabled)
        .map((channel) => `${channel} (at most ${request.channels[channel].maxUnits})`)
        .join(", ");
    const closed = CHANNELS.filter((channel) => !request.channels[channel].enabled);
    const anchorable = observations.filter((observation) => observation.anchorable).length;
    return (
        `## This turn\n` +
        `The review just finished. Its ${observations.length} measurement(s) are in ` +
        `\`work/composition/observations.json\`; ${anchorable} of them cite a line inside this change and can ` +
        `therefore carry a note on the work. Read that file first, then the history.\n\n` +
        `Lanes open this turn: ${lanes}.` +
        (closed.length > 0 ? ` Closed this turn, so write nothing for them: ${closed.join(", ")}.` : "") +
        (request.channels.IN_CONTEXT.enabled
            ? ` IN_CONTEXT placements available here: ${request.inContextPlacementKinds.join(", ")}.`
            : "") +
        `\nA pattern claim needs at least ${request.minDistinctArtifacts} distinct pieces of work.\n\n` +
        `Persist each unit with report_feedback as soon as it is ready. Writing nothing on a lane is a ` +
        `correct and common outcome; say in one line why, and stop.`
    );
}

async function admitObservations() {
    const response = await fetch(`${process.env.LLM_PROXY_URL}/admit-observations`, {
        method: "POST",
        headers: { authorization: `Bearer ${process.env.LLM_PROXY_TOKEN}`, "content-type": "application/json" },
        body: JSON.stringify({ schemaVersion: 1, observations: reviewState.observations }),
    });
    if (!response.ok) throw new Error(`observation admission failed: HTTP ${response.status}`);
    const admitted = await response.json();
    if (
        admitted?.schemaVersion !== 1 ||
        typeof admitted?.admissionDigest !== "string" ||
        !Array.isArray(admitted?.observations)
    ) {
        throw new Error("observation admission returned an invalid contract");
    }
    reviewState.observations.splice(0, reviewState.observations.length, ...admitted.observations);
    admissionDigest = admitted.admissionDigest;
    compositionAdmitted = true;
    composedFeedback.admissionDigest = admissionDigest;
    composedFeedback.observations = leanObservations(reviewState.observations);
    mkdirSync(`${CWD}/work/composition`, { recursive: true });
    writeFileSync(COMPOSITION_OBSERVATIONS_PATH, JSON.stringify({ observations: reviewState.observations }, null, 2));
}

async function main() {
    console.error(`[pi-runner] Embedded SDK mode`);
    console.error(
        `[pi-runner] Budget: total=${AGENT_BUDGET_MS}ms, initial=${INITIAL_TIMEOUT_MS}ms (soft=${SOFT_TIMEOUT_MS}ms), retry=${RETRY_TIMEOUT_MS}ms`,
    );

    // Pi filters custom tools through this allowlist; omit filesystem mutation tools.
    const settingsManager = SettingsManager.create(CWD, AGENT_DIR);
    const sessionManager = SessionManager.inMemory();
    const authStorage = AuthStorage.create();
    const modelRegistry = ModelRegistry.create(authStorage);

    // Pi 0.74.x bug: createAgentSession.findInitialModel runs before the extension runner drains
    // pending registrations into the model registry. Register the hephaestus provider directly
    // here so the session resolves a real model on first prompt. Config (protocol/model/capability)
    // comes from the server-written pi-provider.json; baseUrl/token come from the sandbox env
    // (shared with pi-mentor-runner.mjs via pi-provider.mjs).
    const providerConfig = loadProviderConfig(CWD);
    const registered = registerHephaestusProvider(modelRegistry, providerConfig);
    if (registered) {
        console.error(
            `[pi-runner] registered hephaestus provider: apiProtocol=${providerConfig.apiProtocol} model=${providerConfig.modelId}`,
        );
    } else {
        console.error(`[pi-runner] hephaestus provider NOT registered — missing pi-provider.json or proxy env vars`);
    }

    const compositionRequest = loadCompositionRequest();
    const preparedThreadKeys = stagedPreparedThreadKeys();
    const feedbackTool = compositionRequest
        ? buildFeedbackTool(composablePracticeSlugs(), compositionRequest, reviewState.observations, preparedThreadKeys)
        : null;
    const { session, extensionsResult } = await createAgentSession({
        cwd: CWD,
        agentDir: AGENT_DIR,
        tools: ["read", "bash", "grep", "report_observation", ...(feedbackTool ? ["report_feedback"] : [])],
        customTools: [reportObservationTool, ...(feedbackTool ? [feedbackTool] : [])],
        sessionManager,
        settingsManager,
        authStorage,
        modelRegistry,
    });
    // Fail closed: Pi otherwise silently falls back to a built-in provider.
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

    async function completeWithAdmittedComposition() {
        measurementClosed = true;
        await admitObservations();
        const result = JSON.parse(readFileSync(RESULT_PATH, "utf8"));
        result.admissionDigest = admissionDigest;
        writeFileSync(RESULT_PATH, JSON.stringify(result));
        if (!compositionRequest || reviewState.observations.length === 0) return;
        const instructions = readFileSync(COMPOSER_PROMPT_PATH, "utf8");
        const compositionTimer = setTimeout(() => {
            console.error(`[pi-runner] Composition timeout — preserving observations and composed units so far`);
            session.abort().catch((error) => console.error(`[pi-runner] composition abort failed: ${error.message}`));
        }, COMPOSITION_TIMEOUT_MS);
        try {
            await session.prompt(
                `${instructions}\n\n${buildCompositionTurn(compositionRequest, reviewState.observations)}`,
            );
        } finally {
            clearTimeout(compositionTimer);
        }
        persistComposedFeedback();
    }


    let softTimeoutFired = false;
    let hardAborted = false;
    let prevUsage = null;

    const softTimer = setTimeout(() => {
        softTimeoutFired = true;
        console.error(`[pi-runner] Soft timeout fired — nudging agent to persist review state`);
        const steerMessage =
            `Stop analyzing and persist output now. ` +
            `Use report_observation immediately for any observation you already have, one observation per call. ` +
            PERSIST_DISCIPLINE;
        session.steer(steerMessage).catch((err) => console.error(`[pi-runner] steer failed: ${err.message}`));
    }, SOFT_TIMEOUT_MS);

    const hardTimer = setTimeout(() => {
        hardAborted = true;
        console.error(`[pi-runner] Hard timeout — aborting agent`);
        session.abort().catch((err) => console.error(`[pi-runner] abort failed: ${err.message}`));
    }, INITIAL_TIMEOUT_MS);

    const events = [];
    const streamUsage = newUsageLedger();
    const unsubscribe = session.subscribe((event) => {
        if (event.type === "tool_execution_start") {
            console.error(`[pi-runner] tool: ${event.toolName ?? "?"}`);
        }
        if (event.type === "message_end" && event.message?.role === "assistant") {
            // Compaction removes messages but does not undo their token usage.
            addAssistantUsage(streamUsage, event.message);
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

    const allSlugs = loadPracticeSlugs();
    const batchSize = Number(process.env.PI_PRACTICE_BATCH_SIZE) || 6;
    const groups = loadPracticeGroups();
    const batches = [];
    if (groups.length > 0) {
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
                    ? `${prompt}\n\n## Scope for this turn\n${readHint} and evaluate ONLY these practices, persisting each with report_observation (one call per observation): ${batch.join(", ")}.`
                    : `Continue the SAME review. Using the diff and context you ALREADY read (do NOT re-read the diff), ${readHint} and evaluate ONLY these practices, persisting each with report_observation (one call per observation): ${batch.join(", ")}.`;
            try {
                await session.prompt(batchPrompt);
            } catch (err) {
                console.error(`[pi-runner] turn ${bi + 1}/${batches.length} prompt error: ${err.message}`);
                if (hardAborted) break;
            }
            console.error(`[pi-runner] turn ${bi + 1}/${batches.length} complete (slugs=${batch.length})`);
        }

        if (!hardAborted && allSlugs.length > 0) {
            const covered = new Set(reviewState.observations.map((f) => f.practiceSlug).filter(Boolean));
            const missing = allSlugs.filter((s) => !covered.has(s));
            if (missing.length > 0) {
                console.error(`[pi-runner] Coverage gate: ${missing.length} unreported -> ${missing.join(", ")}`);
                const gatePrompt =
                    `Coverage check. You have NOT yet reported an observation for these practices: ${missing.join(", ")}. ` +
                    `Read inputs/practices/<slug>.md for each and evaluate it against the SAME diff/context you already read ` +
                    `(do NOT re-read the diff). Persist an observation for EVERY one with report_observation, one call per observation ` +
                    `— choose a BEHAVIOR_* outcome when evidence settles the claim. Use NO_REVIEW_OCCASION only when a prerequisite situation explicitly named by the practice did not occur; target-behaviour absence is BEHAVIOR_ABSENT_*, never a decline. Use INSUFFICIENT_EVIDENCE when required evidence is unavailable. Fill exactly the evidence branch the tool schema requires.`;
                try {
                    await session.prompt(gatePrompt);
                } catch (err) {
                    console.error(`[pi-runner] coverage-gate prompt error: ${err.message}`);
                }
                const stillMissing = allSlugs.filter(
                    (s) => !new Set(reviewState.observations.map((f) => f.practiceSlug)).has(s),
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
    const initialUsage = extractUsageFromSession(session.state, streamUsage);
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

    const resultFileSource = resolveResultFile();

    if (resultFileSource === "agent") {
        console.error(`[pi-runner] SUCCESS: result.json valid after initial run`);
        await completeWithAdmittedComposition();
        unsubscribe();
        process.exit(0);
    }
    if (resultFileSource === "tool-state") {
        console.error(`[pi-runner] SUCCESS: composed result.json from persisted tool state after initial run`);
        await completeWithAdmittedComposition();
        unsubscribe();
        process.exit(0);
    }


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
            await completeWithAdmittedComposition();
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

    let retryPrompt;
    if (softTimeoutFired || hardAborted) {
        retryPrompt =
            `You ran out of time before finalizing the review. ` +
            `Do NOT restart analysis from scratch. Do NOT read more files. ` +
            `Persist every remaining justified observation with report_observation immediately, one observation per call. ` +
            PERSIST_DISCIPLINE +
            ` ` +
            scaffold;
    } else if (agentText) {
        retryPrompt =
            `You completed analysis but did not persist the final review output. ` +
            `Do NOT read any more files. Persist the remaining observations with report_observation NOW, one observation per call. ` +
            PERSIST_DISCIPLINE +
            ` ` +
            scaffold;
    } else {
        retryPrompt =
            `You did not persist the review output. The review will fail unless you persist it NOW. ` +
            `Use your analysis from above. Do NOT read more files. Persist observations with report_observation immediately, one observation per call. ` +
            PERSIST_DISCIPLINE +
            ` ` +
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
    const retryUsage = extractUsageFromSession(session.state, streamUsage);
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

    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: result.json valid after retry`);
        await completeWithAdmittedComposition();
        unsubscribe();
        process.exit(0);
    }

    maybeWriteResultFile();
    if (checkResultFile()) {
        console.error(`[pi-runner] SUCCESS: composed result.json from persisted tool state after retry`);
        await completeWithAdmittedComposition();
        unsubscribe();
        process.exit(0);
    }

    if (tryRescueFromTextResponse(session.state)) {
        console.error(`[pi-runner] SUCCESS: rescued valid JSON from text`);
        await completeWithAdmittedComposition();
        unsubscribe();
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
