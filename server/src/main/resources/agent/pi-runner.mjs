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

import {
    ASSESSMENT_DESCRIPTIONS,
    ASSESSMENT_VALUES,
    PRESENCE_DESCRIPTIONS,
    PRESENCE_VALUES,
    SEVERITY_DESCRIPTIONS,
    SEVERITY_VALUES,
    carriesValence,
    citationMatchesArtifact,
    dedupeKeyForFinding,
    describeVocabulary,
    normalizeFinding,
    validateEvidenceSources,
    validateSearchScope,
    validateInapplicabilityScope,
} from "./pi-finding-normalize.mjs";
import { loadProviderConfig, registerHephaestusProvider } from "./pi-provider.mjs";
import { addAssistantUsage, extractUsageFromSession, newUsageLedger } from "./pi-runner-usage.mjs";
import {
    COMPOSITION_MIN_BUDGET_MS,
    compositionBudgetMs,
    deriveTimeouts,
    shouldCompose,
} from "./pi-runner-timings.mjs";

const OUTPUT = "/workspace/out";
const CWD = "/workspace";
const RESULT_PATH = `${OUTPUT}/result.json`;
const REVIEW_STATE_PATH = `${OUTPUT}/review-state.json`;
// The feedback-composition stage (see feedback-composer.md). Mirrors SandboxLayout:
// FEEDBACK_COMPOSITION_PATH / FEEDBACK_FILENAME / FEEDBACK_COMPOSER_PROMPT_FILENAME.
const COMPOSITION_REQUEST_PATH = `${CWD}/inputs/feedback-composition.json`;
const FEEDBACK_PATH = `${OUTPUT}/feedback.json`;
const COMPOSER_PROMPT_PATH = `${CWD}/feedback-composer.md`;
const OBSERVATION_HISTORY_PATH = `${CWD}/inputs/history/observations.json`;
const PREPARED_FEEDBACK_PATH = `${CWD}/inputs/history/prepared.json`;
// This run's own measurements, projected for the composer. Under work/ because the composer must be
// handed them rather than left to grep out/review-state.json for them, and because a projection of
// rows that are already collected has no business being collected a second time.
const COMPOSITION_WORK_DIR = `${CWD}/work/composition`;
const COMPOSITION_OBSERVATIONS_PATH = `${COMPOSITION_WORK_DIR}/observations.json`;
// The source kind a citation must carry for its location to exist inside this change's diff, and
// therefore for an inline note to be placeable on it. Mirrors PullRequestReviewHandler.filterByDiffScope,
// which asks the same question in Java and today answers it by DELETING the observation.
const DIFF_SOURCE_KIND = "scm.pull-request.diff";
const AGENT_BUDGET_MS = Number(process.env.AGENT_BUDGET_MS);
if (!Number.isFinite(AGENT_BUDGET_MS) || AGENT_BUDGET_MS <= 0) {
    throw new Error(`AGENT_BUDGET_MS env var is required and must be a positive number, got: ${process.env.AGENT_BUDGET_MS}`);
}
const AGENT_DIR = process.env.PI_CODING_AGENT_DIR;
if (!AGENT_DIR) {
    throw new Error("PI_CODING_AGENT_DIR env var is required");
}
const {
    initialMs: INITIAL_TIMEOUT_MS,
    retryMs: RETRY_TIMEOUT_MS,
    softNudgeMs: SOFT_TIMEOUT_MS,
    // Ceiling for the feedback-composition stage. Never additive to the review's own allowance: the
    // stage runs only from what the review left unspent, and only when that clears the floor (see main()).
    compositionCeilingMs: COMPOSITION_TIMEOUT_MS,
} = deriveTimeouts(AGENT_BUDGET_MS);

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

const manifest = JSON.parse(readFileSync(`${CWD}/inputs/manifest.json`, "utf8"));
const availableSourceKinds = new Set(
    manifest.sources
        .filter((source) => source.state.availability === "AVAILABLE")
        .map((source) => source.kind),
);
const artifactSources = new Map(
    manifest.sources.flatMap((source) =>
        (source.artifacts ?? []).map((artifact) => [artifact.path, source.kind]),
    ),
);
const practiceIndex = JSON.parse(readFileSync(`${CWD}/inputs/practices/index.json`, "utf8"));
const admittedPractices = new Set(practiceIndex.map((practice) => practice.slug));
// The sources this practice holds EXHAUSTIVE — the domain over which it is allowed to assert an
// absence, and therefore the domain a search must cover before ABSENT is sound. The only per-practice
// source list left: what may be CITED is what the manifest says this run staged, which is the same
// answer for every practice.
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
    findings: [],
    findingKeys: [],
};
// The tool schema the model sees is generated from the SAME vocabulary the normalizer validates
// against, so the SDK boundary can no longer accept a value the normalizer rejects (or, as happened
// with INCONCLUSIVE, reject one the orchestrator instructs the model to emit).
//
// Each enum carries the discriminator for every one of its values, because an enum of bare words is a
// choice the model cannot make: if a person reading the schema could not say which of two values a case
// belongs to, neither can it, and it will settle on whichever value reads as the safe default. That is
// measurable — a live corpus produced NOT_APPLICABLE 61% of the time and INCONCLUSIVE not once. The
// wording lives beside the vocabulary in pi-finding-normalize.mjs so a value can never be added without
// one; here it is only rendered.
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
        "facts land in the same band every run.\n" + describeVocabulary(SEVERITY_VALUES, SEVERITY_DESCRIPTIONS),
};
// Where you looked, for what, and where the looking stopped. REQUIRED when presence=ABSENT: "I did
// not find it" only means "it is not there" if the corpus searched was the one the claim ranges over,
// and that is a fact only the searcher can report.
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
// What the practice looks for, and the fact about THIS work that rules it out. REQUIRED when
// presence=NOT_APPLICABLE: that value is a claim about the developer's work — "there was nothing here to
// see" — and it is the only presence with no proof attached, which is exactly why uncertainty drains into
// it. Naming the ground is what separates it from an abstention. If you cannot name one, say INCONCLUSIVE.
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
// The question the evidence left open, and what would have closed it. REQUIRED when
// presence=INCONCLUSIVE, which until now was the one value that cost nothing to say and appeared in no
// schema at all.
//
// Measured, not assumed. On a bench of 12 real merge requests plus two constructed undecidable ones,
// moving evidence ahead of the verdict dropped INCONCLUSIVE from 6/6 of the undecidable cases to 1/6 —
// once the model had quoted body text it read the body as settling the question. Adding this block put it
// back to 6/6 with no loss of agreement elsewhere. A required sub-schema turns out to be a signpost as
// much as a toll: it is how the model finds a value it otherwise walks past.
//
// It also makes the answer useful. "I could not tell" is a dead end; "I could not tell, and here is what
// would have decided it" is a statement about missing evidence that a practice author can act on.
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
//
// `guidance` is NOT required. The composition stage authors what the developer reads, so guidance is now
// only the fallback body of an in-context note. Requiring it on every finding made this step invent a
// next step for a strength, for a practice with no subject here, and for a question it could not settle
// — a standing pull toward "something is wrong" on the three answers that assert nothing is.
const findingSchema = {
    type: "object",
    additionalProperties: false,
    required: ["practiceSlug", "title", "presence", "confidence", "evidence", "reasoning"],
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
        reasoning: {
            type: "string",
            minLength: 1,
            description:
                "What you observed, in plain prose a developer reads verbatim: the behaviour you looked for, " +
                "where you looked, and what the evidence showed. For INCONCLUSIVE, say here what would have " +
                "decided it.",
        },
        guidance: {
            type: "string",
            description:
                "OPTIONAL — one concrete next step, and only where there is one to take: a BAD finding, or a " +
                "strength worth pushing further. Omit it entirely for NOT_APPLICABLE and INCONCLUSIVE, and for " +
                "any finding where the honest next step is none. Do not invent one to fill the field.",
        },
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
    // assessment is required only for a presence that carries valence; NOT_APPLICABLE and
    // INCONCLUSIVE are both silence and must NOT carry one (mirrors Presence.carriesValence()).
    if (carriesValence(f.presence) && typeof f.assessment !== "string") return false;
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
        p.findings.every(isValidFinding)
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
        if (!valid) return false;
        const normalized = data.findings.map(normalizeAndValidateFinding);
        writeFileSync(RESULT_PATH, JSON.stringify({ findings: normalized }, null, 2));
        return true;
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

/**
 * Settle whether this attempt has a usable result.json, and say where it came from: "agent" when the
 * agent wrote one that validates, "tool-state" when one had to be composed from the persisted
 * report_finding calls, null when neither works and the retry is owed its turn.
 *
 * Both branches are the ones the exit path used to run inline; naming the outcome lets the answer be
 * computed once and read by both the budget arithmetic and the exit.
 */
function resolveResultFile() {
    if (checkResultFile()) return "agent";
    if (maybeWriteResultFile() && checkResultFile()) return "tool-state";
    return null;
}


function appendFindings(findings) {
    let inserted = 0;
    let duplicates = 0;
    const seen = new Set(reviewState.findingKeys);
    for (const rawFinding of findings) {
        const finding = normalizeAndValidateFinding(rawFinding);
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

function normalizeAndValidateFinding(rawFinding) {
    const finding = normalizeFinding(rawFinding);
    if (!admittedPractices.has(finding.practiceSlug)) throw new Error(`unknown practice '${finding.practiceSlug}'`);
    validateEvidenceSources(finding, availableSourceKinds, artifactSources);
    validateSearchScope(
        finding,
        practiceExhaustiveSources.get(finding.practiceSlug) ?? new Set(),
        availableSourceKinds,
    );
    validateInapplicabilityScope(finding, availableSourceKinds);
    for (const citation of finding.evidence.citations) {
        const content = readFileSync(`${CWD}/${citation.artifactPath}`, "utf8");
        if (!citationMatchesArtifact(citation, content)) {
            throw new Error(`citation does not match artifact location '${citation.artifactPath}'`);
        }
    }
    return finding;
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

// ── Feedback composition stage ────────────────────────────────────────────────
//
// A SECOND LLM turn, after the review's measurements are final, that decides what — if anything — is
// worth saying to this developer now, on which surface, and in what words. Measurement and intervention
// are separate acts; this is where the separation lives in the runtime.
//
// One turn writes for every enabled lane, because the per-lane rules are only statable in contrast:
// "the note on the work says this, so the private page must not say it again" cannot be expressed by a
// stage that can see one lane at a time.
//
// Strictly additive. It runs in its OWN session, only once the review has durable state and only from
// time the review did not need, and every failure inside it is swallowed: a review that measured
// correctly is a successful review whether or not anything was composed from it. Nothing here can
// touch reviewState or the exit code.

const CHANNELS = ["IN_CONTEXT", "IN_APP", "IN_CHAT"];
const ACTIONS = ["NEW", "SUPERSEDE", "WITHHOLD"];
const WITHHOLD_REASONS = ["NO_MATERIAL_CHANGE", "ALREADY_SAID", "BELOW_BAR"];

// What the stage produces, and everything Java needs to check it against. The observations and the
// thread keys are echoed rather than re-derived server-side on purpose: they are the exact inputs the
// composer was shown, so a unit that names one of them can be validated against what was actually on
// the table rather than against a re-query that may have moved.
const composedFeedback = { observations: [], preparedThreadKeys: [], units: [] };

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
        return {
            channels,
            minDistinctArtifacts: Math.max(2, Number(request.minDistinctArtifacts) || 2),
        };
    } catch (e) {
        console.error(`[pi-runner] composition request unreadable: ${e.message}`);
        return null;
    }
}

// The practices a message may be about: what this run evaluated, plus what this developer's recorded
// history mentions. A pattern routinely predates the current run, so restricting to the run's own
// practice set would make the commonest true pattern unsayable. The server resolves the slug to this
// person's own measurements regardless, so an unknown one simply finds no evidence.
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

// The messages already written for this person and not yet read. A composer may replace one of these,
// and only one of these: the key it names must be a key it was shown, or it is inventing a target.
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

// A citation can carry an inline note only if it locates a line inside THIS change. Everything else is
// a true observation about work that is not on the diff, which is a reason to route it elsewhere and
// never a reason to drop it.
function citationIsAnchorable(citation) {
    return (
        citation?.sourceKind === DIFF_SOURCE_KIND &&
        typeof citation?.path === "string" &&
        citation.path.trim().length > 0 &&
        Number.isInteger(citation?.startLine)
    );
}

// This run's measurements, as the composer sees them. Ids are positional and stable for the run: the
// composer names one, and Java resolves it back through the same list, echoed alongside the units.
function projectObservations() {
    return reviewState.findings.map((finding, index) => {
        const citations = (finding.evidence?.citations ?? []).map((citation, citationIndex) => ({
            index: citationIndex,
            sourceKind: citation.sourceKind,
            path: citation.path,
            side: citation.side ?? null,
            startLine: citation.startLine ?? null,
            endLine: citation.endLine ?? null,
            quote: citation.quote,
            anchorable: citationIsAnchorable(citation),
        }));
        return {
            id: `obs-${index}`,
            practiceSlug: finding.practiceSlug,
            title: finding.title,
            presence: finding.presence,
            assessment: finding.assessment ?? null,
            severity: finding.severity ?? null,
            confidence: finding.confidence ?? null,
            reasoning: finding.reasoning,
            anchorable: citations.some((citation) => citation.anchorable),
            citations,
        };
    });
}

// Java resolves the anchor from the citation, so it needs the locations and nothing else. The quote and
// the reasoning stay out: they are already on the observation rows, and re-collecting them would put a
// second copy of every measurement into the job's output column.
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

// Structurally distinct from report_finding, and that is the point: no presence, no assessment, no
// severity, no confidence, no citations the composer typed. An intervention that could carry a verdict
// would eventually be read back as one, and an anchor the composer invented would put a note on a line
// that does not exist — so it names an observation and a citation index, never a path and never a line.
function buildFeedbackTool(practiceSlugs, request, observations, preparedThreadKeys) {
    const observationsById = new Map(observations.map((observation) => [observation.id, observation]));
    const enabledChannels = CHANNELS.filter((channel) => request.channels[channel].enabled);
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
                                "IN_CONTEXT and IN_APP only. Markdown, read verbatim by the developer.",
                        },
                        nextStep: {
                            type: "string",
                            maxLength: 2000,
                            description:
                                "IN_CONTEXT and IN_APP only. One edit before merging, or one habit for next time.",
                        },
                        conversation: {
                            type: "object",
                            additionalProperties: false,
                            required: ["opener", "evidence", "target"],
                            description:
                                "IN_CHAT only. The move, not the script: the mentor still writes the words " +
                                "of the turn with the live conversation in front of it.",
                            properties: {
                                opener: {
                                    type: "string",
                                    maxLength: 2000,
                                    description:
                                        "A question about how they work, asked before anything is told. Read verbatim " +
                                        "when the mentor raises it.",
                                },
                                evidence: {
                                    type: "string",
                                    maxLength: 4000,
                                    description:
                                        "What you would show them once they have answered, and not before. The mentor " +
                                        "decides when.",
                                },
                                target: {
                                    type: "string",
                                    maxLength: 2000,
                                    description: "What this turn is trying to leave them able to do themselves.",
                                },
                            },
                        },
                        anchor: {
                            type: "object",
                            additionalProperties: false,
                            required: ["observationId", "citationIndex"],
                            description:
                                "IN_CONTEXT only. Names an observation and one of ITS citations; the file, side and " +
                                "line come from that citation, never from you.",
                            properties: {
                                observationId: { type: "string", minLength: 1 },
                                citationIndex: { type: "integer", minimum: 0 },
                            },
                        },
                    },
                },
            },
        },
        execute: async (_toolCallId, params) => {
            const unit = params.unit;
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
            const rejection = validateUnit(unit, observationsById, preparedThreadKeys);
            if (rejection) {
                return refuse(rejection);
            }
            seen.add(key);
            usedPerChannel[unit.channel]++;
            composedFeedback.units.push(unit);
            // Written on every call, like report_finding, so a stage killed by the watchdog still
            // leaves behind what it had already decided.
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

// The rules JSON Schema cannot state: which fields each channel and each action require, and that an
// anchor and a supersession target must both name something that was actually on the table. Java checks
// every one of these again — this side exists so the model is told at once, while it can still fix it.
function validateUnit(unit, observationsById, preparedThreadKeys) {
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
        if (unit.body || unit.nextStep) return "IN_CHAT takes conversation{opener,evidence,target}, not body/nextStep; skipped.";
        if (!unit.conversation?.opener?.trim()) return "IN_CHAT needs conversation.opener; skipped.";
        if (!unit.conversation?.evidence?.trim()) return "IN_CHAT needs conversation.evidence; skipped.";
        if (!unit.conversation?.target?.trim()) return "IN_CHAT needs conversation.target; skipped.";
        if (unit.anchor) return "Only IN_CONTEXT units may carry an anchor; skipped.";
        return null;
    }
    if (unit.conversation) return "Only IN_CHAT units may carry a conversation block; skipped.";
    if (!unit.body?.trim()) return `${unit.channel} needs a body; skipped.`;
    if (!unit.nextStep?.trim()) return `${unit.channel} needs a nextStep; skipped.`;
    if (unit.channel === "IN_APP") {
        if (unit.anchor) return "Only IN_CONTEXT units may carry an anchor; skipped.";
        return null;
    }
    if (!unit.anchor) return "An IN_CONTEXT unit is a note on a line, so it needs an anchor; skipped.";
    const observation = observationsById.get(unit.anchor.observationId);
    if (!observation) return `No observation '${unit.anchor.observationId}' in this run; skipped.`;
    const citation = observation.citations[unit.anchor.citationIndex];
    if (!citation) return `Observation '${observation.id}' has no citation ${unit.anchor.citationIndex}; skipped.`;
    if (!citation.anchorable) {
        return `Citation ${unit.anchor.citationIndex} of '${observation.id}' is not on this change's diff, so no note can be placed on it. Skipped.`;
    }
    return null;
}

async function runCompositionStage(sharedDeps, budgetMs) {
    const request = loadCompositionRequest();
    if (!request) {
        console.error(`[pi-runner] Composition stage: not requested for this run`);
        return null;
    }
    if (!existsSync(COMPOSER_PROMPT_PATH)) {
        console.error(`[pi-runner] Composition stage: ${COMPOSER_PROMPT_PATH} missing, skipping`);
        return null;
    }
    const practiceSlugs = composablePracticeSlugs();
    if (practiceSlugs.length === 0) {
        return null;
    }
    const observations = projectObservations();
    const preparedThreadKeys = stagedPreparedThreadKeys();
    composedFeedback.observations = leanObservations(observations);
    composedFeedback.preparedThreadKeys = preparedThreadKeys;
    mkdirSync(COMPOSITION_WORK_DIR, { recursive: true });
    writeFileSync(COMPOSITION_OBSERVATIONS_PATH, JSON.stringify({ observations }, null, 2));

    const instructions = readFileSync(COMPOSER_PROMPT_PATH, "utf8");
    const tool = buildFeedbackTool(practiceSlugs, request, observations, preparedThreadKeys);

    // A SEPARATE session, not another turn on the review's. Two reasons, both structural: the review's
    // conversation is a record of taking measurements, and re-sending it would both cost its full
    // token weight again and invite the composer to treat its own reasoning as evidence.
    const { session } = await createAgentSession({
        cwd: CWD,
        agentDir: AGENT_DIR,
        tools: ["read", "grep", "report_feedback"],
        customTools: [tool],
        sessionManager: SessionManager.inMemory(),
        settingsManager: sharedDeps.settingsManager,
        authStorage: sharedDeps.authStorage,
        modelRegistry: sharedDeps.modelRegistry,
    });

    // Subscribed for the same reason the review session is: this session is created with the same
    // settings, so it compacts too, and a compacted call is one the proxy has already billed upstream
    // while this stage reports nothing for it.
    const streamUsage = newUsageLedger();
    const unsubscribeUsage = session.subscribe((event) => {
        if (event.type === "message_end") {
            addAssistantUsage(streamUsage, event.message);
        }
    });

    let aborted = false;
    const timer = setTimeout(() => {
        aborted = true;
        console.error(`[pi-runner] Composition stage hard timeout — aborting`);
        session.abort().catch((err) => console.error(`[pi-runner] composition abort failed: ${err.message}`));
    }, budgetMs);
    const startMs = Date.now();
    try {
        await session.prompt(`${instructions}\n\n${buildCompositionTurn(request, observations)}`);
    } catch (err) {
        console.error(`[pi-runner] Composition stage prompt error: ${err.message}`);
    } finally {
        clearTimeout(timer);
        unsubscribeUsage();
    }
    // Always written, even empty: an empty payload is the stage saying it looked and composed nothing,
    // which is a different fact from the stage never having run.
    persistComposedFeedback();
    console.error(
        `[pi-runner] Composition stage: ${composedFeedback.units.length} unit(s) in ${((Date.now() - startMs) / 1000).toFixed(1)}s, aborted=${aborted}`,
    );
    return extractUsageFromSession(session.state, streamUsage);
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
        (closed.length > 0
            ? ` Closed this turn, so write nothing for them: ${closed.join(", ")}.`
            : "") +
        `\nA pattern claim needs at least ${request.minDistinctArtifacts} distinct pieces of work.\n\n` +
        `Persist each unit with report_feedback as soon as it is ready. Writing nothing on a lane is a ` +
        `correct and common outcome; say in one line why, and stop.`
    );
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
    `guidance is optional: write it only where there is a real next step — a BAD finding, or a strength worth ` +
    `pushing further — and leave it off entirely for NOT_APPLICABLE and INCONCLUSIVE rather than inventing one. ` +
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
    const streamUsage = newUsageLedger();
    const unsubscribe = session.subscribe((event) => {
        if (event.type === "tool_execution_start") {
            console.error(`[pi-runner] tool: ${event.toolName ?? "?"}`);
        }
        if (event.type === "message_end" && event.message?.role === "assistant") {
            // Counted here, not from session.messages at the end: compaction deletes messages, and a
            // deleted message took its tokens off the bill.
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
                    `— set presence (${PRESENCE_VALUES.join("/")}) and, unless it is NOT_APPLICABLE or INCONCLUSIVE, ` +
                    `assessment (GOOD/BAD). Ask the one question presence answers: is the behaviour this practice names ` +
                    `in the work? It is there = PRESENT; the occasion for it was here and it is not = ABSENT; the ` +
                    `occasion never arose = NOT_APPLICABLE. If you read the evidence and it does not settle the ` +
                    `question, say INCONCLUSIVE — do NOT reach for NOT_APPLICABLE, which claims there was nothing here ` +
                    `to see.`;
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

    // ── Is the review already finished? ──────────────────────────────────────
    //
    // Asked BEFORE composition, not after, because the answer decides how much time composition may
    // have. The retry below is the only claimant on RETRY_TIMEOUT_MS and it fires on exactly one
    // condition: no usable result.json. Settle that condition first and a healthy review stops reserving
    // an allowance nothing can spend.
    //
    // Safe to move ahead of the stage: the composer runs on its own session with its own tool set, which
    // does not include report_finding, so nothing between here and the exit can change reviewState or
    // the file.
    const resultFileSource = resolveResultFile();

    // ── Feedback composition ─────────────────────────────────────────────────
    //
    // The second LLM step: observations were the measurement, this turns them into an intervention for
    // the developer's practice pages. Three conditions, and the last is the whole rule: durable review
    // state to compose from; no hard abort, because a review that lost its turn needs its retry allowance
    // more than the practice pages need a message today; and enough of the budget still unspent to
    // finish a turn — counting the retry allowance as spoken for only while the retry can still fire.
    // Whether the soft nudge fired is not a condition — it is a steer that lands at 42.5% of the budget
    // and says nothing about what is left.
    // Isolated: any failure is logged and this review's outcome is untouched.
    const reviewStatePersisted = hasPersistedReviewState();
    const leftoverForCompositionMs = compositionBudgetMs({
        agentBudgetMs: AGENT_BUDGET_MS,
        elapsedMs: Date.now() - startMs,
        retryMs: RETRY_TIMEOUT_MS,
        compositionCeilingMs: COMPOSITION_TIMEOUT_MS,
        resultFileValid: resultFileSource !== null,
    });
    if (
        shouldCompose({
            hasPersistedReviewState: reviewStatePersisted,
            hardAborted,
            resultFileValid: resultFileSource !== null,
            budgetMs: leftoverForCompositionMs,
        })
    ) {
        try {
            const composeUsage = await runCompositionStage(
                { settingsManager, authStorage, modelRegistry },
                leftoverForCompositionMs,
            );
            if (composeUsage) {
                accumulateUsage(null, composeUsage);
                persistUsage();
            }
        } catch (err) {
            console.error(`[pi-runner] Composition stage failed (review unaffected): ${err.message}`);
        }
    } else {
        console.error(
            `[pi-runner] Composition stage skipped: reviewState=${reviewStatePersisted}, hardAbort=${hardAborted}, ` +
                `resultFile=${resultFileSource ?? "none"}, ` +
                `budget=${leftoverForCompositionMs}ms (floor ${COMPOSITION_MIN_BUDGET_MS}ms)`,
        );
    }

    if (resultFileSource === "agent") {
        console.error(`[pi-runner] SUCCESS: result.json valid after initial run`);
        unsubscribe();
        process.exit(0);
    }
    if (resultFileSource === "tool-state") {
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
