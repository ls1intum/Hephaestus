import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import nodePath from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import { isDeepStrictEqual } from "node:util";

import {
	type AgentSession,
	type AgentSessionEvent,
	type AgentToolResult,
	createAgentSession,
	createCodemodeExtension,
	DefaultResourceLoader,
	defineTool,
	ModelRuntime,
	SessionManager,
	SettingsManager,
	type ToolDefinition,
} from "@earendil-works/pi-coding-agent";

import {
	PUBLIC_REVIEW_RESOURCE_LOADER_OPTIONS,
	PUBLIC_REVIEW_TOOLS,
	SANDBOX_RESOURCE_LOADER_OPTIONS,
	SANDBOX_SETTINGS_MANAGER_OPTIONS,
} from "./pi-agent-sandbox.ts";
import { assessmentCacheExtension } from "./pi-assessment-cache.ts";
import {
	CHANGE_ROOT,
	checkedOutCommit,
	pinnedDiff,
	readChange,
	readDescription,
	readPinnedBlob,
	writeChangeView,
} from "./pi-change.ts";
import { errorText } from "./pi-error-text.ts";
import { folderCitationIndex } from "./pi-folder-index.ts";
import {
	addModelCall,
	type ModelCallTiming,
	type ModelFailure,
	type ModelFailureKind,
	modelFailure,
	newModelCallTiming,
} from "./pi-model-failure.ts";
import { PracticeCoverageLedger } from "./pi-practice-coverage.ts";
import { executionReport } from "./pi-practice-scripts-report.ts";
import { loadProviderConfig, reasoningSetting, registerHephaestusProvider } from "./pi-provider.ts";
import {
	CONVERSATION_THREAD,
	MAX_SUMMARY_CHARS,
	OUTCOME_DESCRIPTIONS,
	SEVERITY_DESCRIPTIONS,
	citesReviewedTurn,
	describeVocabulary,
	isRecord,
	normalizePracticeSlug,
	normalizeSubmission,
	resolveQuote,
	submissionCitations,
	validateEvidenceSources,
	validateInapplicabilityScope,
	validateSearchScope,
	type NormalizedCitation,
	type NormalizedSubmission,
} from "./pi-result-normalize.ts";
import {
	buildBrief,
	buildPrimarySourceReference,
	buildPublicReviewHistory,
	buildSameWorkContext,
} from "./pi-review-brief.ts";
import {
	type Work,
	deriveWindows,
	missingSlugs,
	planTurns,
	shouldRecordNow,
	spent,
	turnBudget,
} from "./pi-review-turns.ts";
import {
	ACTIONS,
	CHANNELS,
	type ComposedFeedbackEnvelope,
	type ComposedFeedbackUnit,
	type ComposedReview,
	type EvidenceRef,
	type InAppSupport,
	type InChatEligibility,
	PRIOR_ADVICE_REASONS,
	PRIVATE_CHANNELS,
	type PreparedFeedbackTarget,
	type ReviewPractice,
	REVIEW_CONTRACT_VERSION,
	REVIEW_TOOL_DESCRIPTION,
	type ReviewedResult,
	WITHHOLD_REASONS,
	buildReviewTurn,
	decidedByReview,
	evidenceRefs,
	namedEvidence,
	notReachedNote,
	priorAdviceWitnesses,
	priorPublicFeedback,
	publicResults,
	publicQualifications,
	qualificationEvidence,
	readInAppSupport,
	readInChatEligibility,
	readReview,
	prepareReviewArguments,
	reviewToolParameters,
	undeliverableUnits,
	validateFeedbackEvidence,
	workIdentity,
} from "./pi-runner-composition.ts";
import { outputPath } from "./pi-runner-output.ts";
import { isRetryableStatus, isTimeoutAbort, retrying } from "./pi-runner-retry.ts";
import {
	addAssistantUsage,
	extractUsageFromSession,
	newUsageLedger,
	outputTokensOf,
	type UsageReport,
} from "./pi-runner-usage.ts";
import {
	REVIEW_SESSION_ENTRY,
	type ReviewSessionBinding,
	type SessionPhase,
	stopSession,
} from "./pi-session-lifecycle.ts";
import { SUPPORTED_SCHEMA_VERSION, taskPaths, resolveTaskPaths } from "./pi-task-paths.ts";
import { hasText, isBlank } from "./pi-text.ts";
import { prepareResultArguments } from "./pi-tool-arguments.ts";
import { prepareTurnText } from "./pi-turn-context.ts";
import { MODEL_CONCURRENCY } from "./work/practice-scripts/lib/concurrency.ts";
import {
	boundModelsSchema,
	CANDIDATE_LIMITS,
	DIFF_SOURCE,
	OUTCOMES,
	parseCandidate,
	SEVERITIES,
	TREE_SOURCE,
	type BoundModels,
	type Candidate,
	type CodeCitationIdentity,
	type Coverage,
	type Omission,
	type Outcome,
	type ReviewOptions,
} from "./work/practice-scripts/lib/contract.ts";
import { parseDiff } from "./work/practice-scripts/lib/diff-parser.ts";
import {
	EvidenceReader,
	noReceipts,
	type EvidenceSources,
	type Receipts,
} from "./work/practice-scripts/lib/evidence-reader.ts";
import type { JobFiles } from "./work/practice-scripts/lib/ipc.ts";
import { renderReviewInput, resolveLeads } from "./work/practice-scripts/lib/leads.ts";
import { hostModels, ModelBudget } from "./work/practice-scripts/lib/models-host.ts";
import { UnratedError, type UnratedReason } from "./work/practice-scripts/lib/practice-script.ts";
import {
	notReached as notReachedOutcome,
	runPracticeProgram,
	type PracticeProgram,
	type ProgramOutcome,
} from "./work/practice-scripts/lib/program-host.ts";
import { InputReader } from "./work/practice-scripts/lib/script-inputs.ts";
import { abortReasonOf, RETURN_MS } from "./work/practice-scripts/lib/script-run.ts";
import type { DiffFile } from "./work/practice-scripts/lib/types.ts";

// Staged work notes survive isolated sessions and context compaction.

function parseJson(text: string): unknown {
	return JSON.parse(text);
}

function jsonArray(value: unknown): unknown[] {
	return Array.isArray(value) ? value : [];
}

function logValue(value: unknown): string {
	if (typeof value === "string") {
		return value;
	}
	if (value === undefined) {
		return "undefined";
	}
	return JSON.stringify(value);
}

/** SDK event arrays may be absent before completion or after abort. */
function listOrEmpty<T>(items: T[] | undefined): T[] {
	return items ?? [];
}

/** Accept array, single-item and serialized-list forms; validate each parsed item separately. */
function submittedList(
	value: unknown,
	itemKeys: ReadonlySet<string>,
): { items: unknown[]; repaired: boolean } | { error: string } {
	if (typeof value === "string") {
		// Only a string that does not parse is repaired: valid JSON is read as written.
		try {
			return { items: listOrItem(parseJson(value)), repaired: false };
		} catch {
			// Repaired below.
		}
		const repaired = repairedClosers(value, itemKeys);
		try {
			return { items: listOrItem(parseJson(repaired)), repaired: true };
		} catch (error) {
			// Where the parse stopped, shown: resending the same text cannot fix a fault nobody located.
			const at = /position (?<position>\d+)/u.exec(errorText(error))?.groups?.position;
			const near =
				at === undefined
					? ""
					: `; it breaks here: ${JSON.stringify(repaired.slice(Math.max(0, Number(at) - 60), Number(at)))} ⟵ ${JSON.stringify(repaired.slice(Number(at), Number(at) + 20))}`;
			return {
				error: `the list arrived as a string that is not valid JSON (${errorText(error)})${near}. Fix that spot — usually a quote inside a text left unescaped — or send the array itself`,
			};
		}
	}
	return { items: listOrItem(value), repaired: false };
}

/** The keys of a feedback unit that never belong to an object inside it. */
const UNIT_ITEM_KEYS: ReadonlySet<string> = new Set([
	"channel",
	"practiceSlug",
	"action",
	"basedOn",
	"title",
	"body",
	"nextStep",
	"withholdReason",
	"supersedesThreadKey",
]);

/** The list, or the one item that was sent where a list of them was asked for. */
function listOrItem(value: unknown): unknown[] {
	if (Array.isArray(value)) {
		return value;
	}
	return isRecord(value) ? [value] : [];
}

/**
 * Repair missing or unmatched closers without changing quoted strings; JSON.parse rejects other faults.
 * `itemKeys` are keys that belong to an item of the list and never to an object inside one: met inside
 * a nested object, they mean that object was left open, and it is closed before them.
 */
function repairedClosers(text: string, itemKeys: ReadonlySet<string>): string {
	const owed: string[] = [];
	let out = "";
	let changed = false;
	let inString = false;
	let escaped = false;
	for (let index = 0; index < text.length; index += 1) {
		const char = text[index] ?? "";
		if (inString) {
			out += char;
			if (escaped) {
				escaped = false;
			} else if (char === "\\") {
				escaped = true;
			} else if (char === '"') {
				inString = false;
			}
			continue;
		}
		if (char === '"') {
			inString = true;
		} else if (char === "," && owed.length > 2 && owed[0] === "]") {
			const key = followingKey(text, index);
			if (key !== null && itemKeys.has(key)) {
				while (owed.length > 2) {
					out += owed.pop() ?? "";
				}
				changed = true;
			}
		} else if (char === "{" || char === "[") {
			const comma = out.trimEnd().length - 1;
			if (char === "{" && owed.at(-1) === "}" && owed.includes("]") && out[comma] === ",") {
				let closers = "";
				while (owed.at(-1) === "}") {
					closers += owed.pop();
				}
				out = out.slice(0, comma) + closers + out.slice(comma);
				changed = true;
			}
			owed.push(char === "{" ? "}" : "]");
		} else if (char === "}" || char === "]") {
			if (owed.at(-1) !== char) {
				changed = true;
				continue;
			}
			if (
				char === "}" &&
				owed.length === 2 &&
				owed[0] === "]" &&
				followingKey(text, index + 1) !== null
			) {
				changed = true;
				continue;
			}
			owed.pop();
		}
		out += char;
	}
	if (inString || (!changed && owed.length === 0)) {
		return text;
	}
	return out.trimEnd() + owed.toReversed().join("");
}

/**
 * The key, when `, "key":` is what comes next — a member of an object, not the next item of a list;
 * null otherwise.
 */
function followingKey(text: string, from: number): string | null {
	let index = from;
	const skipSpace = () => {
		while (index < text.length && /\s/u.test(text[index] ?? "")) {
			index += 1;
		}
	};
	skipSpace();
	if (text[index] !== ",") {
		return null;
	}
	index += 1;
	skipSpace();
	if (text[index] !== '"') {
		return null;
	}
	const start = index + 1;
	index += 1;
	while (index < text.length && text[index] !== '"') {
		if (text[index] === "\\") {
			index += 1;
		}
		index += 1;
	}
	const key = text.slice(start, index);
	index += 1;
	skipSpace();
	return text[index] === ":" ? key : null;
}

/** The schema for such a list: the array, or the same array as a JSON string. */
function listSchema(items: unknown, what: string) {
	return {
		anyOf: [
			{ type: "array", minItems: 1, maxItems: 10, items },
			{ type: "string", minLength: 2, description: `The ${what} as a JSON-encoded array` },
		],
	};
}

interface PracticeIndexEntry {
	slug: string;
	group?: string;
	readsSources: string[];
	exhaustiveSources: string[];
	/** What the practice revision is about, shown to the review on the work as explanatory context. */
	name: string;
	whyItMatters?: string;
	knownLimitations: string[];
	revisionId?: number;
}

/** The task this runner was started for. */
interface TaskEnvelope {
	schemaVersion: number;
	jobId: unknown;
	workspaceId: unknown;
	paths: ReturnType<typeof taskPaths>;
	prompt: string;
	repositoryFullName: unknown;
	pullRequestNumber: unknown;
}

/** How much feedback this run may compose, per lane. */
interface ChannelBounds {
	enabled: boolean;
	maxUnits: number;
}

/** Where an IN_CONTEXT note may be placed on this artifact. */
type PlacementKind = "DIFF" | "ARTIFACT";

/** The task-declared composition request, once its bounds have been clamped to what this run may do. */
interface CompositionRequest {
	channels: Record<Channel, ChannelBounds>;
	inContextPlacementKinds: PlacementKind[];
	minDistinctArtifacts: number;
}

/** One citation as admission projected it: its index, its coordinates and whether a note may sit on it. */
interface AdmittedCitation {
	index: number;
	[key: string]: unknown;
}

/** One admitted observation: a problem of a NOT_MET result, under its own id, with its own citations. */
interface AdmittedChild {
	observationId: string;
	citations: AdmittedCitation[];
	[key: string]: unknown;
}

/**
 * One admitted result as admission projected it: one practice's verdict and its observations. Composition rests on a
 * result by its practiceResultId and on one observation by its observationId. A result's own citations never anchor a
 * note; an observation's may. Validate consumed fields; preserve other server fields for the composer.
 */
interface AdmittedResult {
	practiceResultId: string;
	practiceSlug: string;
	outcome: Outcome;
	citations: AdmittedCitation[];
	observations: AdmittedChild[];
	[key: string]: unknown;
}

function isAdmittedCitation(value: unknown): value is AdmittedCitation {
	return isRecord(value) && typeof value.index === "number";
}

function isAdmittedChild(value: unknown): value is AdmittedChild {
	return (
		isRecord(value) &&
		typeof value.observationId === "string" &&
		value.observationId !== "" &&
		Array.isArray(value.citations) &&
		value.citations.every(isAdmittedCitation)
	);
}

function isAdmittedResult(value: unknown): value is AdmittedResult {
	return (
		isRecord(value) &&
		typeof value.practiceResultId === "string" &&
		value.practiceResultId !== "" &&
		typeof value.practiceSlug === "string" &&
		OUTCOMES.some((outcome) => outcome === value.outcome) &&
		Array.isArray(value.citations) &&
		value.citations.every(isAdmittedCitation) &&
		Array.isArray(value.observations) &&
		value.observations.every(isAdmittedChild) &&
		// Observations are the problems of a NOT_MET result, and only of one.
		(value.outcome === "NOT_MET") === value.observations.length > 0
	);
}

const WORKSPACE_ROOT = "/workspace";
const EVIDENCE_TOOLS = ["read", "grep", "find", "ls"] as const;
// Codemode runs a script that calls the other tools, so a chain of reads and searches, or a large
// result filtered down to what a criterion needs, costs one model call rather than one per step.
const PRACTICE_TOOLS = [...EVIDENCE_TOOLS, "bash", "codemode"] as const;
const CWD = process.env.PI_RUNNER_CWD ?? WORKSPACE_ROOT;
const ENVELOPE_MISMATCH_EXIT = 42;
const TASK_PATH = `${CWD}/task.json`;
const taskEnvelope = readTaskEnvelope();
const INPUT_PATHS = resolveTaskPaths(CWD, taskEnvelope.paths);
/**
 * Staged beside the composition request only when this attempt continues a review an earlier attempt admitted and lost
 * the writer of: what the server admitted then. Such an attempt assesses nothing; it composes the review on the work.
 */
const CONTINUATION_PATH = nodePath.join(
	nodePath.dirname(INPUT_PATHS.compositionRequest),
	"composition-continuation.json",
);
const OUTPUT = `${CWD}/out`;
const RESULT_PATH = outputPath(OUTPUT, "result.json");
const REVIEW_STATE_PATH = outputPath(OUTPUT, "review-state.json");
const WATCHDOG_PATH = outputPath(OUTPUT, "watchdog-killed.json");
const USAGE_PATH = outputPath(OUTPUT, "usage.json");
const RUNNER_DEBUG_PATH = outputPath(OUTPUT, "runner-debug.json");
const PRACTICE_COVERAGE_PATH = outputPath(OUTPUT, "practice-coverage.json");
/** SandboxLayout.PRACTICE_SCRIPT_REPORT_FILE: how the program of each admitted practice ran. */
const PRACTICE_SCRIPT_REPORT_PATH = outputPath(OUTPUT, "practice-scripts.json");
/** SandboxLayout.PRACTICE_SCRIPT_MODELS_FILE: the direct models the server bound; absent when none may be called. */
const PRACTICE_SCRIPT_MODELS_PATH = `${CWD}/practice-script-models.json`;
/** Each review's whole input, for the session to read beyond its bounded section; never composition's. */
const REVIEW_INPUT_ROOT = "work/review-input";
/** The runner's own record of what this review has recorded so far; read back after a compaction. */
const NOTES_PATH = `${CWD}/work/notes/review.md`;
/** The server sets every one of these; a missing one stops the runner before it does any work. */
function requiredEnv(name: string): string {
	const value = process.env[name];
	if (!hasText(value)) {
		throw new Error(`${name} env var is required`);
	}
	return value;
}
function positiveIntegerEnv(name: string): number {
	const text = requiredEnv(name);
	const value = Number(text);
	if (!Number.isInteger(value) || value <= 0) {
		throw new Error(`${name} env var must be a positive integer, got: ${text}`);
	}
	return value;
}
const AGENT_BUDGET_MS = positiveIntegerEnv("AGENT_BUDGET_MS");
const AGENT_DIR = requiredEnv("PI_CODING_AGENT_DIR");
const LLM_PROXY_URL = requiredEnv("LLM_PROXY_URL");
const LLM_PROXY_TOKEN = requiredEnv("LLM_PROXY_TOKEN");
/** The tokens all direct model calls of this attempt may spend together: the proxy's direct-call cap. */
const DIRECT_MODEL_TOKENS = positiveIntegerEnv("PI_DIRECT_MODEL_TOKENS");
/** Practices a turn carries at once; a catalog group larger than this is split. */
const PRACTICES_PER_TURN = hasText(process.env.PI_PRACTICE_BATCH_SIZE)
	? Number(process.env.PI_PRACTICE_BATCH_SIZE)
	: 6;
/** Per-practice refusal cap; an exhausted practice remains NOT_REACHED. */
const MAX_REFUSALS_PER_PRACTICE = 8;
/** What one practice may spend in a turn; a turn is owed {@link turnBudget} of it. Set by the server. */
const PER_PRACTICE_WORK: Work = {
	modelCalls: positiveIntegerEnv("PI_PRACTICE_MODEL_CALLS"),
	outputTokens: positiveIntegerEnv("PI_PRACTICE_OUTPUT_TOKENS"),
};
/**
 * How long a turn may go without a single session event — no streamed token, no tool progress — before
 * it counts as stalled and is aborted. Liveness, not a budget: the stream-idle bound Claude Code and
 * Codex both default to.
 */
const STALL_MS = 300_000;
/**
 * The most output tokens one model call may write, from the model's registration; a call that reaches
 * it was cut off, and the tool it called received it incomplete.
 */
let outputLimit = Number.POSITIVE_INFINITY;
/** A turn whose calls run into the output limit this often is stuck writing one runaway call. */
const CUT_OFF_ABORT = 2;

/** How often the stall watchdog looks: a clock read, so often enough that a stall is caught promptly. */
const STALL_CHECK_MS = 1000;
const WINDOWS = deriveWindows(AGENT_BUDGET_MS, existsSync(INPUT_PATHS.compositionRequest));

// The instant the watchdog below counts from. Every stage deadline starts later than this, so it is
// the only reading against which the process budget means anything.
const PROCESS_START_MS = Date.now();

setTimeout(() => {
	console.error(`[pi-runner] Watchdog: ${AGENT_BUDGET_MS + 30_000}ms elapsed, hard-exiting`);
	// First, because finalizing removes from out/ whatever the session left there — which would
	// include the marker written next.
	finalizeOutputQuietly();
	try {
		writeFileSync(
			WATCHDOG_PATH,
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

/** Snapshot the eligible practices once for the whole review. */
function readPracticeIndex(): PracticeIndexEntry[] {
	const index = parseJson(readFileSync(INPUT_PATHS.practiceIndex, "utf8"));
	if (!Array.isArray(index)) {
		throw new Error("the task-declared practice index: expected an array");
	}
	return jsonArray(index).map((practice): PracticeIndexEntry => {
		if (!isRecord(practice) || typeof practice.slug !== "string") {
			throw new Error("the task-declared practice index: every practice needs a string slug");
		}
		return {
			slug: practice.slug,
			group:
				typeof practice.group === "string" && practice.group !== "" ? practice.group : undefined,
			readsSources: jsonArray(practice.readsSources).filter(
				(kind): kind is string => typeof kind === "string",
			),
			exhaustiveSources: jsonArray(practice.exhaustiveSources).filter(
				(kind): kind is string => typeof kind === "string",
			),
			name: typeof practice.name === "string" ? practice.name : practice.slug,
			whyItMatters:
				typeof practice.whyItMatters === "string" && practice.whyItMatters.trim() !== ""
					? practice.whyItMatters
					: undefined,
			knownLimitations: jsonArray(practice.knownLimitations).filter(
				(limitation): limitation is string => typeof limitation === "string",
			),
			revisionId:
				typeof practice.revisionId === "number" &&
				Number.isSafeInteger(practice.revisionId) &&
				practice.revisionId > 0
					? practice.revisionId
					: undefined,
		};
	});
}

const folderIndex = parseJson(readFileSync(INPUT_PATHS.manifest, "utf8"));
const { availableSourceKinds, artifactSources } = folderCitationIndex(folderIndex);
const availableSourceKindValues = [...availableSourceKinds].toSorted();
const practiceIndex = readPracticeIndex();
const admittedPractices = new Set(practiceIndex.map((practice) => practice.slug));
// ABSENT is sound only over sources the practice declares exhaustive.
const practiceExhaustiveSources = new Map(
	practiceIndex.map((practice) => [practice.slug, new Set(practice.exhaustiveSources)]),
);
/** What this run spent, in the buckets usage.json reports. */
interface UsageTotals {
	model: string | null;
	inputTokens: number;
	outputTokens: number;
	reasoningTokens: number;
	cacheReadTokens: number;
	cacheWriteTokens: number;
	costUsd: number;
	totalCalls: number;
}

/** One attempt's worth of what happened, for runner-debug.json. */
interface AttemptDebug {
	label: string;
	durationMs: number;
	assistantMessages: number;
	stopReasons: Record<string, number>;
	usage: UsageReport;
	resultFilePresent: boolean;
}

const usageTotals: UsageTotals = {
	model: null,
	inputTokens: 0,
	outputTokens: 0,
	reasoningTokens: 0,
	cacheReadTokens: 0,
	cacheWriteTokens: 0,
	costUsd: 0,
	totalCalls: 0,
};
interface TurnTrace {
	modelError: boolean;
	/** Failed or aborted model calls of this turn, by the adapter's own closed kind; no provider text is kept. */
	modelFailures: Partial<Record<ModelFailureKind, number>>;
	/** The most recent available adapter failure timestamp; an unknown later failure keeps its own time null. */
	lastModelFailureAt: number | null;
	/**
	 * The failure the turn's last model call ended on, marked when the SDK then ended the retry unsuccessfully; null once a later
	 * call succeeds.
	 */
	finalModelFailure: (ModelFailure & { retryEndedUnsuccessfully: boolean }) | null;
	/** Failed or aborted calls whose failure diagnostic was missing or unreadable; their kind reads UNKNOWN. */
	unattributedModelFailures: number;
	/** The adapter's own call spans, failed calls included; calls without one are not counted. */
	modelCallTiming: ModelCallTiming;
	runtimeError: boolean;
	label: string;
	durationMs: number;
	calls: number;
	toolCalls: Record<string, number>;
	/** Calls of the turn's recording tool, counted whether or not the SDK let them reach it. */
	recordingCalls: number;
	stored: number;
	refused: number;
	refusalReasons: string[];
	/** Calls that ended in an error: a bash command that failed, a schema the SDK refused, a truncated call. */
	toolErrors: number;
	toolErrorReasons: string[];
	/** The most times one call — the same tool with the same arguments — was repeated in this turn. */
	repeatedCalls: number;
	compactions: number;
	providerRetries: number;
	outputTokens: number;
	/** Calls that ran into the model's output limit, and so reached their tool incomplete. */
	cutOff: number;
	/** What the turn may spend; it ends after the model call that spends it. */
	budget: Work;
	/** Whether the turn was told to record what it owes before its budget ran out. */
	askedToRecord: boolean;
	/** Why the runner ended the turn; null when the model ended it. */
	stoppedBy: StopReason | null;
}
/**
 * Why the runner ended a turn: its work budget was spent, the session stalled, the run neared its safety
 * ceiling, or a loop guard fired.
 */
type StopReason = "budget" | "stall" | "safety" | "loop";
/** Why a review that a stopped turn cut short gives no result. */
const UNRATED_BY_STOP: Readonly<Record<StopReason, UnratedReason>> = {
	budget: "budget",
	safety: "deadline",
	stall: "unavailable",
	loop: "off-format",
};
interface PublicHistoryReceipt {
	readAt: string;
	workCapturedAt: string | null;
	entries: {
		id: unknown;
		deliveredAt: unknown;
		eligibleForAlreadySaid: boolean;
		eligibleForPriorAdvice: boolean;
	}[];
	omissions: { oversizedEntries: number; budgetEntries: number };
}
const runnerDebug: {
	attempts: AttemptDebug[];
	turns: TurnTrace[];
	usageTotals: UsageTotals;
	publicHistory?: PublicHistoryReceipt;
} = {
	attempts: [],
	turns: [],
	usageTotals,
};
/** The turn the session events are charged to; null between turns. */
let currentTurn: TurnTrace | null = null;

/** How much of a refusal reason the trace keeps: the kind of failure, not the whole excerpt. */
const TRACE_REASON_CHARS = 140;
/** The final result of each practice whose program returned one, by slug: what admission is asked to admit. */
const reviewState = new Map<string, Candidate>();
/** How the program of each admitted practice ran, by slug. A practice missing here was not reached. */
const executions = new Map<string, ProgramOutcome>();
/** Why a practice that no program reached was not reached, when the run ends before it. */
let unreachedReason: "deadline" | "budget" | "error" = "deadline";

/** Slugs in the order the server sorts them: by UTF-16 code unit, as Java compares strings. */
function compareSlugs(a: string, b: string): number {
	if (a === b) {
		return 0;
	}
	return a < b ? -1 : 1;
}

/** The admission request: one entry for each practice with a final result, sorted by slug. */
function admissionRequest() {
	return {
		schemaVersion: 2,
		results: [...reviewState]
			.toSorted(([a], [b]) => compareSlugs(a, b))
			.map(([practiceSlug, candidate]) => ({ practiceSlug, candidate })),
	};
}

const searchSchema = {
	type: "object",
	additionalProperties: false,
	required: ["consulted", "lookedFor", "boundary"],
	properties: {
		consulted: {
			type: "array",
			minItems: 1,
			items: { type: "string", enum: availableSourceKindValues },
			description: "Evidence source kinds you actually searched, e.g. scm.review-threads.",
		},
		lookedFor: {
			type: "string",
			minLength: 1,
			description:
				"The specific behavior whose absence you report, matching the behavior assessed in the summary and explanation.",
		},
		boundary: {
			type: "string",
			minLength: 1,
			description:
				"What this search did NOT cover, so a reader can judge how far the absence reaches.",
		},
	},
} as const;
const inapplicabilitySchema = {
	type: "object",
	additionalProperties: false,
	required: ["consulted", "subject", "ruledOutBy"],
	properties: {
		consulted: {
			type: "array",
			minItems: 1,
			items: { type: "string", enum: availableSourceKindValues },
			description:
				"Evidence source kinds you read to reach this conclusion, e.g. scm.pull-request.diff.",
		},
		subject: {
			type: "string",
			minLength: 1,
			description: "The subject this practice's criteria judge.",
		},
		ruledOutBy: {
			type: "string",
			minLength: 1,
			description: "The fact about THIS work that rules out that subject.",
		},
	},
} as const;
const citationSchema = {
	type: "object",
	additionalProperties: false,
	required: ["sourceKind", "artifactPath", "path", "startLine"],
	properties: {
		sourceKind: { type: "string", enum: availableSourceKindValues },
		// Membership is checked when the result is submitted (validateEvidenceSources): a large capture
		// enumerated here produces a declaration the SDK's schema compiler cannot compile.
		artifactPath: {
			type: "string",
			minLength: 1,
			description:
				"The captured artifact the citation reads, exactly as the capture manifest names it.",
		},
		path: { type: "string", minLength: 1 },
		side: {
			type: ["string", "null"],
			enum: ["OLD", "NEW", null],
			description:
				"For a quote of the change: artifactPath names the pinned change (change.json), path is the file as named on that side, and the lines are the [L<n>] coordinates of work/change/diff.patch.",
		},
		revision: {
			type: ["string", "null"],
			pattern: "^(?:[0-9a-f]{40}|[0-9a-f]{64})$",
			description:
				"For repository text: artifactPath names the captured .git/HEAD, path is repository-relative, and revision optionally selects a full commit SHA; omission means the captured HEAD.",
		},
		startLine: {
			type: "integer",
			minimum: 1,
			maximum: 2_147_483_647,
			description:
				"The 1-based line of the quoted text in the artifact; for a quote of the change, the [L<n>] coordinate of work/change/diff.patch.",
		},
		endLine: {
			type: ["integer", "null"],
			minimum: 1,
			maximum: 2_147_483_647,
			description: "The last line of the quote, at least startLine; omitted means one line.",
		},
		quote: {
			type: ["string", "null"],
			description:
				"The text at those lines, copied as shown; a diff marker, a [L<n>] prefix or a non-breaking space read as a space are tolerated. Omit it to cite the whole of the lines: what was recorded is echoed back.",
		},
	},
} as const;
const openQuestionSchema = {
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
		citation: {
			anyOf: [citationSchema, { type: "null" }],
			description: "Where the question arises, when one place of the work does.",
		},
	},
} as const;
const problemSchema = {
	type: "object",
	additionalProperties: false,
	required: ["severity", "summary", "explanation", "citations", "basis"],
	properties: {
		key: {
			type: ["string", "null"],
			description:
				"Optional: a short lowercase-hyphenated name for this problem, unique within the result.",
		},
		severity: {
			type: "string",
			enum: SEVERITIES,
			description: `From the practice's Severity section. ${describeVocabulary(SEVERITIES, SEVERITY_DESCRIPTIONS)}`,
		},
		summary: {
			type: "string",
			minLength: 1,
			maxLength: MAX_SUMMARY_CHARS,
			description: `One line of at most ${MAX_SUMMARY_CHARS} characters naming this one problem, such as 'Debug print left in the request handler'.`,
		},
		explanation: {
			type: "string",
			minLength: 1,
			maxLength: CANDIDATE_LIMITS.problemExplanationChars,
			description:
				"What the cited evidence shows about this one problem and its consequence. Evidence, not advice, intent, confidence, or hidden chain-of-thought.",
		},
		citations: {
			type: "array",
			minItems: 1,
			items: citationSchema,
			description: "The lines that show this problem.",
		},
		basis: {
			type: "object",
			additionalProperties: false,
			required: ["type", "explanation"],
			description:
				"Why the problem belongs to this work. introduced: the change adds it (head: the index of a citation of the NEW side or the reviewed head). worsened: the change makes it worse (base: the index of the OLD side's citation, head: the NEW side's). duty: the criteria require an event that this work lacks (criterionQuote: the criteria's exact words, event: the index of the citation that shows the event).",
			properties: {
				type: { type: "string", enum: ["introduced", "worsened", "duty"] },
				head: { type: ["integer", "null"], minimum: 0 },
				base: { type: ["integer", "null"], minimum: 0 },
				event: { type: ["integer", "null"], minimum: 0 },
				criterionQuote: { type: ["string", "null"] },
				explanation: { type: "string", minLength: 1 },
			},
		},
		nextStep: {
			type: ["string", "null"],
			description:
				"Optional: the one change that would resolve this problem, when the criteria name it.",
		},
	},
} as const;
const resultSchema: ToolDefinition["parameters"] = {
	type: "object",
	additionalProperties: false,
	required: ["outcome", "summary", "explanation", "citations", "evidence"],
	properties: {
		outcome: {
			type: "string",
			enum: OUTCOMES,
			description: describeVocabulary(OUTCOMES, OUTCOME_DESCRIPTIONS),
		},
		summary: {
			type: "string",
			minLength: 1,
			maxLength: MAX_SUMMARY_CHARS,
			description:
				`A short phrase of at most ${MAX_SUMMARY_CHARS} characters identifying the specific behavior whose conformance to the practice standard you assess, such as 'Debug print left in the request handler'. ` +
				"Never a single word and never the practice's own name; the reasons, titles and quotes go in explanation. " +
				"A longer summary is refused, not shortened.",
		},
		explanation: {
			type: "string",
			minLength: 1,
			maxLength: CANDIDATE_LIMITS.explanationChars,
			description:
				"A concise explanation a developer can read: the source facts that decide whether this work holds " +
				"this criterion's subject and occasion, then, when it does, how the cited evidence meets or falls " +
				"short of what this criterion judges, leaving to other practices what it assigns to them. Describe " +
				"evidence, not advice, intent, confidence, or hidden chain-of-thought.",
		},
		citations: {
			type: "array",
			items: citationSchema,
			description:
				"The lines that decide the outcome. Required for MET, NOT_APPLICABLE and UNDETERMINED; a NOT_MET result may leave it empty, since each problem cites its own lines.",
		},
		problems: {
			anyOf: [{ type: "array", minItems: 1, items: problemSchema }, { type: "null" }],
			description:
				"NOT_MET only, and required there: each problem the evidence shows, each with its own severity, citations and basis. null for every other outcome.",
		},
		severity: {
			type: ["string", "null"],
			enum: [...SEVERITIES, null],
			description:
				"NOT_MET only, and optional: the result's severity when the pattern of problems is worse than the worst one alone. null takes the worst problem's severity.",
		},
		example: {
			anyOf: [
				{
					type: "object",
					additionalProperties: false,
					required: ["citation", "note"],
					properties: {
						citation: citationSchema,
						note: { type: "string", minLength: 1, maxLength: CANDIDATE_LIMITS.exampleNoteChars },
					},
				},
				{ type: "null" },
			],
			description:
				"Optional, MET or NOT_MET only: one place of this work that shows the practice as the criteria ask, when the evidence holds one; never on the lines of a problem. null otherwise.",
		},
		evidence: {
			type: "object",
			additionalProperties: false,
			description:
				"The warrant the outcome owes, and nothing else: search for a MET or NOT_MET or UNDETERMINED that rests on something " +
				"missing — what you looked for, where, and what the search did not cover; inapplicability for NOT_APPLICABLE; " +
				"undecidability, a list of open questions, for UNDETERMINED and, beside its problems, for a NOT_MET. Use null for " +
				"branches that do not apply.",
			properties: {
				search: { anyOf: [searchSchema, { type: "null" }] },
				inapplicability: { anyOf: [inapplicabilitySchema, { type: "null" }] },
				undecidability: {
					anyOf: [{ type: "array", minItems: 1, items: openQuestionSchema }, { type: "null" }],
				},
			},
		},
	},
} as const;

function persistUsage() {
	writeFileSync(USAGE_PATH, JSON.stringify(usageTotals, null, 2));
}
function persistRunnerDebug() {
	writeFileSync(RUNNER_DEBUG_PATH, JSON.stringify(runnerDebug, null, 2));
}
function persistReviewState() {
	writeFileSync(REVIEW_STATE_PATH, JSON.stringify(admissionRequest(), null, 2));
}

/**
 * This attempt continues an admitted review: it runs no program, and submits no result of its own. Known before
 * anything runs, so that a failure even before the first practice reports no program of its own.
 */
const continuing = existsSync(CONTINUATION_PATH);

/** The admission request as this run's result; a continuing writer's holds none, since it submitted none. */
function maybeWriteResultFile(): boolean {
	if (!continuing && reviewState.size === 0) {
		return false;
	}
	writeFileSync(RESULT_PATH, JSON.stringify(admissionRequest(), null, 2));
	return true;
}

/** The program each admitted practice runs: its staged custom program, or the built-in. */
function programOf(slug: string): PracticeProgram {
	const file = nodePath.join(INPUT_PATHS.practiceScripts, `${slug}.ts`);
	return existsSync(file)
		? { script: { path: file, source: readFileSync(file) } }
		: { script: null };
}

/** One entry for each admitted practice: how its program ran, or that none reached it. */
function persistExecutionReport(): void {
	const entries = practiceIndex.map(
		({ slug }) =>
			[slug, executions.get(slug) ?? notReachedOutcome(programOf(slug), unreachedReason)] as const,
	);
	writeFileSync(PRACTICE_SCRIPT_REPORT_PATH, executionReport(entries));
}

// out/ is the sandbox's own claim, so the runner writes every file in it last, from memory, and
// removes whatever else a session left there.
function finalizeOutput(): void {
	const written = new Set<string>();
	const persist = (path: string, write: () => unknown) => {
		if (write() !== false) {
			written.add(path);
		}
	};
	persist(REVIEW_STATE_PATH, persistReviewState);
	persist(RESULT_PATH, maybeWriteResultFile);
	persist(USAGE_PATH, persistUsage);
	persist(RUNNER_DEBUG_PATH, persistRunnerDebug);
	if (practiceCoverageLedger !== null) {
		persist(PRACTICE_COVERAGE_PATH, persistPracticeCoverage);
	}
	if (compositionAdmitted) {
		persist(FEEDBACK_PATH, persistComposedFeedback);
	}
	// A continuing writer runs no program: its earlier attempt reported them.
	if (!continuing) {
		persist(PRACTICE_SCRIPT_REPORT_PATH, persistExecutionReport);
	}
	for (const entry of readdirSync(OUTPUT)) {
		const path = `${OUTPUT}/${entry}`;
		if (!written.has(path)) {
			rmSync(path, { recursive: true, force: true });
		}
	}
}

function hasPersistedReviewState(): boolean {
	return reviewState.size > 0;
}

/** A checked result, and what the check changed about its citations, for the session to see. */
interface Validated {
	candidate: Candidate;
	notes: string[];
}

/** Resolve only the path placeholders owned by the task envelope. */
function orchestratorWithPaths(text: string): string {
	const {
		contextRoot,
		repositoryRoot,
		manifest,
		practiceIndex: indexPath,
		preparedFeedback,
	} = taskEnvelope.paths;
	const paths: Record<string, string> = {
		contextRoot,
		repositoryRoot,
		manifest,
		practiceIndex: indexPath,
		practiceRoot: nodePath.dirname(indexPath),
		historyRoot: nodePath.dirname(preparedFeedback),
	};
	return text.replaceAll(
		/<(?<path>contextRoot|repositoryRoot|manifest|practiceIndex|practiceRoot|historyRoot)>/gu,
		(placeholder, key: string) => paths[key] ?? placeholder,
	);
}

/** What a MET with incomplete coverage is told: which reads fell short, and what it may submit instead. */
function incompleteCoverageText(coverage: Coverage): string {
	const omitted = coverage.complete
		? []
		: coverage.omitted.map(
				(omission) =>
					`${omission.count} ${omission.what}${omission.units === undefined ? "" : ` (${omission.units.join(", ")})`}`,
			);
	return (
		`a MET result needs complete coverage, and this session's reads did not complete: ${omitted.join("; ")}. ` +
		"Read the rest of what was cut short, with an offset or a narrower search, and submit again; or submit what the " +
		"evidence supports: NOT_MET for a problem it proves, or UNDETERMINED with the open question"
	);
}

/**
 * Check a submitted result for the session's one practice the way admission will: its shape, its sources, its
 * search and inapplicability scope, its quotes against the artifacts, and its rules with the coverage the host
 * measured. Each repair is noted.
 */
function validateSubmission(raw: unknown, slug: string, coverage: Coverage): Validated {
	const notes: string[] = [];
	const submission = normalizeSubmission(raw, notes, (artifact) => artifactSources.get(artifact));
	const citations = submissionCitations(submission);
	// The manifest says which source staged an artifact; a citation that names the artifact under
	// another source kind is read as the manifest reads it, and the correction is echoed back.
	for (const citation of citations) {
		// A quote of a staged record (description.md, comments.json) named as its path under the pinned
		// change is a quote of that record: the artifact is the path.
		const stagedAtPath = artifactSources.get(citation.path);
		if (stagedAtPath !== undefined && citation.artifactPath !== citation.path) {
			notes.push(
				`${citation.path} is an artifact of its own, staged by ${stagedAtPath}; recorded against it, not ${citation.artifactPath}`,
			);
			citation.artifactPath = citation.path;
			citation.sourceKind = stagedAtPath;
		}
		const staged = artifactSources.get(citation.artifactPath);
		if (staged !== undefined && staged !== citation.sourceKind) {
			notes.push(
				`${citation.artifactPath} is staged by ${staged}, not ${citation.sourceKind}; recorded as ${staged}`,
			);
			citation.sourceKind = staged;
		}
		// Once the source is settled: a side selects a line of the change and a revision a commit of the
		// checkout, and neither says anything of another source. Admission refuses either one elsewhere, and
		// with it the whole result, so neither leaves the runner on any other source.
		if (citation.sourceKind !== DIFF_SOURCE && citation.side !== undefined) {
			notes.push(`${citation.path}: side dropped — only a line of the change has a side`);
			delete citation.side;
		}
		if (citation.sourceKind !== TREE_SOURCE && citation.revision !== undefined) {
			notes.push(
				`${citation.path}: revision dropped — only a repository file is read at a revision`,
			);
			delete citation.revision;
		}
	}
	validateEvidenceSources(
		submission,
		availableSourceKinds,
		artifactSources,
		`${taskEnvelope.paths.repositoryRoot.replace(/\/+$/u, "")}/`,
	);
	validateSearchScope(
		submission,
		practiceExhaustiveSources.get(slug) ?? new Set(),
		availableSourceKinds,
	);
	validateInapplicabilityScope(submission, availableSourceKinds);
	if ((submission.problems ?? []).some((problem) => problem.basis.type === "state")) {
		throw new Error(
			"a problem's basis is introduced, worsened or duty in a review of a change: a state basis is for a review of the whole work",
		);
	}
	// A practice that reads the change grounds a decision of nothing in it, unless another practice's
	// result already consulted it; a practice judging comments or history alone owes the change nothing.
	if (
		(submission.outcome === "NOT_APPLICABLE" || submission.outcome === "UNDETERMINED") &&
		availableSourceKinds.has(DIFF_SOURCE) &&
		practiceIndex.find((practice) => practice.slug === slug)?.readsSources.includes(DIFF_SOURCE) ===
			true &&
		!readTheChange(submission) &&
		![...reviewState].some(
			([other, candidate]) => other !== slug && candidateReadTheChange(candidate),
		)
	) {
		throw new Error(
			"a result that decides nothing must show it read the change: cite a line of " +
				`${CHANGE_ROOT}/diff.patch (sourceKind ${DIFF_SOURCE}), or list ${DIFF_SOURCE} among the ` +
				"sources consulted in evidence.search or evidence.inapplicability",
		);
	}
	for (const citation of citations) {
		// Match admission: repository quotes use Git blobs; change quotes use the derived diff.
		const content = citedContent(citation);
		const resolved = resolveCited(citation, content);
		if ("mismatch" in resolved) {
			throw new Error(
				`citation does not match ${citation.path}:${citation.startLine}-${citation.endLine} ` +
					`(${citation.side ?? "text"}) in '${citation.artifactPath}': ${resolved.mismatch}. Copy the exact ` +
					`artifact text and, for the change, the [L<n>] coordinates and OLD/NEW side of ${CHANGE_ROOT}/diff.patch ` +
					"(the numbers sed -n or grep -n print are lines of diff.patch, not these coordinates)",
			);
		}
		const cited = `${citation.path}:${citation.startLine}-${citation.endLine}`;
		if (!citation.quote.trim()) {
			notes.push(`${cited} recorded ${excerptOf(resolved.quote)}`);
		}
		if (resolved.path !== undefined && resolved.side !== undefined) {
			citation.path = resolved.path;
			citation.side = resolved.side;
		}
		if (resolved.startLine !== undefined && resolved.endLine !== undefined) {
			notes.push(
				`${cited} does not hold that text; it is at ${citation.path}:${resolved.startLine}-${resolved.endLine}${resolved.side === undefined ? "" : ` (${resolved.side})`}, recorded there`,
			);
			citation.startLine = resolved.startLine;
			citation.endLine = resolved.endLine;
		}
		citation.quote = resolved.quote;
	}
	// A conversation is reviewed once for each participant; each lapse must be that participant's own.
	const thread = `${CWD}/${CONVERSATION_THREAD}`;
	if (submission.outcome === "NOT_MET" && existsSync(thread)) {
		const turns = readFileSync(thread, "utf8");
		if (
			(submission.problems ?? []).some((problem) => !citesReviewedTurn(problem.citations, turns))
		) {
			throw new Error(
				`each problem of a NOT_MET of this conversation review must cite a turn of the participant under review: quote a ` +
					`line inside a turn of ${CONVERSATION_THREAD} marked "underReview": true. The other turns are ` +
					"context, and a lapse that only they show is not this participant's",
			);
		}
	}
	const change = capturedChange();
	const parsed = parseCandidate(
		{ ...submission, coverage },
		(citation): CodeCitationIdentity | undefined => {
			if (citation.sourceKind === TREE_SOURCE) {
				const repository = citationRepository(citation);
				const revision = citation.revision ?? checkedOutCommit(repository);
				return revision === null ? undefined : { repository, revision };
			}
			if (citation.sourceKind === DIFF_SOURCE && change !== null) {
				return {
					repository: INPUT_PATHS.repositoryRoot,
					revision: citation.side === "OLD" ? change.base : change.head,
				};
			}
			return undefined;
		},
	);
	if (!parsed.ok) {
		throw new Error(
			parsed.reasonCode === "met_coverage_incomplete"
				? incompleteCoverageText(coverage)
				: `${parsed.reasonCode}: ${parsed.message}`,
		);
	}
	return { candidate: parsed.candidate, notes };
}

/** Infer an omitted diff side by exact quote match, preferring NEW; never override a supplied side. */
function resolveOnEitherSide(
	citation: NormalizedCitation,
	content: string,
): ReturnType<typeof resolveQuote> {
	if (citation.sourceKind !== "scm.pull-request.diff" || citation.side !== undefined) {
		return resolveQuote(citation, content);
	}
	const onNew = resolveQuote({ ...citation, side: "NEW" }, content);
	if ("quote" in onNew) {
		citation.side = "NEW";
		return onNew;
	}
	const onOld = resolveQuote({ ...citation, side: "OLD" }, content);
	if ("quote" in onOld) {
		citation.side = "OLD";
		return onOld;
	}
	return { mismatch: `${onNew.mismatch} (no side was named; NEW was tried, then OLD)` };
}

function excerptOf(text: string): string {
	return JSON.stringify(text.length > 160 ? `${text.slice(0, 160)}…` : text);
}

/** Whether a submission cites the change or names it among the sources its warrants consulted. */
function readTheChange(submission: NormalizedSubmission): boolean {
	if (submissionCitations(submission).some((citation) => citation.sourceKind === DIFF_SOURCE)) {
		return true;
	}
	const consulted = [
		...(submission.evidence.search?.consulted ?? []),
		...(submission.evidence.inapplicability?.consulted ?? []),
	];
	return consulted.includes(DIFF_SOURCE);
}

/** Whether a final result cites the change or names it among the sources its warrants consulted. */
function candidateReadTheChange(candidate: Candidate): boolean {
	const cited = [
		...candidate.citations,
		...(candidate.outcome === "NOT_MET"
			? candidate.problems.flatMap((problem) => problem.citations)
			: []),
	];
	const { evidence } = candidate;
	const consulted = [
		...("search" in evidence && evidence.search !== undefined ? evidence.search.consulted : []),
		...("inapplicability" in evidence ? evidence.inapplicability.consulted : []),
	];
	return (
		cited.some((citation) => citation.sourceKind === DIFF_SOURCE) || consulted.includes(DIFF_SOURCE)
	);
}

/** What a repository read returns for a file that is not text: git's own rule, a NUL in the opening bytes. */
const BINARY = Symbol("binary");

/** What a citation quotes: the checkout at HEAD or at the named revision, or the derived change view. */
function citedContent(citation: NormalizedCitation): string | typeof BINARY | null {
	if (citation.sourceKind === TREE_SOURCE) {
		return citation.revision === undefined
			? readCheckoutFile(citation.path, citationRepository(citation))
			: readRevisionFile(citation.path, citation.revision, citationRepository(citation));
	}
	if (citation.sourceKind === DIFF_SOURCE) {
		return readFileSync(`${CWD}/${CHANGE_ROOT}/diff.patch`, "utf8");
	}
	return readFileSync(`${CWD}/${citation.artifactPath}`, "utf8");
}

/** The citation resolved against what it quotes, or the mismatch that says why it cannot be. */
function resolveCited(
	citation: NormalizedCitation,
	content: string | typeof BINARY | null,
): ReturnType<typeof resolveOnEitherSide> {
	if (content === null) {
		return {
			mismatch:
				citation.revision === undefined
					? "no such file in the checkout"
					: `no such file at revision ${citation.revision} in the checkout's history`,
		};
	}
	if (content === BINARY) {
		return {
			mismatch:
				`that file is binary and has no lines to quote; cite the commit that adds it in ` +
				`${taskEnvelope.paths.contextRoot}/commits.json, or the text that embeds it`,
		};
	}
	return resolveOnEitherSide(citation, content);
}

/** The bytes as text, or BINARY: a quote of a binary file cannot be verified, here or at admission. */
function asText(bytes: Buffer): string | typeof BINARY {
	return bytes.subarray(0, 8000).includes(0) ? BINARY : bytes.toString("utf8");
}

/** The artifact index, not the task's primary checkout, determines which repository a citation reads. */
function citationRepository(citation: NormalizedCitation): string {
	const match = /^repos\/(?<repository>[A-Za-z0-9_-]+)\/\.git\/HEAD$/u.exec(citation.artifactPath);
	if (!match?.groups || typeof match.groups.repository !== "string") {
		throw new Error("Repository citation requires a captured repository HEAD");
	}
	return nodePath.resolve(CWD, "repos", match.groups.repository);
}

/** The blob at a repository-relative path in a revision of the checkout's history, or null. */
function readRevisionFile(
	path: string,
	revision: string,
	repository: string,
): string | typeof BINARY | null {
	if (path.startsWith("/") || path.split("/").includes("..")) {
		return null;
	}
	const child = spawnSync("git", ["-C", repository, "--no-pager", "show", `${revision}:${path}`], {
		maxBuffer: 64 * 1024 * 1024,
		env: { ...process.env, GIT_TERMINAL_PROMPT: "0", GIT_OPTIONAL_LOCKS: "0" },
	});
	return child.status === 0 ? asText(child.stdout) : null;
}

/** The file at a repository-relative path in the checkout, or null when there is none. */
function readCheckoutFile(path: string, repository: string): string | typeof BINARY | null {
	const file = nodePath.resolve(repository, path);
	if (!file.startsWith(`${repository}/`)) {
		return null;
	}
	try {
		return asText(readFileSync(file));
	} catch {
		return null;
	}
}

/** A draft disposition; its practice slug is its stable reference within this review. */
/** What one submission did to the review call's draft result. */
type Recorded =
	| (({ kind: "stored" } | { kind: "revised" }) & {
			slug: string;
			negative: boolean;
			filled: string[];
	  })
	| { kind: "duplicate"; slug: string }
	| { kind: "refused"; slug: string; reason: string };

/** Set once the final results have gone to admission; no result is accepted after it. */
let measurementClosed = false;

/** Calls of the recording tools one turn may make before recording anything; past it, the turn ends. */
const MAX_RECORDING_ATTEMPTS_PER_TURN = 24;

/** Per-turn thresholds for nudging and aborting identical tool calls. */
const REPEATED_CALL_NUDGE = 3;
const REPEATED_CALL_ABORT = 6;
/**
 * The same thresholds for a recording call: its answer is a function of its arguments, so the second
 * identical call already shows a loop, and a nudge has never broken one.
 */
const REPEATED_RECORDING_NUDGE = 2;
const REPEATED_RECORDING_ABORT = 3;

/** Avoid logging SDK error events again when the tool already logged its refusal. */
const answeredRefusals = new Set<string>();

/** A refusal the tool itself is answering: logged where it was decided, and once. */
async function refusal<T>(toolCallId: string, text: string): Promise<AgentToolResult<T>> {
	answeredRefusals.add(toolCallId);
	throw new Error(text);
}

/** The tools a turn exists to call: what it records with them is what it owes. */
const RECORDING_TOOLS: ReadonlySet<string> = new Set([
	"submit_result",
	"report_feedback",
	"report_review",
]);

/** The tool the composition turn under way records with: the review on the work, or the private lanes. */
let composerTool: "report_review" | "report_feedback" = "report_feedback";

/** The text of a tool result as the model reads it; empty when the result carries none. */
function toolResultText(result: unknown): string {
	return jsonArray(isRecord(result) ? result.content : null)
		.map((block) => (isRecord(block) && typeof block.text === "string" ? block.text : ""))
		.filter(Boolean)
		.join("\n");
}

/** How much of a tool error the log and the trace keep: the kind of failure, not the whole excerpt. */
const TOOL_ERROR_CHARS = 240;

/** A reason on one line, bounded: the SDK puts what it refused on the line after its heading. */
function firstLine(text: string): string {
	const line = text.replaceAll(/\s+/gu, " ").trim() || "(no reason given)";
	return line.length > TOOL_ERROR_CHARS ? `${line.slice(0, TOOL_ERROR_CHARS)}…` : line;
}

/** The session the turn events belong to, once it exists; what the loop bound aborts. */
let activeSession: AgentSession | null = null;

/**
 * What the turn in flight still owes and how it is told to record it; set and cleared with its trace.
 * Measuring owes one result per practice, composition one decision per NOT_MET practice.
 * `endsWhenPaid` marks a turn that exists only to pay what it owes, so it ends once nothing is owed.
 */
interface TurnDemand {
	owed: () => number;
	nudge: string;
	endsWhenPaid?: true;
}
let turnDemand: TurnDemand | null = null;

/**
 * Output tokens the model spends per result it submits, averaged over its submitting messages: what a
 * turn must keep back to submit what it owes. The prior is a slow open model's pace until the session
 * has submitted once.
 */
let tokensPerObservation = 1500;
/** Output tokens of the last assistant message, when that message carries a recording call. */
let recordingMessageTokens = 0;
/** When the session last showed any sign of life; what the stall watchdog reads. */
let lastEventAt = Date.now();

/** How many results a submit_result call carried, stored, duplicate or refused alike. */
function observationsIn(result: unknown): number {
	const details = isRecord(result) ? result.details : undefined;
	if (!isRecord(details)) {
		return 0;
	}
	return [details.inserted, details.duplicates, details.refused]
		.map((count) => (typeof count === "number" ? count : 0))
		.reduce((sum, count) => sum + count, 0);
}

/**
 * Charges each model call to the turn in flight. The turn is told once to record what it owes when its
 * remaining work only pays for that, and is ended between model calls — after the tools of the call that
 * spent the budget have run, so a recording in that call lands — never within one.
 */
function chargeWork(event: AgentSessionEvent): void {
	lastEventAt = Date.now();
	const turn = currentTurn;
	if (event.type === "message_end" && event.message.role === "assistant") {
		const output = outputTokensOf(event.message);
		recordingMessageTokens = listOrEmpty(event.message.content).some(
			(c) => c.type === "toolCall" && c.name === "submit_result",
		)
			? output
			: 0;
		if (!turn || !turnDemand) {
			return;
		}
		turn.calls += 1;
		turn.outputTokens += output;
		noteCutOff(turn, event.message.stopReason === "length" || output >= outputLimit);
		const used: Work = { modelCalls: turn.calls, outputTokens: turn.outputTokens };
		if (
			!turn.askedToRecord &&
			turn.stoppedBy === null &&
			shouldRecordNow(used, turn.budget, turnDemand.owed(), tokensPerObservation)
		) {
			turn.askedToRecord = true;
			console.error(
				`[pi-runner] ${turn.label}: ${turn.calls} of ${turn.budget.modelCalls} calls and ` +
					`${turn.outputTokens} of ${turn.budget.outputTokens} output tokens spent — nudging to record`,
			);
			if (activeSession) {
				void steer(activeSession, turnDemand.nudge);
			}
		}
		return;
	}
	if (event.type === "tool_execution_end" && event.toolName === "submit_result") {
		const written = observationsIn(event.result);
		if (written > 0 && recordingMessageTokens > 0) {
			tokensPerObservation = Math.round(
				(tokensPerObservation + recordingMessageTokens / written) / 2,
			);
		}
		recordingMessageTokens = 0;
		return;
	}
	if (
		event.type === "turn_end" &&
		turn?.stoppedBy === null &&
		spent({ modelCalls: turn.calls, outputTokens: turn.outputTokens }, turn.budget)
	) {
		stopTurn(
			"budget",
			`work budget spent (${turn.calls} calls, ${turn.outputTokens} output tokens) — ending this turn`,
		);
	}
}

/**
 * A call cut off at the output limit reaches its tool incomplete, and the tool's error reads like the
 * model's mistake: the model is told it was the cut, once; a second cut in the turn ends it.
 */
function noteCutOff(turn: TurnTrace, cutOff: boolean): void {
	if (!cutOff) {
		return;
	}
	turn.cutOff += 1;
	sessionGuards.cutOff += 1;
	if (sessionGuards.cutOff >= CUT_OFF_ABORT) {
		stopTurn(
			"loop",
			`${sessionGuards.cutOff} calls in this session ran into the ${outputLimit}-token output limit — aborting this turn`,
		);
		return;
	}
	console.error(`[pi-runner] ${turn.label}: a call ran into the output limit — telling the model`);
	if (activeSession) {
		void steer(
			activeSession,
			`Your last call was cut off at the ${outputLimit}-token output limit, so its tool received it ` +
				"incomplete and failed: the error it reports is the cut, not your syntax. Send a short call " +
				"instead — a search with a pattern rather than a list of every value. Keep each recording " +
				"call complete.",
		);
	}
}

/** End the turn in flight for a reason of the runner's own; the first reason is the one the trace keeps. */
function stopTurn(reason: StopReason, why: string): void {
	const turn = currentTurn;
	console.error(`[pi-runner] ${turn?.label ?? "review"}: ${why}`);
	if (turn && turn.stoppedBy === null) {
		turn.stoppedBy = reason;
	}
	if (activeSession) {
		void abortSession(activeSession);
	}
}

/** Refused submissions per practice; a practice past MAX_REFUSALS_PER_PRACTICE accepts no more. */
const refusals = new Map<string, number>();
/**
 * Practices this review asks no more about: past the refusal limit, too large for the context, or ended on their
 * own again after the one request to record. Without a draft, each stays not reached.
 */
const blockedPractices = new Map<string, UnratedReason>();

/** Whether the practice was closed by its refused submissions, not by another reason. */
function refusalLimited(slug: string): boolean {
	return (refusals.get(slug) ?? 0) >= MAX_REFUSALS_PER_PRACTICE;
}

function countRefusal(slug: string): void {
	const count = (refusals.get(slug) ?? 0) + 1;
	refusals.set(slug, count);
	if (count >= MAX_REFUSALS_PER_PRACTICE) {
		blockedPractices.set(slug, "off-format");
	}
}

/**
 * The items refused in this turn, as sent, with why: an identical resend gets the same answer, so it is
 * told so at once rather than checked again; cleared with the turn.
 */
const refusedItems = new Map<string, { reason: string; resent: number }>();

/** An identical refused item sent this many times more ends the turn: the circuit breaker. */
const IDENTICAL_REFUSAL_ABORT = 2;

/** The session tools whose answers are evidence reads the host keeps receipts of. */
const READ_TOOLS: ReadonlySet<string> = new Set(["read", "grep", "find", "ls"]);
/** The most units an omission names; the count says how many there are. */
const OMISSION_UNITS = CANDIDATE_LIMITS.omissionUnits;
/** A tool error that says the source could not be read, not that the session asked for something absent. */
const READ_FAILURE =
	/EACCES|EPERM|EIO|ETIMEDOUT|permission denied|operation not permitted|timed out/iu;

/**
 * What one review session's evidence reads reached, from the host's own receipts of its tools: every read,
 * the reads its tool cut short and the session did not read on from, and the reads that failed.
 */
interface SessionReads {
	reads: number;
	/** Files whose last read was cut at the tool's limit, and not read on to their end since. */
	cutFiles: Set<string>;
	/** Searches and listings cut at their limit. */
	cutSearches: string[];
	failed: string[];
	/** The arguments of each read in flight, by call. */
	pending: Map<string, unknown>;
}

function newSessionReads(): SessionReads {
	return { reads: 0, cutFiles: new Set(), cutSearches: [], failed: [], pending: new Map() };
}

/** Keep the receipt of one read the session made, directly or from a codemode script. */
function noteSessionRead(reads: SessionReads, event: AgentSessionEvent): void {
	if (event.type === "tool_execution_start" && READ_TOOLS.has(event.toolName)) {
		reads.pending.set(event.toolCallId, event.args);
		return;
	}
	if (event.type !== "tool_execution_end" || !reads.pending.has(event.toolCallId)) {
		return;
	}
	const args = reads.pending.get(event.toolCallId);
	reads.pending.delete(event.toolCallId);
	reads.reads += 1;
	const target = isRecord(args)
		? [args.path, args.pattern].find((value): value is string => typeof value === "string")
		: undefined;
	const named = target ?? event.toolName;
	const result: unknown = event.result;
	if (event.isError) {
		if (READ_FAILURE.test(toolResultText(result))) {
			reads.failed.push(named);
		}
		return;
	}
	const details = isRecord(result) && isRecord(result.details) ? result.details : {};
	const cut =
		(isRecord(details.truncation) && details.truncation.truncated === true) ||
		details.matchLimitReached !== undefined ||
		details.resultLimitReached !== undefined ||
		details.entryLimitReached !== undefined;
	if (event.toolName === "read" && target !== undefined) {
		// A read that reaches the end of its file without a cut, or a read with no offset, is the file's last word.
		const continued = isRecord(args) && args.offset !== undefined;
		if (cut) {
			reads.cutFiles.add(target);
		} else if (continued && !/more lines in file/u.test(toolResultText(result))) {
			reads.cutFiles.delete(target);
		}
		return;
	}
	if (cut) {
		reads.cutSearches.push(named);
	}
}

/** The host's receipts of a session's reads, as a report counts them. */
function sessionReceipts(reads: SessionReads): Receipts {
	return {
		reads: reads.reads,
		truncatedReads: reads.cutFiles.size + reads.cutSearches.length,
		failedReads: reads.failed.length,
	};
}

/** What a session's reads covered: complete only when no read was cut short or failed. */
function sessionCoverage(reads: SessionReads): Coverage {
	const receipts = sessionReceipts(reads);
	const cut = [...reads.cutFiles, ...reads.cutSearches];
	const omitted: Omission[] = [
		...(cut.length > 0
			? [
					{
						what: "reads cut short at the tool's output limit",
						count: cut.length,
						reason: "truncated" as const,
						units: cut
							.slice(0, OMISSION_UNITS)
							.map((unit) => unit.slice(0, CANDIDATE_LIMITS.warrantFieldChars)),
					},
				]
			: []),
		...(reads.failed.length > 0
			? [
					{
						what: "reads that failed",
						count: reads.failed.length,
						reason: "unavailable" as const,
						units: reads.failed
							.slice(0, OMISSION_UNITS)
							.map((unit) => unit.slice(0, CANDIDATE_LIMITS.warrantFieldChars)),
					},
				]
			: []),
	];
	const [first, ...rest] = omitted;
	return first === undefined
		? { source: "host", ...receipts, complete: true }
		: { source: "host", ...receipts, complete: false, omitted: [first, ...rest] };
}

/**
 * The one review in flight: the practice it reviews, its private draft result, and what its session's
 * reads reached. A submission goes to this draft only; it becomes the practice's result only when the
 * program returns it.
 */
interface ActiveReview {
	slug: string;
	draft: Candidate | null;
	reads: SessionReads;
}
let activeReview: ActiveReview | null = null;

function record(raw: unknown): Recorded {
	const review = activeReview;
	if (review === null) {
		return {
			kind: "refused",
			slug: "unknown",
			reason: "no review of a practice is open in this session; nothing is accepted",
		};
	}
	const { slug } = review;
	if (blockedPractices.has(slug)) {
		return {
			kind: "refused",
			slug,
			reason: refusalLimited(slug)
				? `${MAX_REFUSALS_PER_PRACTICE} submissions for '${slug}' were refused; no more are accepted for it.`
				: `this review asks no more about '${slug}'; no more submissions are accepted for it.`,
		};
	}
	const sent = JSON.stringify(raw);
	const earlier = refusedItems.get(sent);
	if (earlier !== undefined) {
		earlier.resent += 1;
		countRefusal(slug);
		if (earlier.resent >= IDENTICAL_REFUSAL_ABORT) {
			stopTurn(
				"loop",
				`the same refused ${slug} result sent ${earlier.resent + 1} times — aborting this turn`,
			);
		}
		return {
			kind: "refused",
			slug,
			reason:
				`identical to a result this turn already refused, so the answer is the same: ${earlier.reason}. ` +
				"Change what that names",
		};
	}
	let validated: Validated;
	try {
		validated = validateSubmission(raw, slug, sessionCoverage(review.reads));
	} catch (error) {
		countRefusal(slug);
		const reason = errorText(error);
		refusedItems.set(sent, { reason, resent: 0 });
		return { kind: "refused", slug, reason };
	}
	const { candidate, notes } = validated;
	const previous = review.draft;
	if (previous !== null && isDeepStrictEqual(previous, candidate)) {
		return { kind: "duplicate", slug };
	}
	review.draft = candidate;
	return {
		kind: previous === null ? "stored" : "revised",
		slug,
		negative: candidate.outcome === "NOT_MET",
		filled: notes,
	};
}

/** The results submitted so far, for the review's notes; read back after a compaction. */
function persistRecordedNotes(): void {
	try {
		mkdirSync(nodePath.dirname(NOTES_PATH), { recursive: true });
		writeFileSync(NOTES_PATH, `# Current results\n\n${recordedSoFar()}\n`);
	} catch (error) {
		console.error(`[pi-runner] notes could not be written: ${errorText(error)}`);
	}
}

/** One line for each result: the final ones and the open review's draft, or of the given practices only. */
function recordedSoFar(only?: readonly string[]): string {
	const results = new Map(reviewState);
	if (activeReview?.draft != null) {
		results.set(activeReview.slug, activeReview.draft);
	}
	const shown = [...results].filter(([slug]) => only === undefined || only.includes(slug));
	if (shown.length === 0) {
		return "Nothing recorded yet.";
	}
	return shown
		.map(([slug, candidate]) => {
			const cited = [
				...new Set(
					[
						...candidate.citations,
						...(candidate.outcome === "NOT_MET"
							? candidate.problems.flatMap((problem) => problem.citations)
							: []),
					].map((citation) => citation.path),
				),
			];
			return `- ${slug}: ${candidate.outcome} — ${candidate.summary} (cites ${cited.join(", ")})`;
		})
		.join("\n");
}

interface SubmitResultDetails {
	inserted: number;
	revised: number;
	duplicates: number;
	refused: number;
}

const MAX_REFUSAL_LOG_CHARS = 500;

function logRefusal(slug: string, reason: string): void {
	const line = `[pi-runner] result refused for ${slug}: ${reason}`;
	console.error(
		line.length > MAX_REFUSAL_LOG_CHARS ? `${line.slice(0, MAX_REFUSAL_LOG_CHARS)}…` : line,
	);
	if (currentTurn) {
		currentTurn.refused += 1;
		currentTurn.refusalReasons.push(
			reason.length > TRACE_REASON_CHARS ? `${reason.slice(0, TRACE_REASON_CHARS)}…` : reason,
		);
	}
}

/** The practices the current session asked about; a recorded result for one of them is what it owes. */
let currentTurnSlugs: readonly string[] = [];

/**
 * The one practice the measuring session in flight assesses, or null outside measurement. Each review gets
 * a session of its own, and its result is bound to that practice.
 */
let activePractice: string | null = null;

/** The given practices, by default the session's, that still owe the open review a result. */
function owedPractices(slugs: readonly string[] = currentTurnSlugs): string[] {
	const review = activeReview;
	return review === null || review.draft !== null || blockedPractices.has(review.slug)
		? []
		: slugs.filter((slug) => slug === review.slug);
}

/** JSON Schema keywords that refuse; what they say is applied per unit instead. */
const RULE_KEYWORDS = new Set([
	"required",
	"additionalProperties",
	"minLength",
	"maxLength",
	"minItems",
	"maxItems",
	"pattern",
	"minimum",
	"maximum",
]);

/**
 * Pi validates a whole tool call before execution. For a tool that takes a batch of independent units, keep shape
 * hints here and let the tool validate each unit, so one invalid unit does not discard the rest of the batch.
 */
function documentedShape(schema: unknown): unknown {
	if (Array.isArray(schema)) {
		return schema.map(documentedShape);
	}
	if (!isRecord(schema)) {
		return schema;
	}
	const out: Record<string, unknown> = {};
	// Container types go, so a list may arrive serialized and each unit is answered on its own; scalar
	// types stay, because they tell the model what a value is.
	const structural = schema.type === "object" || schema.type === "array";
	const noted = (note: string) => {
		const description = typeof out.description === "string" ? out.description : "";
		out.description = description ? `${description} ${note}` : note;
	};
	if (typeof schema.description === "string") {
		out.description = schema.description;
	}
	for (const [key, value] of Object.entries(schema)) {
		if (key === "description") {
			continue;
		}
		// What the schema required is still said, in words: the rule is applied by the tool, but the
		// session is told which fields a call cannot do without.
		if (key === "required" && Array.isArray(value) && value.length > 0) {
			noted(`Required: ${value.map(String).join(", ")}.`);
			continue;
		}
		if (RULE_KEYWORDS.has(key) || (key === "type" && structural)) {
			continue;
		}
		if (key === "enum" && Array.isArray(value)) {
			const listed = value.map((item) => (item === null ? "null" : String(item))).join(", ");
			const description = typeof out.description === "string" ? out.description : "";
			if (!description.includes(listed.split(", ")[0] ?? "")) {
				noted(`One of: ${listed}.`);
			}
			continue;
		}
		out[key] = documentedShape(value);
	}
	return out;
}

function buildSubmitResultTool() {
	return defineTool({
		name: "submit_result",
		exposure: "model-only",
		label: "Submit Result",
		description:
			"Submit the one complete evidenced result of this session's practice: its outcome, the explanation, the " +
			"citations it rests on, and for NOT_MET each problem with its severity, citations and basis. The host adds " +
			"the coverage it measured from your reads and keeps the result for server admission after measuring. To " +
			"correct it, submit the complete result again.",
		parameters: resultSchema,
		prepareArguments: prepareResultArguments,
		execute: async (toolCallId, params): Promise<AgentToolResult<SubmitResultDetails>> => {
			if (measurementClosed || activeReview === null) {
				const text = "Measurement is closed; this turn may only compose feedback.";
				return {
					content: [{ type: "text", text }],
					details: { inserted: 0, revised: 0, duplicates: 0, refused: 0 },
				};
			}
			// The result is a typed value, never a second JSON document to parse or repair.
			if (!isRecord(params) || Array.isArray(params)) {
				const reason = "the arguments must be one result object";
				logRefusal("(not an object)", reason);
				return refusal(toolCallId, `result refused — ${reason}. Nothing changed.`);
			}
			const outcome = record(params);
			const stored = outcome.kind === "stored" || outcome.kind === "revised" ? 1 : 0;
			if (outcome.kind === "refused") {
				logRefusal(outcome.slug, outcome.reason);
			}
			if (currentTurn) {
				currentTurn.stored += stored;
				sessionGuards.stored += stored;
			}
			if (stored > 0) {
				persistRecordedNotes();
			}
			const head = `${outcome.slug}:`;
			let text: string;
			if (outcome.kind === "stored" || outcome.kind === "revised") {
				text = `${head} ${outcome.kind}${outcome.negative ? " (negative)" : ""}. A later complete submission replaces it.${outcome.filled.map((line) => `\n   ${line}`).join("")}`;
			} else if (outcome.kind === "duplicate") {
				text = `${head} already submitted; this result changed nothing, so do not send it again.`;
			} else {
				text = `${head} refused — ${outcome.reason}`;
			}
			if (blockedPractices.has(outcome.slug) && refusalLimited(outcome.slug)) {
				text += `\nNo longer accepted (refusal limit): ${outcome.slug}.`;
			}
			const details: SubmitResultDetails = {
				inserted: outcome.kind === "stored" ? 1 : 0,
				revised: outcome.kind === "revised" ? 1 : 0,
				duplicates: outcome.kind === "duplicate" ? 1 : 0,
				refused: outcome.kind === "refused" ? 1 : 0,
			};
			// A call that stored nothing is an error the session must correct — a resend of what is already
			// submitted included, or it reads as success and is sent again.
			if (stored === 0) {
				return refusal(toolCallId, text);
			}
			// The session owes nothing more once its practice has a result; ending the run here spares the
			// wrap-up calls a session otherwise makes after its last submission.
			return { content: [{ type: "text", text }], details, terminate: true };
		},
	});
}

function accumulateUsage(prev: UsageReport | null, curr: UsageReport): void {
	usageTotals.model = curr.model ?? usageTotals.model;
	usageTotals.inputTokens += Math.max(0, curr.inputTokens - (prev?.inputTokens ?? 0));
	usageTotals.outputTokens += Math.max(0, curr.outputTokens - (prev?.outputTokens ?? 0));
	usageTotals.reasoningTokens += Math.max(0, curr.reasoningTokens - (prev?.reasoningTokens ?? 0));
	usageTotals.cacheReadTokens += Math.max(0, curr.cacheReadTokens - (prev?.cacheReadTokens ?? 0));
	usageTotals.cacheWriteTokens += Math.max(
		0,
		curr.cacheWriteTokens - (prev?.cacheWriteTokens ?? 0),
	);
	usageTotals.costUsd += Math.max(0, curr.costUsd - (prev?.costUsd ?? 0));
	usageTotals.totalCalls += Math.max(0, curr.totalCalls - (prev?.totalCalls ?? 0));
}

/**
 * Derive the change view from the captured checkout and the pinned range, before any program runs. A capture
 * that fails here fails the review: an empty change is never what a failed capture reads as.
 */
function deriveChangeView(): void {
	const change = readChange(INPUT_PATHS.contextRoot);
	if (change !== null) {
		writeChangeView(
			CWD,
			INPUT_PATHS.repositoryRoot,
			change,
			readDescription(INPUT_PATHS.contextRoot),
		);
	}
}

/** The captured change, parsed from the derived view; null when this work has none. */
function capturedChange(): { base: string; head: string; files: Map<string, DiffFile> } | null {
	const change = readChange(INPUT_PATHS.contextRoot);
	const view = `${CWD}/${CHANGE_ROOT}/diff.patch`;
	if (change === null || !existsSync(view)) {
		return null;
	}
	return { ...change, files: parseDiff(readFileSync(view, "utf8")) };
}

/** What the programs of this review read: the pinned checkout, the change and the captured records. */
function captureSources(): EvidenceSources {
	const repository = INPUT_PATHS.repositoryRoot;
	const checkout = existsSync(`${repository}/.git`) ? repository : null;
	const change = capturedChange();
	return {
		repo: checkout,
		headRevision: change?.head ?? (checkout === null ? null : checkedOutCommit(checkout)),
		treeArtifact: `${taskEnvelope.paths.repositoryRoot}/.git/HEAD`,
		contextDir: existsSync(INPUT_PATHS.contextRoot) ? INPUT_PATHS.contextRoot : null,
		contextReference: taskEnvelope.paths.contextRoot,
		change,
		sourceKindOf: (artifactPath) => artifactSources.get(artifactPath),
	};
}

/** The files a program of `kind` reads: the pinned tree and the change of a pull request, and none otherwise. */
function jobFiles(kind: string): JobFiles | null {
	const sources = capturedChange();
	const repository = INPUT_PATHS.repositoryRoot;
	const head =
		sources?.head ?? (existsSync(`${repository}/.git`) ? checkedOutCommit(repository) : null);
	if (kind !== "scm.pull_request" || head === null) {
		return null;
	}
	return { head: { revision: head }, change: sources };
}

/** The direct models the server bound for this job; none when it staged none or they do not parse. */
function readBoundModels(): BoundModels {
	if (!existsSync(PRACTICE_SCRIPT_MODELS_PATH)) {
		return {};
	}
	try {
		return boundModelsSchema.parse(parseJson(readFileSync(PRACTICE_SCRIPT_MODELS_PATH, "utf8")));
	} catch (error) {
		console.error(
			`[pi-runner] the bound models do not parse, so none is bound: ${errorText(error)}`,
		);
		return {};
	}
}

function loadPracticeSlugs(): string[] {
	return practiceIndex.map((practice) => practice.slug).filter(Boolean);
}

let practiceCoverageLedger: PracticeCoverageLedger | null = null;

function persistPracticeCoverage() {
	if (practiceCoverageLedger === null) {
		throw new Error("practice coverage ledger is not initialized");
	}
	return practiceCoverageLedger.markEvaluated([...reviewState.keys()]);
}

function logPracticeCoverage() {
	const coverage = persistPracticeCoverage();
	const ratio =
		coverage.eligible === 0 ? "n/a" : (coverage.evaluated / coverage.eligible).toFixed(4);
	console.error(
		`[pi-runner] Practice coverage: evaluated=${coverage.evaluated}, eligible=${coverage.eligible}, ratio=${ratio}`,
	);
}

/** What a private composer that reads instead of writing is told, at the exploration bound and near the budget's end. */
const COMPOSITION_NUDGE =
	`Stop reading: the admitted results with their observations and the history are in this turn's prompt and in ` +
	`work/composition/results.json, and nothing else decides a unit. Persist the units you have ` +
	`with report_feedback now — and a WITHHOLD with its reason for each NOT_MET practice you decided ` +
	`to stay quiet about. Use tools only from this point onward; no planning prose.`;

/** What the composer of the review on the work is told near its budget's end. */
const REVIEW_NUDGE =
	`Everything this review may rest on is in this session. Store the final review now with one report_review call: ` +
	`first one decision for each NOT_MET result, RAISE or a withholding reason, then the complete summary and ` +
	`any line notes, which speak about each raised result, itself or through its observations, and no withheld one. ` +
	`No prose outside the calls.`;

/** Calls a composition may make before its first recording call; at this one it is nudged to persist. */
const COMPOSITION_EXPLORATION_NUDGE = 12;

const PERSIST_DISCIPLINE =
	`Submit the outcome the evidence supports, MET, NOT_MET, NOT_APPLICABLE, or UNDETERMINED; there is no quota. ` +
	`Use tools only from this point onward; no planning prose.`;

/** What a turn is told once its remaining work only pays for submitting what it owes. */
const RECORD_NUDGE = `This turn has the work left to write its result and no more. Stop exploring. Submit what the inspected evidence supports for this session's practice with submit_result. ${PERSIST_DISCIPLINE}`;

/**
 * The one request a review session gets when it ended on its own with its practice's result unsubmitted. It goes
 * to the same session, which still holds what it read, and leaves a collection gap unsubmitted.
 */
function recordOnceMoreText(slug: string): string {
	return (
		`## Not recorded\nThis session ended without a result for ${slug}: text outside a ` +
		`submit_result call records nothing. Submit the outcome its criterion and the evidence you read ` +
		`support, in one submit_result call. If a required source is missing, truncated or blocked, that ` +
		`is a collection gap: submit nothing.`
	);
}

/** Tells the server to retry a review whose admission endpoint was unreachable. */
const SERVER_UNREACHABLE_EXIT = 75;
/** Tells the server to retry a review that recorded nothing after provider failures. */
const PROVIDER_UNREACHABLE_EXIT = 76;

function readTaskEnvelope(): TaskEnvelope {
	let raw: string;
	try {
		raw = readFileSync(TASK_PATH, "utf8");
	} catch (error) {
		console.error(`[pi-runner] Failed to read ${TASK_PATH}: ${errorText(error)}`);
		process.exit(ENVELOPE_MISMATCH_EXIT);
	}
	let parsed: unknown;
	try {
		parsed = parseJson(raw);
	} catch (error) {
		console.error(`[pi-runner] Failed to parse ${TASK_PATH}: ${errorText(error)}`);
		process.exit(ENVELOPE_MISMATCH_EXIT);
	}
	const envelope: Record<string, unknown> = isRecord(parsed) ? parsed : {};
	if (envelope.schemaVersion !== SUPPORTED_SCHEMA_VERSION) {
		console.error(
			`[pi-runner] Unsupported schemaVersion: got ${logValue(envelope.schemaVersion)}, expected ${SUPPORTED_SCHEMA_VERSION}. ` +
				`Task envelope and staged runner disagree.`,
		);
		process.exit(ENVELOPE_MISMATCH_EXIT);
	}
	if (typeof envelope.prompt !== "string" || envelope.prompt.trim() === "") {
		console.error(`[pi-runner] prompt is missing or blank in ${TASK_PATH}`);
		process.exit(ENVELOPE_MISMATCH_EXIT);
	}
	let paths: ReturnType<typeof taskPaths>;
	try {
		paths = taskPaths(envelope);
	} catch (error) {
		console.error(`[pi-runner] ${errorText(error)}`);
		process.exit(ENVELOPE_MISMATCH_EXIT);
	}
	return {
		paths,
		schemaVersion: SUPPORTED_SCHEMA_VERSION,
		jobId: envelope.jobId,
		workspaceId: envelope.workspaceId,
		prompt: envelope.prompt,
		repositoryFullName: envelope.repositoryFullName,
		pullRequestNumber: envelope.pullRequestNumber,
	};
}

const prompt = taskEnvelope.prompt.trim();
console.error(
	`[pi-runner] Task envelope loaded: ` +
		`jobId=${logValue(taskEnvelope.jobId)}, workspaceId=${logValue(taskEnvelope.workspaceId)}, ` +
		`repository=${logValue(taskEnvelope.repositoryFullName ?? "?")}, ` +
		`prNumber=${logValue(taskEnvelope.pullRequestNumber ?? "?")}`,
);

const COMPOSITION_REQUEST_PATH = INPUT_PATHS.compositionRequest;
const FEEDBACK_PATH = outputPath(OUTPUT, "feedback.json");
const COMPOSER_PROMPT_PATH = `${CWD}/feedback-composer.md`;
const REVIEW_COMPOSER_PROMPT_PATH = `${CWD}/review-composer.md`;
/** The writing style both compositions share, ahead of each one's own instructions. */
const FEEDBACK_STYLE_PATH = `${CWD}/feedback-style.md`;
const PREPARED_FEEDBACK_PATH = INPUT_PATHS.preparedFeedback;
const COMPOSITION_RESULTS_PATH = `${CWD}/work/composition/results.json`;
const IN_APP_SUPPORT_PATH = `${CWD}/work/composition/in-app-support.json`;

let inAppSupport: InAppSupport | null = null;
let inChatEligibility: InChatEligibility | null = null;

/** The open private lanes each NOT_MET practice is routed to; null until admission routed them. */
let routedLanes: ReadonlyMap<string, readonly PrivateChannel[]> | null = null;

/** Whether the server's read after admission routes the practice's NOT_MET result to the lane. */
function routedTo(channel: PrivateChannel, practiceSlug: string): boolean {
	if (channel === "IN_APP") {
		return (
			inAppSupport?.state === "COMPLETE" &&
			inAppSupport.practices.some(
				(practice) => practice.practiceSlug === practiceSlug && practice.decision === "ADMIT",
			)
		);
	}
	if (inChatEligibility?.state !== "COMPLETE") {
		return false;
	}
	const admitted = new Set(
		inChatEligibility.results
			.filter((route) => route.decision === "ADMIT")
			.map((route) => route.practiceResultId),
	);
	return admittedResults.some(
		(result) =>
			result.practiceSlug === practiceSlug &&
			result.outcome === "NOT_MET" &&
			admitted.has(result.practiceResultId),
	);
}

/** Why the server routes none of the practice's NOT_MET results to the lane, in its own words. */
function laneRefusal(channel: PrivateChannel, practiceSlug: string): string {
	if (channel === "IN_APP") {
		const decision =
			inAppSupport?.state === "COMPLETE"
				? inAppSupport.practices.find((practice) => practice.practiceSlug === practiceSlug)
						?.decision
				: undefined;
		return decision ?? "not routed";
	}
	const ids = new Set(
		admittedResults
			.filter((result) => result.practiceSlug === practiceSlug)
			.map((result) => result.practiceResultId),
	);
	const decisions =
		inChatEligibility?.state === "COMPLETE"
			? inChatEligibility.results
					.filter((route) => ids.has(route.practiceResultId))
					.map((route) => route.decision)
			: [];
	return decisions.length > 0 ? [...new Set(decisions)].join(", ") : "not routed";
}
const PUBLIC_FEEDBACK_HISTORY_PATH = `${CWD}/work/composition/public-feedback-history.json`;
let compositionAdmitted = false;
let admissionDigest: string | null = null;

type Channel = (typeof CHANNELS)[number];
type PrivateChannel = (typeof PRIVATE_CHANNELS)[number];
type FeedbackAction = (typeof ACTIONS)[number];

function isChannel(value: unknown): value is Channel {
	return CHANNELS.some((channel) => channel === value);
}

function isFeedbackAction(value: unknown): value is FeedbackAction {
	return ACTIONS.some((action) => action === value);
}

/** A string the model sent, or nothing — used for every optional free-text field on a feedback unit. */
function optionalString(value: unknown): string | undefined {
	return typeof value === "string" ? value : undefined;
}

/** The notes an IN_CHAT unit carries TO the mentor. Kept in step with ConversationBrief by Java. */
interface ConversationNotes {
	situation?: string;
	capability?: string;
	evidenceSummary?: string;
	inConversationSignal?: string;
	alreadySaid?: string;
}

/**
 * A private-lane unit; per-unit validation in readFeedbackUnit/validateUnit. As read, basedOn holds the ids the
 * composer named; as stored, the references they resolve to.
 */
interface FeedbackUnitOf<Basis> extends ComposedFeedbackUnit {
	channel: PrivateChannel;
	practiceSlug: string;
	basedOn: Basis;
	action: FeedbackAction;
	supersedesThreadKey?: string;
	withholdReason?: string;
	title?: string;
	body?: string;
	nextStep?: string;
	notes?: ConversationNotes;
}
type ReadFeedbackUnit = FeedbackUnitOf<string[]>;
type FeedbackUnit = FeedbackUnitOf<EvidenceRef[]>;

/** The admitted fields the server resolves references against: enough to reference one, never enough to author one. */
interface LeanCitation {
	index: number;
	sourceKind: unknown;
	path: unknown;
	side: unknown;
	startLine: unknown;
	endLine: unknown;
	anchorable: unknown;
}

interface LeanResult {
	practiceResultId: string;
	practiceSlug: string;
	outcome: Outcome;
	severity: unknown;
	citations: LeanCitation[];
	observations: {
		observationId: string;
		severity: unknown;
		anchorable: unknown;
		citations: LeanCitation[];
	}[];
}

/** What gets written to feedback.json, with the fields the reader resolves references against. */
type CompositionPhase = "PUBLIC_REVIEW" | "PRIVATE_FEEDBACK";
type CompositionFailureReason =
	| "MODEL_ERROR"
	| "RUNTIME_ERROR"
	| "BUDGET"
	| "STALL"
	| "SAFETY"
	| "LOOP"
	| "NO_DECISION"
	| "SERVER_REFUSED"
	| "PRIVATE_NOT_RECOVERED";
interface CompositionFailure {
	phase: CompositionPhase;
	reason: CompositionFailureReason;
}

interface ComposedFeedback extends ComposedFeedbackEnvelope {
	compositionFailures: CompositionFailure[];
	contractVersion: number;
	admissionDigest: string | null;
	results: LeanResult[];
	preparedTargets: PreparedFeedbackTarget[];
	units: FeedbackUnit[];
	review: ComposedReview | null;
}

// Echo the exact composition inputs so Java validates references against the same snapshot.
const composedFeedback: ComposedFeedback = {
	compositionFailures: [],
	contractVersion: REVIEW_CONTRACT_VERSION,
	admissionDigest: null,
	results: [],
	preparedTargets: [],
	units: [],
	review: null,
};

/**
 * Admission assigns result and observation IDs and anchor eligibility. Populate this captured array in place
 * because the composer tool is created before admission.
 */
const admittedResults: AdmittedResult[] = [];

function isPlacementKind(value: unknown): value is PlacementKind {
	return value === "DIFF" || value === "ARTIFACT";
}

function loadCompositionRequest(): CompositionRequest | null {
	try {
		if (!existsSync(COMPOSITION_REQUEST_PATH)) {
			return null;
		}
		const parsed = parseJson(readFileSync(COMPOSITION_REQUEST_PATH, "utf8"));
		if (!isRecord(parsed) || parsed.enabled !== true) {
			return null;
		}
		const declared: Record<string, unknown> = isRecord(parsed.channels) ? parsed.channels : {};
		const boundsFor = (channel: Channel): ChannelBounds => {
			const bounds: Record<string, unknown> = isRecord(declared[channel]) ? declared[channel] : {};
			return {
				enabled: bounds.enabled === true,
				maxUnits: Math.max(0, Math.min(Number(bounds.maxUnits) || 0, 10)),
			};
		};
		// Spelled out rather than folded into the CHANNELS loop, so that Record<Channel, …> is what makes
		// every lane present: adding a channel to CHANNELS then fails to compile until it is bounded here.
		const channels: Record<Channel, ChannelBounds> = {
			IN_CONTEXT: boundsFor("IN_CONTEXT"),
			IN_APP: boundsFor("IN_APP"),
			IN_CHAT: boundsFor("IN_CHAT"),
		};
		if (!CHANNELS.some((channel) => channels[channel].enabled && channels[channel].maxUnits > 0)) {
			return null;
		}
		const inContextPlacementKinds = jsonArray(parsed.inContextPlacementKinds).filter(
			isPlacementKind,
		);
		if (channels.IN_CONTEXT.enabled && inContextPlacementKinds.length === 0) {
			return null;
		}
		return {
			channels,
			inContextPlacementKinds,
			minDistinctArtifacts: Math.max(2, Number(parsed.minDistinctArtifacts) || 2),
		};
	} catch (error) {
		console.error(`[pi-runner] composition request unreadable: ${errorText(error)}`);
		return null;
	}
}

function composablePracticeSlugs(): string[] {
	return [...admittedPractices].toSorted();
}

// Supersession is limited to unread thread keys present in this snapshot.
function stagedPreparedTargets(): PreparedFeedbackTarget[] {
	try {
		if (!existsSync(PREPARED_FEEDBACK_PATH)) {
			return [];
		}
		const prepared = parseJson(readFileSync(PREPARED_FEEDBACK_PATH, "utf8"));
		const entries = isRecord(prepared) ? jsonArray(prepared.prepared) : [];
		return entries.flatMap((entry) => {
			if (!isRecord(entry)) {
				return [];
			}
			const { threadKey, channel, practiceSlug } = entry;
			return typeof threadKey === "string" &&
				threadKey.trim() &&
				isChannel(channel) &&
				typeof practiceSlug === "string" &&
				practiceSlug.trim()
				? [{ threadKey, channel, practiceSlug }]
				: [];
		});
	} catch (error) {
		console.error(`[pi-runner] prepared feedback unreadable for composition: ${errorText(error)}`);
		return [];
	}
}

/**
 * The `channel:practice` pairs the staged delivered feedback can show as already said. The server chose the person
 * and the records this review may read; a record counts only when it is current, not withdrawn, carries its staged
 * words and a delivery time, and rests on a NOT_MET observation of the practice. Prepared feedback reached no one, and
 * a history that cannot be read does not establish prior communication.
 */
function stagedDeliveredPractices(): Set<string> {
	const file = `${nodePath.dirname(PREPARED_FEEDBACK_PATH)}/feedback.json`;
	try {
		if (!existsSync(file)) {
			return new Set();
		}
		const history = parseJson(readFileSync(file, "utf8"));
		if (
			!isRecord(history) ||
			history.recordRole !== "RECORDED_DELIVERY" ||
			!Array.isArray(history.feedback)
		) {
			return new Set();
		}
		return new Set(
			history.feedback.flatMap((entry: unknown) => {
				if (!isRecord(entry)) {
					return [];
				}
				const { channel, recordedClaimCurrentness, withdrawn, body, deliveredAt } = entry;
				if (
					(channel !== "IN_APP" && channel !== "IN_CHAT") ||
					recordedClaimCurrentness !== "CURRENT" ||
					(withdrawn !== undefined && withdrawn !== false) ||
					typeof body !== "string" ||
					body.trim() === "" ||
					typeof deliveredAt !== "string" ||
					!Number.isFinite(Date.parse(deliveredAt))
				) {
					return [];
				}
				return jsonArray(entry.basedOn).flatMap((support) =>
					isRecord(support) && support.outcome === "NOT_MET"
						? [`${channel}:${normalizePracticeSlug(support.practiceSlug)}`]
						: [],
				);
			}),
		);
	} catch (error) {
		console.error(`[pi-runner] delivered feedback unreadable for composition: ${errorText(error)}`);
		return new Set();
	}
}

function leanCitation(citation: AdmittedCitation): LeanCitation {
	return {
		index: citation.index,
		sourceKind: citation.sourceKind,
		path: citation.path,
		side: citation.side,
		startLine: citation.startLine,
		endLine: citation.endLine,
		anchorable: citation.anchorable,
	};
}

// Inline placement requires an observation's citation inside the current diff.
function leanResults(results: readonly AdmittedResult[]): LeanResult[] {
	return results.map((result) => ({
		practiceResultId: result.practiceResultId,
		practiceSlug: result.practiceSlug,
		outcome: result.outcome,
		severity: result.severity,
		citations: result.citations.map(leanCitation),
		observations: result.observations.map((observation) => ({
			observationId: observation.observationId,
			severity: observation.severity,
			// The server places a line note only on an observation that admission marked anchorable.
			anchorable: observation.anchorable,
			citations: observation.citations.map(leanCitation),
		})),
	}));
}

/** The admitted results by practiceResultId: what an id the composer names is resolved against. */
function resultsById(results: readonly AdmittedResult[]): Map<string, AdmittedResult> {
	return new Map(results.map((result) => [result.practiceResultId, result]));
}

function persistComposedFeedback(): void {
	for (const unit of undeliverableUnits(composedFeedback)) {
		const { channel, practiceSlug, supersedesThreadKey } = unit;
		console.error(
			channel === undefined || practiceSlug === undefined || supersedesThreadKey === undefined
				? `[pi-runner] composed feedback the server cannot deliver: a SUPERSEDE unit lacks the channel, ` +
						`practice or thread it replaces: ${JSON.stringify({ channel, practiceSlug, supersedesThreadKey })}`
				: `[pi-runner] composed feedback the server cannot deliver: ${channel}/${practiceSlug} ` +
						`supersedes '${supersedesThreadKey}', which this envelope does not list as staged`,
		);
	}
	// The server reads at most 30 units. Keep deliverable feedback ahead of WITHHOLD records.
	const units = composedFeedback.units.toSorted(
		(a, b) => Number(a.action === "WITHHOLD") - Number(b.action === "WITHHOLD"),
	);
	writeFileSync(FEEDBACK_PATH, JSON.stringify({ ...composedFeedback, units }, null, 2));
}

// The composer references admitted results and observations; it cannot author verdicts, citations, or locations.
/** What report_feedback reports back: a refusal stores nothing and names no lane. */
interface ReportFeedbackDetails {
	stored: number;
	channel?: Channel;
	total?: number;
}

/** A unit the tool did not store, with the reason the session is told. */
function skipped(text: string): { stored: boolean; text: string } {
	return { stored: false, text };
}

function buildFeedbackTool(
	practiceSlugs: string[],
	request: CompositionRequest,
	results: readonly AdmittedResult[],
	preparedTargets: PreparedFeedbackTarget[],
	deliveredPractices: ReadonlySet<string>,
) {
	// The reader resolves supersession against the envelope's copy of this list and drops any unit
	// naming a thread outside it, so the vocabulary is recorded here, where it is decided.
	composedFeedback.preparedTargets = preparedTargets;
	const enabledChannels = PRIVATE_CHANNELS.filter((channel) => request.channels[channel].enabled);
	const usedPerChannel: Record<PrivateChannel, number> = { IN_APP: 0, IN_CHAT: 0 };
	const seen = new Set<string>();

	/** One unit stored, or the reason it was not. */
	const store = (value: unknown): { stored: boolean; text: string } => {
		const repairs: string[] = [];
		const read = readFeedbackUnit(withholdBasis(value, results, repairs), practiceSlugs);
		if (typeof read === "string") {
			return { stored: false, text: read };
		}
		const unit = read;
		// Built per call: the tool is defined before admission fills the results it reads.
		const admitted = resultsById(results);
		const bounds = request.channels[unit.channel];
		if (!bounds.enabled) {
			return skipped(
				`${unit.channel} is not a lane this run may write for (open: ${enabledChannels.join(", ")}); skipped.`,
			);
		}
		const key = `${unit.channel}:${unit.practiceSlug}`;
		if (seen.has(key)) {
			return skipped(
				`already have a ${unit.channel} unit for ${unit.practiceSlug}, and the first one stands; nothing to resend for it. Skipped.`,
			);
		}
		const delivers = unit.action !== "WITHHOLD";
		if (delivers && usedPerChannel[unit.channel] >= bounds.maxUnits) {
			return skipped(
				`${unit.channel} holds at most ${bounds.maxUnits} unit(s) and is full: this one does not fit — WITHHOLD it with BELOW_BAR or leave it out. Skipped.`,
			);
		}
		const rejection = validateUnit(unit, admitted, preparedTargets, deliveredPractices);
		if (rejection !== null) {
			return skipped(rejection);
		}
		// The server's routing decides the lanes a practice may reach; a withholding elsewhere decides nothing.
		if (!(routedLanes?.get(unit.practiceSlug) ?? []).includes(unit.channel)) {
			return skipped(
				`${unit.channel} cannot carry feedback about ${unit.practiceSlug} this run ` +
					`(${laneRefusal(unit.channel, unit.practiceSlug)}); write no unit for it there, not even a WITHHOLD. Skipped.`,
			);
		}
		// IN_APP pattern feedback requires NOT_MET results on distinct pieces of work.
		if (unit.channel === "IN_APP" && delivers) {
			const pieces = negativePiecesOfWork(unit.practiceSlug);
			if (pieces < request.minDistinctArtifacts) {
				return skipped(
					`IN_APP needs a pattern across at least ${request.minDistinctArtifacts} pieces of work, and ` +
						`${unit.practiceSlug} has support on ${pieces} (the IN_APP support read); WITHHOLD it ` +
						`with BELOW_BAR. Skipped.`,
				);
			}
		}
		seen.add(key);
		if (delivers) {
			usedPerChannel[unit.channel] += 1;
		}
		composedFeedback.units.push({
			...unit,
			basedOn: evidenceRefs(unit.basedOn, namedEvidence(admitted)),
		});
		return {
			stored: true,
			text: `stored a ${unit.channel} unit for ${unit.practiceSlug} (${unit.action}); ${usedPerChannel[unit.channel]}/${bounds.maxUnits} used on that lane.${repairs.map((repair) => ` ${repair}.`).join("")}`,
		};
	};

	return defineTool({
		name: "report_feedback",
		exposure: "model-only",
		label: "Report Feedback",
		description:
			"Persist feedback units for the developer's practice pages and the mentor conversation, one per " +
			"channel and practice. Send every unit you have ready in one call; each is stored or skipped on " +
			"its own, with the reason, and a skipped unit can be sent again corrected without re-sending the " +
			"stored ones. This is an intervention, not a measurement: it takes no outcome, severity or " +
			"confidence, and no citation you typed yourself.",
		// See documentedShape: validate feedback per unit, not per tool call.
		parameters: {
			type: "object",
			required: ["units"],
			properties: {
				units: documentedShape(
					listSchema(
						{
							type: "object",
							required: ["channel", "practiceSlug", "basedOn", "action"],
							properties: {
								channel: {
									type: "string",
									enum: enabledChannels,
									description:
										"Which surface this unit is for. Each has its own level and its own rules.",
								},
								practiceSlug: {
									type: "string",
									enum: practiceSlugs,
									description:
										"The primary practice whose intervention this unit advances. Related results may support it.",
								},
								basedOn: {
									type: "array",
									minItems: 1,
									items: { type: "string", minLength: 1 },
									description:
										"What this rests on: admitted ids from this run, a result's practiceResultId or, for one located problem, an observation's observationId. Include related practices only when they describe the same underlying event.",
								},
								action: {
									type: "string",
									enum: ACTIONS,
									description:
										"NEW to say something; SUPERSEDE to replace prepared feedback that has not been delivered; " +
										"WITHHOLD to record, with a reason, that you decided to stay quiet.",
								},
								supersedesThreadKey: {
									type: "string",
									maxLength: FEEDBACK_TEXT_BOUNDS.supersedesThreadKey,
									description:
										"Required for SUPERSEDE: the threadKey of an entry in the task-declared prepared-feedback file. " +
										"You may not name a key that is not in that file.",
								},
								withholdReason: { type: "string", enum: WITHHOLD_REASONS },
								title: {
									type: "string",
									maxLength: FEEDBACK_TEXT_BOUNDS.title,
									description: "Names the issue in a few words. Never names the person.",
								},
								body: {
									type: "string",
									maxLength: FEEDBACK_TEXT_BOUNDS.body,
									description:
										"IN_APP only: the evidenced pattern across the work and why it matters; the future way of working belongs to nextStep. Grounded in current results and their observations; never quote a line or claim change over time from the pre-run history. Markdown, read verbatim.",
								},
								nextStep: {
									type: "string",
									maxLength: FEEDBACK_TEXT_BOUNDS.nextStep,
									description:
										"IN_APP only: the one repeatable way of working for the next piece of work; the body does not state it. Name the missing decision, not a heading/template unless the practice requires one; never provide paste-ready prose.",
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
											maxLength: FEEDBACK_TEXT_BOUNDS.situation,
											description:
												"The concern the observations have in common, as it can be observed, stated once rather than " +
												"listing each artifact again. Your words about it, not words for it - third person, never " +
												"addressed to the developer as 'you', and never a judgement of the person.",
										},
										capability: {
											type: "string",
											maxLength: FEEDBACK_TEXT_BOUNDS.capability,
											description:
												"The self-check of the assessed behaviour this conversation should support: how the developer can tell whether their own work shows it, not only whether something is wired up. State the capability, not a solution such as a required heading/template, and not a question, script, diagnosis, or fixed tactic.",
										},
										evidenceSummary: {
											type: "string",
											maxLength: FEEDBACK_TEXT_BOUNDS.evidenceSummary,
											description:
												"Where the concern occurred, compactly: each supporting work and observation located once. " +
												"Summarise rather than inventing a quote; the original result evidence is " +
												"staged separately for the mentor to inspect.",
										},
										inConversationSignal: {
											type: "string",
											maxLength: FEEDBACK_TEXT_BOUNDS.inConversationSignal,
											description:
												"Understanding the developer articulates in a way that can be observed before the conversation ends: a distinction, decision, question, or self-check they say. Not a promise, future artifact, message text, or compliance target.",
										},
										alreadySaid: {
											type: "string",
											maxLength: FEEDBACK_TEXT_BOUNDS.alreadySaid,
											description:
												"Optional. Relevant prior communication the shown history records, and movement the shown evidence supports. Omit when none is shown. Partial or missing history leaves coverage unknown: it never proves that nothing was said or that a concern was resolved.",
										},
									},
								},
							},
						},
						"units",
					),
				),
			},
		},
		execute: async (toolCallId, params): Promise<AgentToolResult<ReportFeedbackDetails>> => {
			if (!compositionAdmitted) {
				return {
					content: [
						{
							type: "text",
							text: "Feedback composition opens only after Java admits the completed results.",
						},
					],
					details: { stored: 0 },
				};
			}
			const submitted = submittedList(isRecord(params) ? params.units : null, UNIT_ITEM_KEYS);
			if ("error" in submitted) {
				return refusal(toolCallId, `units refused — ${submitted.error}`);
			}
			if (submitted.items.length === 0) {
				return refusal(
					toolCallId,
					"units refused — the list is empty; send every unit you have ready in it",
				);
			}
			const outcomes = submitted.items.map(store);
			const stored = outcomes.filter((outcome) => outcome.stored).length;
			if (currentTurn) {
				currentTurn.stored += stored;
				sessionGuards.stored += stored;
			}
			if (stored > 0) {
				persistComposedFeedback();
			}
			const text = outcomes.map((outcome, index) => `#${index + 1}: ${outcome.text}`).join("\n");
			// A call that stored nothing is an error the session must correct — the reasons are in it;
			// one that stored some of what it sent is an answer, with the skips named per unit.
			if (stored === 0) {
				return refusal(toolCallId, text);
			}
			return {
				content: [{ type: "text", text }],
				details: { stored, total: composedFeedback.units.length },
				terminate: turnDemand?.endsWhenPaid === true && turnDemand.owed() === 0,
			};
		},
	});
}

interface ReportReviewDetails {
	stored: number;
}

/**
 * Once final, the tool refuses every call: one response can carry several calls after the final one. A completed
 * compaction takes the opening reference out of view, and the next turn restores it before a review is accepted.
 */
interface PublicReviewState {
	final: boolean;
	reviewContext: "LOST" | "RESTORING" | "HELD";
}

/** The public results the review may rest on, by practiceResultId, each with its observations' citations. */
function reviewableById(
	reviewable: readonly Record<string, unknown>[],
): Map<string, ReviewedResult> {
	return new Map(
		reviewable.map((result) => [
			String(result.practiceResultId),
			{
				practiceSlug: String(result.practiceSlug),
				summaryOnly: result.summaryOnly === true,
				outcome: result.outcome,
				observations: jsonArray(result.observations)
					.filter(isRecord)
					.map((observation) => ({
						observationId: String(observation.observationId),
						citations: jsonArray(observation.citations).filter(isRecord),
					})),
			},
		]),
	);
}

const REVIEW_FINAL = "The review on this work is final; this composition accepts nothing more.";

/**
 * Reads the review against only the NOT_MET results admission marked publicEligible and their observations, and the
 * statements shown as already said.
 */
function buildReviewTool(
	lineNotes: boolean,
	restable: ReadonlyMap<string, ReviewedResult>,
	witnesses: ReturnType<typeof priorAdviceWitnesses>,
	state: PublicReviewState,
	restore: () => string,
) {
	// Every witness a decision can name; readReview checks which reason each one may support.
	const eligible = [...witnesses].filter(([, witness]) => witness.eligibleForAlreadySaid);
	return defineTool({
		name: "report_review",
		prepareArguments: prepareReviewArguments,
		exposure: "model-only",
		label: "Report Review",
		description: REVIEW_TOOL_DESCRIPTION,
		parameters: reviewToolParameters(
			restable,
			lineNotes,
			eligible.map(([id]) => id),
		),
		execute: async (toolCallId, params): Promise<AgentToolResult<ReportReviewDetails>> => {
			if (!compositionAdmitted) {
				return refusal<ReportReviewDetails>(
					toolCallId,
					"Feedback composition opens only after Java admits the completed results.",
				);
			}
			if (state.final) {
				return refusal<ReportReviewDetails>(toolCallId, REVIEW_FINAL);
			}
			if (state.reviewContext !== "HELD") {
				const restored = state.reviewContext === "LOST" ? `\n${restore()}\n` : "";
				state.reviewContext = "RESTORING";
				return refusal<ReportReviewDetails>(
					toolCallId,
					`review refused, nothing was stored: the session's context was compacted. Use the restored review reference in the next model turn before storing the review.${restored}\nSend the whole review again.`,
				);
			}
			const read = readReview(params, restable, lineNotes, { witnesses });
			if ("errors" in read) {
				return refusal<ReportReviewDetails>(
					toolCallId,
					`review refused, nothing was stored:\n${read.errors.map((error) => `- ${error}`).join("\n")}`,
				);
			}
			// Final only once persisted: a failed write leaves the review unaccepted, so it is never delivered.
			const previous = composedFeedback.review;
			composedFeedback.review = read.review;
			try {
				persistComposedFeedback();
			} catch (error) {
				composedFeedback.review = previous;
				throw error;
			}
			state.final = true;
			if (currentTurn) {
				currentTurn.stored += 1;
				sessionGuards.stored += 1;
			}
			const { summary, inline, withheld } = read.review;
			const said = summary
				? `a summary resting on ${summary.basedOn.length} result(s) and observation(s)`
				: "no summary";
			return {
				content: [
					{
						type: "text",
						text: `Stored the review: ${said}, ${inline.length} line note(s), ${withheld.length} withholding decision(s). It is final.`,
					},
				],
				details: { stored: 1 },
				terminate: true,
			};
		},
	});
}

/** The NOT_MET results the review may rest on that it neither says nor withholds. */
function undecidedByReview(reviewable: readonly Record<string, unknown>[]): string[] {
	const decided = decidedByReview(composedFeedback.review);
	return reviewable
		.filter((result) => result.outcome === "NOT_MET")
		.map((result) => String(result.practiceResultId))
		.filter((id) => !decided.has(id));
}

function finishReviewText(undecided: readonly string[]): string {
	const owed =
		undecided.length > 0
			? `## Undecided\nThe review leaves these NOT_MET results undecided: ${undecided.join(", ")}.`
			: "## Unfinished\nThe review on this work is not final yet.";
	return `${owed}\nStore the final review with one report_review call. A review that says nothing is still one final report_review call. No prose outside the calls.`;
}

/** Explanatory context on each practice the review's results were measured against, from the staged index. */
function practiceContext(results: readonly Record<string, unknown>[]): ReviewPractice[] {
	const slugs = new Set(results.map((result) => String(result.practiceSlug)));
	return practiceIndex
		.filter((practice) => slugs.has(practice.slug))
		.map((practice) => ({
			slug: practice.slug,
			name: practice.name,
			...(practice.whyItMatters === undefined ? {} : { whyItMatters: practice.whyItMatters }),
			knownLimitations: practice.knownLimitations,
			...(practice.revisionId === undefined ? {} : { revisionId: practice.revisionId }),
		}));
}

/** Match ComposedFeedbackUnit bounds so the model can correct a unit before server admission. */
const FEEDBACK_TEXT_BOUNDS = {
	title: 255,
	body: 8000,
	nextStep: 2000,
	supersedesThreadKey: 64,
	situation: 4000,
	capability: 2000,
	evidenceSummary: 4000,
	inConversationSignal: 2000,
	alreadySaid: 2000,
} as const;

const UNIT_FIELDS = [
	"channel",
	"practiceSlug",
	"basedOn",
	"action",
	"supersedesThreadKey",
	"withholdReason",
	"title",
	"body",
	"nextStep",
	"notes",
] as const;

const NOTE_FIELDS = [
	"situation",
	"capability",
	"evidenceSummary",
	"inConversationSignal",
	"alreadySaid",
] as const;

/** Accept case and hyphen variants; reject values outside the declared vocabulary. */
function vocabularyWord<T extends string>(
	value: unknown,
	vocabulary: readonly T[],
	field: string,
): T | string {
	if (typeof value !== "string" || value.trim() === "") {
		return `${field} is required: one of ${vocabulary.join(", ")}`;
	}
	const word = value.trim().toUpperCase().replaceAll("-", "_");
	const match = vocabulary.find((candidate) => candidate === word);
	return match ?? `${field} must be one of ${vocabulary.join(", ")} (received '${value}')`;
}

/** Enforce text length here; validateUnit handles lane-specific required fields. */
function boundedText(
	value: unknown,
	field: keyof typeof FEEDBACK_TEXT_BOUNDS,
): { text: string | undefined } | { error: string } {
	if (value === undefined || value === null) {
		return { text: undefined };
	}
	if (typeof value !== "string") {
		return { error: `${field} must be a string` };
	}
	const text = value.trim();
	if (text === "") {
		return { text: undefined };
	}
	const bound = FEEDBACK_TEXT_BOUNDS[field];
	if (text.length > bound) {
		return {
			error: `${field} must be at most ${bound} characters; this one is ${text.length}. Shorten it and send the unit again`,
		};
	}
	return { text };
}

/** Validate one feedback item; the outer tool schema intentionally permits partial batch success. */
/**
 * A WITHHOLD sent with no basedOn rests on its practice's NOT_MET result, the only one it can withhold: it is filled
 * in, and said so.
 */
function withholdBasis(
	value: unknown,
	results: readonly AdmittedResult[],
	repairs: string[],
): unknown {
	if (
		!isRecord(value) ||
		String(value.action).toUpperCase() !== "WITHHOLD" ||
		listOrSingle(value.basedOn).length > 0
	) {
		return value;
	}
	const slug = normalizePracticeSlug(value.practiceSlug);
	const ids = results
		.filter((result) => result.practiceSlug === slug && result.outcome === "NOT_MET")
		.map((result) => result.practiceResultId);
	if (ids.length === 0) {
		return value;
	}
	repairs.push(`basedOn filled in with ${slug}'s NOT_MET result ${ids.join(", ")}`);
	return { ...value, basedOn: ids };
}

function readFeedbackUnit(
	value: unknown,
	practiceSlugs: readonly string[],
): ReadFeedbackUnit | string {
	if (!isRecord(value)) {
		return `each item of units is one unit object with channel, practiceSlug, basedOn and action (received ${typeof value}); skipped.`;
	}
	const unknownFields = Object.keys(value).filter(
		(key) => !UNIT_FIELDS.some((field) => field === key),
	);
	if (unknownFields.length > 0) {
		return `unknown unit field(s): ${unknownFields.join(", ")} — a unit takes ${UNIT_FIELDS.join(", ")}; skipped.`;
	}
	const channel = vocabularyWord(value.channel, CHANNELS, "channel");
	if (!isChannel(channel)) {
		return `${channel}; skipped.`;
	}
	if (channel === "IN_CONTEXT") {
		return "the review on the work is written in its own turn, not as a unit here; skipped.";
	}
	const action = vocabularyWord(value.action, ACTIONS, "action");
	if (!isFeedbackAction(action)) {
		return `${action}; skipped.`;
	}
	const practiceSlug =
		typeof value.practiceSlug === "string"
			? value.practiceSlug.trim().toLowerCase().replaceAll("_", "-")
			: "";
	if (!practiceSlug) {
		return "practiceSlug is required; skipped.";
	}
	if (!practiceSlugs.includes(practiceSlug)) {
		return `practiceSlug '${practiceSlug}' is not a practice with an admitted result in this run (those are: ${practiceSlugs.join(", ")}); skipped.`;
	}
	// One id sent bare is the list of one; the reader drops a unit that cites nothing, so admitting an
	// empty list here would tell the session it succeeded and then deliver nothing.
	const basedOn = listOrSingle(value.basedOn).filter(
		(reference): reference is string => typeof reference === "string" && reference.trim() !== "",
	);
	if (basedOn.length === 0) {
		return "basedOn is required: the admitted result or observation id(s) this unit rests on; skipped.";
	}
	let withholdReason: string | undefined;
	if (value.withholdReason !== undefined && value.withholdReason !== null) {
		const word = vocabularyWord(value.withholdReason, WITHHOLD_REASONS, "withholdReason");
		if (!WITHHOLD_REASONS.some((reason) => reason === word)) {
			return `${word}; skipped.`;
		}
		withholdReason = word;
	}
	const texts: Partial<Record<keyof typeof FEEDBACK_TEXT_BOUNDS, string>> = {};
	for (const field of ["supersedesThreadKey", "title", "body", "nextStep"] as const) {
		const read = boundedText(value[field], field);
		if ("error" in read) {
			return `${read.error}; skipped.`;
		}
		texts[field] = read.text;
	}
	let notes: ConversationNotes | undefined;
	if (value.notes !== undefined && value.notes !== null) {
		if (!isRecord(value.notes)) {
			return "notes must be an object; skipped.";
		}
		const unknownNotes = Object.keys(value.notes).filter(
			(key) => !NOTE_FIELDS.some((field) => field === key),
		);
		if (unknownNotes.length > 0) {
			return `unknown notes field(s): ${unknownNotes.join(", ")} — notes take ${NOTE_FIELDS.join(", ")}; skipped.`;
		}
		notes = {};
		for (const field of NOTE_FIELDS) {
			const read = boundedText(value.notes[field], field);
			if ("error" in read) {
				return `notes.${read.error}; skipped.`;
			}
			notes[field] = read.text;
		}
	}
	return {
		channel,
		practiceSlug,
		action,
		basedOn: basedOn.map((reference) => reference.trim()),
		supersedesThreadKey: texts.supersedesThreadKey,
		withholdReason,
		title: texts.title,
		body: texts.body,
		nextStep: texts.nextStep,
		notes,
	};
}

/** A list as sent, or the one value sent bare where a list was asked for. */
function listOrSingle(value: unknown): unknown[] {
	if (Array.isArray(value)) {
		return value;
	}
	return value === undefined || value === null ? [] : [value];
}

const THIS_WORK = ((): string | undefined => {
	const metadataPath = nodePath.join(INPUT_PATHS.contextRoot, "metadata.json");
	if (!existsSync(metadataPath) || !isRecord(folderIndex)) {
		return undefined;
	}
	const metadata = parseJson(readFileSync(metadataPath, "utf8"));
	if (!isRecord(metadata)) {
		return undefined;
	}
	const kind = folderIndex.artifactKind;
	if (kind !== "scm.pull_request" && kind !== "scm.issue") {
		return undefined;
	}
	return workIdentity(kind, metadata[kind === "scm.pull_request" ? "pr_url" : "html_url"]);
})();

// Enforce snapshot-dependent constraints here for fast model correction; Java rechecks them.
/**
 * Count the distinct pieces of work the server's IN_APP support read selected for the practice. Two occurrences on
 * one piece of work are one piece, as the server counts it (InAppFeedbackRouter.distinctArtifacts).
 */
function negativePiecesOfWork(practiceSlug: string): number {
	if (inAppSupport?.state !== "COMPLETE") {
		return 0;
	}
	const support = inAppSupport.practices.find((practice) => practice.practiceSlug === practiceSlug);
	return new Set(
		(support?.occurrences ?? []).map(
			(occurrence) => `${occurrence.artifactKind}:${occurrence.artifactId}`,
		),
	).size;
}

/** The IN_CHAT lane: notes to the mentor, and nothing that would be read out. */
function validateChatUnit(unit: ReadFeedbackUnit): string | null {
	if (hasText(unit.body) || hasText(unit.nextStep)) {
		return "IN_CHAT takes notes{situation,capability,evidenceSummary,inConversationSignal}, not body/nextStep - nothing on this lane is read out; skipped.";
	}
	const { notes } = unit;
	for (const field of [
		"situation",
		"capability",
		"evidenceSummary",
		"inConversationSignal",
	] as const) {
		if (isBlank(notes?.[field])) {
			return `IN_CHAT needs notes.${field}; skipped.`;
		}
	}
	return null;
}

/**
 * The IN_APP lane: a body about a pattern across work, never a quote of this work. Every quote of a result the unit
 * rests on counts, its own and its observations', whichever of its ids the unit names.
 */
function validateAppUnit(
	unit: ReadFeedbackUnit,
	named: ReadonlyMap<string, { result: AdmittedResult }>,
): string | null {
	const { body } = unit;
	if (!hasText(body?.trim())) {
		return "IN_APP needs a body; skipped.";
	}
	const normalizedBody = normalizeQuotedText(body);
	const repeatsCurrentEvidence = unit.basedOn.some((id) => {
		const result = named.get(id)?.result;
		return [
			...(result?.citations ?? []),
			...(result?.observations ?? []).flatMap((observation) => observation.citations),
		].some((citation) => {
			const quote = normalizeQuotedText(optionalString(citation.quote) ?? "");
			return quote.length >= 12 && normalizedBody.includes(quote);
		});
	});
	if (repeatsCurrentEvidence) {
		return "IN_APP describes a cross-artifact pattern; do not copy a current artifact quote into it. Skipped.";
	}
	return null;
}

function validateUnit(
	unit: ReadFeedbackUnit,
	results: ReadonlyMap<string, AdmittedResult>,
	preparedTargets: readonly PreparedFeedbackTarget[],
	deliveredPractices: ReadonlySet<string>,
): string | null {
	const evidenceError = validateFeedbackEvidence(unit.practiceSlug, unit.basedOn, results);
	if (evidenceError !== null) {
		return evidenceError;
	}
	const named = namedEvidence(results);
	if (unit.action === "WITHHOLD") {
		const reason = unit.withholdReason;
		if (!hasText(reason)) {
			return "WITHHOLD needs a withholdReason; skipped.";
		}
		// The withholding settles each NOT_MET practice in basedOn (undecidedPrivatePractices), so each needs a record here.
		const unrecorded = notMetPractices(
			unit.basedOn.flatMap((id) => named.get(id)?.result ?? []),
		).filter((slug) => !deliveredPractices.has(`${unit.channel}:${slug}`));
		if ([...PRIOR_ADVICE_REASONS].some((prior) => prior === reason) && unrecorded.length > 0) {
			return (
				`${reason} rests on feedback recorded as delivered on ${unit.channel} for ${unrecorded.join(", ")} in the ` +
				`delivered-feedback history: current, not withdrawn, with its words, for each NOT_MET practice in basedOn. The captured history does not establish that prerequisite; prepared ` +
				`feedback and other channels do not count. Decide this lane on its own evidence: the unit it supports, ` +
				`or WITHHOLD with BELOW_BAR. Skipped.`
			);
		}
		return null;
	}
	if (isBlank(unit.title)) {
		return "A unit that is not a WITHHOLD needs a title; skipped.";
	}
	if (unit.action === "SUPERSEDE") {
		if (!hasText(unit.supersedesThreadKey)) {
			return "SUPERSEDE needs a supersedesThreadKey; skipped.";
		}
		if (
			!preparedTargets.some(
				(target) =>
					target.threadKey === unit.supersedesThreadKey &&
					target.channel === unit.channel &&
					target.practiceSlug === unit.practiceSlug,
			)
		) {
			return `No queued message has threadKey '${unit.supersedesThreadKey}'; it must come from the task-declared prepared-feedback file. Skipped.`;
		}
	}
	if (unit.channel === "IN_CHAT") {
		return validateChatUnit(unit);
	}
	if (unit.notes) {
		return "Only IN_CHAT units may carry a notes block; skipped.";
	}
	if (isBlank(unit.nextStep)) {
		return `${unit.channel} needs a nextStep; skipped.`;
	}
	return validateAppUnit(unit, named);
}

function normalizeQuotedText(value: string): string {
	return value
		.normalize("NFKC")
		.replaceAll(/[“”„‟]/gu, '"')
		.replaceAll(/[‘’‚‛]/gu, "'")
		.replaceAll(/\s+/gu, " ")
		.trim()
		.toLowerCase();
}

/** A citation by its coordinates: its quote and verification digests stay in the full admission record on disk. */
function coordinatesOf(citation: unknown): unknown {
	if (!isRecord(citation)) {
		return citation;
	}
	const { quote: _quote, verification: _verification, ...coordinates } = citation;
	return coordinates;
}

/**
 * One admitted result as the private composer sees it, with its observations nested: every citation by its
 * coordinates only. An abstention keeps the limit it recorded and its id; its summary and rationale may assert more
 * than it decided.
 */
function composerView(result: AdmittedResult): Record<string, unknown> {
	const citations = result.citations.map(coordinatesOf);
	if (result.outcome !== "NOT_MET" && result.outcome !== "MET") {
		const { practiceResultId, practiceSlug, outcome } = result;
		return {
			practiceResultId,
			practiceSlug,
			outcome,
			evidence: qualificationEvidence(result),
			citations,
		};
	}
	// MET counterevidence keeps the same qualifications as NOT_MET.
	const { citations: _admitted, example, observations, ...rest } = result;
	return {
		...rest,
		...(isRecord(example)
			? { example: { ...example, citation: coordinatesOf(example.citation) } }
			: {}),
		citations,
		observations: observations.map((observation) => ({
			...observation,
			citations: observation.citations.map(coordinatesOf),
		})),
	};
}

const COMPOSITION_BLOCK_LIMIT = 32_000;

/** A staged file as a titled block of the composition turn, or a pointer to it when it is too large. */
function stagedBlock(label: string, file: string, limit = COMPOSITION_BLOCK_LIMIT): string {
	if (!existsSync(file)) {
		return "";
	}
	const text = readFileSync(file, "utf8").trim();
	return text.length > limit
		? `### ${label} — too large to show here; read \`${file}\`\n`
		: `### ${label}\n\`\`\`json\n${text}\n\`\`\`\n`;
}

/**
 * The complete staged criteria of each practice with a NOT_MET result: feedback addresses the standard that was
 * assessed. The measuring leads and source directives belong to assessment and stay out. Each is shown whole, named
 * with where to read it when too large, or named as not staged or not readable; never cut.
 */
function notMetCriteria(
	results: readonly AdmittedResult[],
	limit = COMPOSITION_BLOCK_LIMIT,
): string {
	return notMetPractices(results)
		.map((slug) => {
			const file = criteriaPathOf(slug);
			if (!existsSync(file)) {
				return `### Criteria of \`${slug}\` — not staged for this review; nothing is known about them here\n`;
			}
			let text: string;
			try {
				text = readFileSync(file, "utf8").trim();
			} catch {
				return `### Criteria of \`${slug}\` — \`${file}\` could not be read; nothing is known about them here\n`;
			}
			if (text.length === 0) {
				return `### Criteria of \`${slug}\` — \`${file}\` is empty; no standard is available here\n`;
			}
			if (text.length > limit) {
				return `### Criteria of \`${slug}\` — too large to show here; read \`${file}\`\n`;
			}
			const longestRun = Math.max(
				0,
				...[...text.matchAll(/`+/gu)].map((backticks) => backticks[0].length),
			);
			const fence = "`".repeat(Math.max(3, longestRun + 1));
			return `### Criteria of \`${slug}\`\n${fence}markdown\n${text}\n${fence}\n`;
		})
		.join("\n");
}

/** The producer owns body eligibility. Keep its facts here and locate only bodies it actually staged. */
function priorFeedbackFacts(label: string, file: string, key: "feedback" | "prepared"): string {
	if (!existsSync(file)) {
		return "";
	}
	const pointer = `### ${label} — read \`${file}\` for the recorded history; its contents are not shown here\n`;
	let history: unknown;
	try {
		history = parseJson(readFileSync(file, "utf8"));
	} catch {
		return pointer;
	}
	if (!isRecord(history) || !Array.isArray(history[key])) {
		return pointer;
	}
	const entries = jsonArray(history[key]);
	if (
		entries.some(
			(entry) => !isRecord(entry) || (entry.body != null && typeof entry.body !== "string"),
		)
	) {
		return pointer;
	}
	const projected = entries.map((entry, index) => {
		if (!isRecord(entry)) {
			return entry;
		}
		const { body, ...facts } = entry;
		return {
			...facts,
			...(typeof body === "string"
				? { bodyAt: `${file} → ${key}[${index}].body (${body.length} characters)` }
				: {}),
		};
	});
	const text = JSON.stringify({ ...history, [key]: projected }, null, 1);
	return text.length > COMPOSITION_BLOCK_LIMIT
		? pointer
		: `### ${label} — bodies remain at their \`bodyAt\` in the unchanged file; read them when earlier wording matters\n\`\`\`json\n${text}\n\`\`\`\n`;
}

/** The composed public decision is not a delivery receipt or prior advice for the private writer. */
function plannedReviewFacts(): string {
	const { review } = composedFeedback;
	if (!review) {
		return "";
	}
	const stored = existsSync(FEEDBACK_PATH);
	const bodyAt = (field: string) =>
		stored ? { bodyAt: `${FEEDBACK_PATH} → review.${field}` } : {};
	const facts = {
		summary: review.summary ? { basedOn: review.summary.basedOn, ...bodyAt("summary.body") } : null,
		inline: review.inline.map(({ body: _body, ...note }, index) => ({
			...note,
			...bodyAt(`inline[${index}].body`),
		})),
		withheld: review.withheld,
	};
	return `### The review composed for this work\nThis decision is not a receipt of provider delivery. It may have entered public handling, whose final gates can still withhold it. Only delivered-history records establish prior communication. Private channels remain independently useful and may make the same supported point. ${stored ? "Read the located bodies if their wording matters." : "Its wording is not available as a staged file here."}\n\`\`\`json\n${JSON.stringify(facts, null, 1)}\n\`\`\`\n`;
}

function buildCompositionTurn(
	request: CompositionRequest,
	results: readonly AdmittedResult[],
	notReached: readonly string[] = [],
): string {
	const lanes = PRIVATE_CHANNELS.filter((channel) => request.channels[channel].enabled)
		.map((channel) => `${channel} (at most ${request.channels[channel].maxUnits})`)
		.join(", ");
	const closed = PRIVATE_CHANNELS.filter((channel) => !request.channels[channel].enabled);
	const closedNote =
		closed.length > 0 ? ` Closed this turn, so write nothing for them: ${closed.join(", ")}.` : "";
	const coverageNote = notReachedNote(notReached);
	const routing = PRIVATE_CHANNELS.filter((channel) => request.channels[channel].enabled)
		.map(
			(channel) =>
				`${channel}: ${privatelyComposable()
					.filter((slug) => routedLanes?.get(slug)?.includes(channel) === true)
					.join(", ")}`,
		)
		.join("; ");
	const admitted = JSON.stringify({ results: results.map(composerView) }, null, 1);
	const onTheWork = plannedReviewFacts();
	const historyRoot = nodePath.dirname(PREPARED_FEEDBACK_PATH);
	const context = [
		stagedBlock("The composition request (lanes, caps, placements)", COMPOSITION_REQUEST_PATH),
		stagedBlock(
			"What earlier reviews recorded about this person",
			`${historyRoot}/observations.json`,
		),
		request.channels.IN_APP.enabled
			? stagedBlock(
					"IN_APP support: occurrences a new card may cite at this read",
					IN_APP_SUPPORT_PATH,
				)
			: "",
		priorFeedbackFacts("Recorded delivered feedback", `${historyRoot}/feedback.json`, "feedback"),
		priorFeedbackFacts(
			"Prepared feedback (not delivered; supersession targets)",
			PREPARED_FEEDBACK_PATH,
			"prepared",
		),
	]
		.filter((block) => block !== "")
		.join("\n");
	return `## This turn
The review just finished. The criteria its NOT_MET practices were assessed against come first, as staged; its ${results.length} admitted result(s) follow them, one per practice. The MET and NOT_MET ones carry their rationale and their citations by coordinates; each NOT_MET one also carries its observations, the located problems it found, each with its own citations. The abstentions carry the limit they recorded, an open question or why the practice did not apply, and their citations by coordinates. The full record, quoted lines included, is \`work/composition/results.json\`. The history follows them.

${notMetCriteria(results)}
\`\`\`json
${admitted}
\`\`\`

${context}
${onTheWork}
Lanes open this turn: ${lanes}.${closedNote}
The practices each open lane may carry: ${routing}. Write no unit, not even a WITHHOLD, for a practice on a lane that does not list it.
A pattern claim needs at least ${request.minDistinctArtifacts} distinct pieces of work.

${coverageNote}Persist the units with report_feedback — every unit you have in one call. Writing nothing on a lane is a correct and common outcome; say in one line why, and stop.`;
}

/** A transport failure or a server that is not answering yet; a refusal is a decision, not a blip. */
class AdmissionUnreachableError extends Error {
	name = "AdmissionUnreachableError";
}

/** The cause behind a wrapped error, in parentheses, or nothing when the error carries none. */
function causeText(error: unknown): string {
	return error instanceof Error && error.cause !== undefined ? ` (${errorText(error.cause)})` : "";
}

/** Retry transport failures, not slow admission or explicit refusals. */
const ADMISSION_ATTEMPTS = 4;

// Admission verifies every cited blob against the Git object, in a container the server starts, so
// one attempt may take minutes; the cap only bounds a server that went silent without closing.
const ADMISSION_ATTEMPT_TIMEOUT_MS = 10 * 60_000;

/** Preserve an HTTP refusal even when its body is unreadable. */
async function answerText(response: Response): Promise<string> {
	try {
		const body = await response.text();
		const problem: unknown = body.trim().startsWith("{") ? parseJson(body) : null;
		const detail = isRecord(problem) ? problem.detail : null;
		return typeof detail === "string" && detail.length > 0 ? detail : body.slice(0, 500);
	} catch {
		return "the server gave no readable reason";
	}
}

/** One admission attempt: the answer it returns is the parsed body, unvalidated. */
async function postAdmission(): Promise<unknown> {
	let response: Response;
	try {
		response = await fetch(`${LLM_PROXY_URL}/admit-results`, {
			method: "POST",
			signal: AbortSignal.timeout(ADMISSION_ATTEMPT_TIMEOUT_MS),
			headers: {
				authorization: `Bearer ${LLM_PROXY_TOKEN}`,
				"content-type": "application/json",
				...(hasText(process.env.TRACEPARENT) ? { traceparent: process.env.TRACEPARENT } : {}),
			},
			body: JSON.stringify(admissionRequest()),
		});
	} catch (error) {
		if (isTimeoutAbort(error)) {
			// The server is still verifying; a second attempt would only queue the same work behind it.
			throw new Error(`result admission did not answer within ${ADMISSION_ATTEMPT_TIMEOUT_MS}ms`, {
				cause: error,
			});
		}
		// Node reports every transport failure as `TypeError: fetch failed`; only the cause says which
		// one it was, and a report that has just the message cannot tell a restart from a wrong URL.
		throw new AdmissionUnreachableError(
			`result admission could not be sent: ${errorText(error)}${causeText(error)}`,
		);
	}
	if (isRetryableStatus(response.status)) {
		throw new AdmissionUnreachableError(`result admission failed: HTTP ${response.status}`);
	}
	if (!response.ok) {
		// The server has decided, and it said why. Asking again puts the same question, so the run ends
		// here — with the reason in the transcript, which is the only place a reader can find it.
		throw new Error(
			`result admission was refused: HTTP ${response.status} — ${await answerText(response)}`,
		);
	}
	try {
		return await response.json();
	} catch (error) {
		// A server that dies while writing its answer ends the body mid-stream. The admission may well
		// have been recorded; asking again replays it rather than admitting it twice.
		throw new AdmissionUnreachableError(
			`result admission answer was cut short: ${errorText(error)}${causeText(error)}`,
		);
	}
}

async function admitResults() {
	// Admission retries are idempotent: measurement is frozen and the server replays identical payloads.
	const admitted: unknown = await retrying(
		postAdmission,
		(error) => error instanceof AdmissionUnreachableError,
		{ attempts: ADMISSION_ATTEMPTS },
		(attempt, error, delayMs) =>
			console.error(
				`[pi-runner] admission attempt ${attempt}/${ADMISSION_ATTEMPTS} did not arrive (${errorText(error)}); retrying in ${delayMs}ms`,
			),
	);
	const answer = acceptAdmission(admitted);
	// The support read is checked only after the admission it accompanies; it never changes what was admitted.
	inAppSupport = readInAppSupport(answer.inAppSupport, admittedResults);
	if (inAppSupport?.state === "COMPLETE") {
		writeFileSync(IN_APP_SUPPORT_PATH, JSON.stringify(inAppSupport, null, 2));
	}
	inChatEligibility = readInChatEligibility(answer.inChatEligibility, admittedResults);
}

/** Hand the complete public decision to its server owner without settling the review or its usage. */
async function completeReviewOnWork(safety: ReturnType<typeof scheduleDeadline>): Promise<void> {
	if (composedFeedback.review === null || admissionDigest === null || safety.expired()) {
		return;
	}
	const result = await completePublicReview({
		url: `${LLM_PROXY_URL}/complete-public-review`,
		token: LLM_PROXY_TOKEN,
		admissionDigest,
		review: composedFeedback.review,
		timeoutMs: Math.min(
			ADMISSION_ATTEMPT_TIMEOUT_MS,
			AGENT_BUDGET_MS - (Date.now() - PROCESS_START_MS),
		),
	});
	if (result.state === "REFUSED") {
		// The server made a final decision: returning this package at terminal must not bypass it.
		composedFeedback.review = null;
		recordCompositionFailure("PUBLIC_REVIEW", "SERVER_REFUSED");
		persistComposedFeedback();
	}
	// An unavailable acknowledgement may follow a committed acceptance. The server reconciles the
	// unchanged terminal package against that immutable completion; it never authorizes another writer.
	console.error(`[pi-runner] public completion: ${result.state}`);
}

/**
 * Holds an admission answer as the composition's ground, whether this attempt's own admission returned it or the
 * server staged an earlier attempt's for a continuing writer: both answer the one contract.
 */
function acceptAdmission(admitted: unknown): Record<string, unknown> {
	if (
		!isRecord(admitted) ||
		admitted.schemaVersion !== 2 ||
		typeof admitted.admissionDigest !== "string" ||
		!Array.isArray(admitted.results) ||
		!admitted.results.every(isAdmittedResult)
	) {
		throw new Error("result admission returned an invalid contract");
	}
	for (const withheld of jsonArray(admitted.withheld)) {
		if (isRecord(withheld)) {
			console.error(
				`[pi-runner] admission withheld ${logValue(withheld.practiceSlug)}: ${logValue(withheld.reasonCode)}`,
			);
		}
	}
	admittedResults.splice(0, admittedResults.length, ...admitted.results);
	admissionDigest = admitted.admissionDigest;
	compositionAdmitted = true;
	composedFeedback.admissionDigest = admissionDigest;
	composedFeedback.results = leanResults(admittedResults);
	mkdirSync(`${CWD}/work/composition`, { recursive: true });
	writeFileSync(COMPOSITION_RESULTS_PATH, JSON.stringify({ results: admittedResults }, null, 2));
	return admitted;
}

/**
 * A private lane composes only what the server's read after admission routes to it. Without a complete read the lane
 * closes for this run: a refusal is the policy's answer, anything else is feedback that could not be composed. A lane
 * no NOT_MET practice is routed to closes as decided. Other lanes go on, and preparation still routes what is written.
 */
function closeUnroutedPrivateLanes(request: CompositionRequest): void {
	const notMet = notMetPractices(admittedResults);
	for (const channel of PRIVATE_CHANNELS) {
		if (!request.channels[channel].enabled || notMet.length === 0) {
			continue;
		}
		const read = channel === "IN_APP" ? inAppSupport : inChatEligibility;
		if (read?.state !== "COMPLETE") {
			request.channels[channel].enabled = false;
			console.error(
				`[pi-runner] ${channel} closed for this run: ${channel === "IN_APP" ? "support context" : "routing"} ${read === null ? "missing or malformed" : read.state}`,
			);
			if (read?.state !== "REFUSED") {
				recordCompositionFailure("PRIVATE_FEEDBACK", "RUNTIME_ERROR");
			}
		} else if (!notMet.some((slug) => routedTo(channel, slug))) {
			request.channels[channel].enabled = false;
			console.error(`[pi-runner] ${channel} closed for this run: no practice is routed to it`);
		}
	}
	routedLanes = new Map(
		notMet.map((slug) => [
			slug,
			PRIVATE_CHANNELS.filter(
				(channel) => request.channels[channel].enabled && routedTo(channel, slug),
			),
		]),
	);
}

/** The NOT_MET practices private composition owes a decision on: those routed to an open lane, once routed. */
function privatelyComposable(): string[] {
	const notMet = notMetPractices(admittedResults);
	const lanes = routedLanes;
	return lanes === null ? notMet : notMet.filter((slug) => (lanes.get(slug) ?? []).length > 0);
}

/** One read of the delivered public history; any refusal or bad answer is final, never an older or empty history. */
async function postPublicHistoryRead(): Promise<unknown> {
	let response: Response;
	try {
		response = await fetch(`${LLM_PROXY_URL}/public-feedback-history`, {
			method: "POST",
			signal: AbortSignal.timeout(
				Math.max(1, Math.min(60_000, AGENT_BUDGET_MS - (Date.now() - PROCESS_START_MS))),
			),
			headers: {
				authorization: `Bearer ${LLM_PROXY_TOKEN}`,
				...(hasText(process.env.TRACEPARENT) ? { traceparent: process.env.TRACEPARENT } : {}),
			},
		});
	} catch (error) {
		throw new AdmissionUnreachableError(
			`public feedback history could not be read: ${errorText(error)}${causeText(error)}`,
		);
	}
	if (isRetryableStatus(response.status)) {
		throw new AdmissionUnreachableError(
			`public feedback history read failed: HTTP ${response.status}`,
		);
	}
	if (!response.ok) {
		throw new Error(
			`public feedback history read was refused: HTTP ${response.status} — ${await answerText(response)}`,
		);
	}
	let body: string;
	try {
		body = await response.text();
	} catch (error) {
		throw new AdmissionUnreachableError(
			`public feedback history body could not be read: ${errorText(error)}${causeText(error)}`,
		);
	}
	return JSON.parse(body);
}

/**
 * The delivered feedback on this work as it stands now, which may include feedback delivered after the work was
 * captured. Kept beside the staged history rather than over it: the staged file is what the review was prepared from.
 */
async function readPublicFeedbackHistory(): Promise<{
	readAt: string;
	history: { feedback: unknown[] };
}> {
	const answer: unknown = await retrying(
		postPublicHistoryRead,
		(error) => error instanceof AdmissionUnreachableError,
		{ attempts: ADMISSION_ATTEMPTS },
		(attempt, error, delayMs) =>
			console.error(
				`[pi-runner] public history read ${attempt}/${ADMISSION_ATTEMPTS} did not arrive (${errorText(error)}); retrying in ${delayMs}ms`,
			),
	);
	const history = isRecord(answer) && isRecord(answer.history) ? answer.history : null;
	if (
		!isRecord(answer) ||
		answer.schemaVersion !== 1 ||
		typeof answer.readAt !== "string" ||
		!Number.isFinite(Date.parse(answer.readAt)) ||
		history === null ||
		!Array.isArray(history.feedback) ||
		!history.feedback.every(
			(entry: unknown) =>
				isRecord(entry) &&
				typeof entry.id === "string" &&
				entry.channel === "IN_CONTEXT" &&
				entry.publicEligible === true &&
				(entry.deliveredAt === null ||
					(typeof entry.deliveredAt === "string" &&
						Number.isFinite(Date.parse(entry.deliveredAt)))) &&
				isRecord(entry.artifact) &&
				typeof entry.artifact.kind === "string" &&
				(entry.artifact.url === undefined ||
					entry.artifact.url === null ||
					typeof entry.artifact.url === "string") &&
				(entry.recordedClaimCurrentness === "CURRENT" ||
					entry.recordedClaimCurrentness === "STALE") &&
				(entry.body === undefined || entry.body === null || typeof entry.body === "string") &&
				(entry.withdrawn === undefined || typeof entry.withdrawn === "boolean"),
		)
	) {
		throw new Error("public feedback history read returned an invalid contract");
	}
	const read = { readAt: answer.readAt, history: { ...history, feedback: history.feedback } };
	writeFileSync(PUBLIC_FEEDBACK_HISTORY_PATH, JSON.stringify(read, null, 2));
	return read;
}

function noteToolCall(turn: TurnTrace, toolName: string, args: unknown, measuring: boolean): void {
	turn.toolCalls[toolName] = (turn.toolCalls[toolName] ?? 0) + 1;
	if (!activeSession) {
		return;
	}
	const calls = Object.values(turn.toolCalls).reduce((sum, count) => sum + count, 0);
	if (
		!measuring &&
		!RECORDING_TOOLS.has(toolName) &&
		sessionGuards.recordingCalls === 0 &&
		calls === COMPOSITION_EXPLORATION_NUDGE
	) {
		console.error(
			`[pi-runner] composition: ${COMPOSITION_EXPLORATION_NUDGE} calls without a recording call — nudging to persist`,
		);
		void steer(activeSession, composerTool === "report_review" ? REVIEW_NUDGE : COMPOSITION_NUDGE);
	}
	const signature = `${toolName}:${JSON.stringify(args)}`;
	const repeats = (repeatedCalls.get(signature) ?? 0) + 1;
	repeatedCalls.set(signature, repeats);
	turn.repeatedCalls = Math.max(turn.repeatedCalls, repeats);
	const recording = RECORDING_TOOLS.has(toolName);
	if (repeats === (recording ? REPEATED_RECORDING_NUDGE : REPEATED_CALL_NUDGE)) {
		console.error(
			`[pi-runner] ${turn.label}: the same ${toolName} call ${repeats} times — nudging to record`,
		);
		// Naming what is still owed matters most when the repeated call is the recording itself: "record"
		// alone reads as "send that again".
		const owed = measuring ? owedPractices() : [];
		const next =
			owed.length > 0
				? `Still owed: ${owed.join(", ")}. Submit what the evidence you have read supports for these`
				: "Submit what the evidence you have read supports";
		// The review's next tool depends on what it still lacks, which REVIEW_NUDGE already says.
		let correction = `Correct what its answer names and send one ${recording ? toolName : composerTool} call.`;
		if (composerTool === "report_review") {
			correction = `Correct what its answer names. ${REVIEW_NUDGE}`;
		}
		void steer(
			activeSession,
			measuring
				? `You have run the same ${toolName} call ${repeats} times; its result will not change. ${next}, in one submit_result call. ${PERSIST_DISCIPLINE}`
				: `You have run the same ${toolName} call ${repeats} times; its result will not change. ${correction}`,
		);
	}
	if (repeats >= (recording ? REPEATED_RECORDING_ABORT : REPEATED_CALL_ABORT)) {
		stopTurn("loop", `the same ${toolName} call ${repeats} times — aborting this turn`);
	}
	// SDK schema refusals bypass tool execution and its per-practice cap; count attempts here too.
	if (RECORDING_TOOLS.has(toolName)) {
		turn.recordingCalls += 1;
		sessionGuards.recordingCalls += 1;
		if (
			sessionGuards.recordingCalls >= MAX_RECORDING_ATTEMPTS_PER_TURN &&
			sessionGuards.stored === 0
		) {
			stopTurn(
				"loop",
				`${sessionGuards.recordingCalls} recording calls without a record — aborting this turn`,
			);
		}
	}
}

/** Guard memory belongs to one native session; the turn trace still accumulates its shared work. */
const repeatedCalls = new Map<string, number>();
let sessionGuards = { cutOff: 0, recordingCalls: 0, stored: 0 };
function resetSessionGuards(): void {
	repeatedCalls.clear();
	refusedItems.clear();
	recordingMessageTokens = 0;
	sessionGuards = { cutOff: 0, recordingCalls: 0, stored: 0 };
}

function openTurnTrace(label: string, budget: Work, demand: TurnDemand): TurnTrace {
	resetSessionGuards();
	lastEventAt = Date.now();
	currentTurn = {
		modelError: false,
		modelFailures: {},
		lastModelFailureAt: null,
		finalModelFailure: null,
		unattributedModelFailures: 0,
		modelCallTiming: newModelCallTiming(),
		runtimeError: false,
		label,
		durationMs: Date.now(),
		calls: 0,
		toolCalls: {},
		recordingCalls: 0,
		stored: 0,
		refused: 0,
		refusalReasons: [],
		toolErrors: 0,
		toolErrorReasons: [],
		repeatedCalls: 0,
		compactions: 0,
		providerRetries: 0,
		outputTokens: 0,
		cutOff: 0,
		budget,
		askedToRecord: false,
		stoppedBy: null,
	};
	turnDemand = demand;
	return currentTurn;
}

function recordModelResponse(trace: TurnTrace, stopReason: string, message: unknown): void {
	const failed = stopReason === "error" || stopReason === "aborted";
	addModelCall(trace.modelCallTiming, message, failed);
	if (failed) {
		const failure = modelFailure(message);
		trace.modelFailures[failure.kind] = (trace.modelFailures[failure.kind] ?? 0) + 1;
		if (failure.source !== "ADAPTER") {
			trace.unattributedModelFailures += 1;
		}
		trace.lastModelFailureAt = failure.at ?? trace.lastModelFailureAt;
		trace.finalModelFailure = { ...failure, retryEndedUnsuccessfully: false };
	} else {
		trace.finalModelFailure = null;
	}
}

function recordUnsuccessfulRetryEnd(trace: TurnTrace): void {
	const final = trace.finalModelFailure;
	if (final !== null) {
		trace.finalModelFailure = { ...final, retryEndedUnsuccessfully: true };
	}
}

function closeTurnTrace(trace: TurnTrace): void {
	trace.durationMs = Date.now() - trace.durationMs;
	runnerDebug.turns.push(trace);
	currentTurn = null;
	turnDemand = null;
	persistRunnerDebug();
	console.error(
		`[pi-runner] ${trace.label}: ${(trace.durationMs / 1000).toFixed(1)}s, calls=${trace.calls}/${trace.budget.modelCalls}, ` +
			`outputTokens=${trace.outputTokens}/${trace.budget.outputTokens}${trace.stoppedBy ? `, stoppedBy=${trace.stoppedBy}` : ""}, ` +
			`tools=${JSON.stringify(trace.toolCalls)}, stored=${trace.stored}, refused=${trace.refused}` +
			`${trace.toolErrors ? `, toolErrors=${trace.toolErrors}` : ""}${trace.compactions ? `, compactions=${trace.compactions}` : ""}${trace.providerRetries ? `, providerRetries=${trace.providerRetries}` : ""}`,
	);
}

function scheduleDeadline(timeoutMs: number, onTimeout: () => void) {
	// Read timer state through a function so TypeScript does not retain a pre-await narrowing.
	let expired = false;
	const { promise: elapsed, resolve: release } = Promise.withResolvers<undefined>();
	const timer = setTimeout(() => {
		expired = true;
		onTimeout();
		release(undefined);
	}, timeoutMs);
	return { elapsed, timer, expired: () => expired };
}

/** A mid-turn nudge; the turn goes on either way, so a steer that fails is only logged. */
async function steer(session: AgentSession, message: string): Promise<void> {
	try {
		await session.steer(message);
	} catch (error) {
		console.error(`[pi-runner] steer failed: ${errorText(error)}`);
	}
}

/** Abort and wait for idle; Pi refuses a new prompt while the previous run is still active. */
async function abortSession(session: AgentSession): Promise<void> {
	session.clearQueue();
	// A compaction in flight is a model call of its own that `abort()` leaves running, and the
	// session is not idle until it ends; the next turn compacts again if it must, inside its own share.
	session.abortCompaction();
	try {
		await session.abort();
	} catch (error) {
		console.error(`[pi-runner] session abort failed: ${errorText(error)}`);
	}
}

/** How long an aborted turn waits for the session to go idle before the next turn is sent. */
const ABORT_SETTLE_MS = 30_000;

/** Return false if the previous operation does not become idle within maxMs. */
async function settleSession(
	session: AgentSession,
	label: string,
	maxMs: number,
): Promise<boolean> {
	if (session.isIdle) {
		return true;
	}
	const started = Date.now();
	const becameIdle = async (): Promise<boolean> => {
		await session.waitForIdle();
		return true;
	};
	const idle = await Promise.race([becameIdle(), sleep(maxMs, false)]);
	// The run flag clears a beat after the prompt resolves; a wait that short is not worth a line.
	const waitedMs = Date.now() - started;
	if (!idle || waitedMs >= 1000) {
		console.error(
			`[pi-runner] ${label}: waited ${(waitedMs / 1000).toFixed(1)}s for the session to go idle${idle ? "" : " — still busy"}`,
		);
	}
	return idle;
}

/** Where the practice's criteria are staged for this review. */
function criteriaPathOf(slug: string): string {
	return `${nodePath.dirname(INPUT_PATHS.practiceIndex)}/${slug}.md`;
}

function criteriaFileOf(slug: string): string | null {
	const file = criteriaPathOf(slug);
	return existsSync(file) ? readFileSync(file, "utf8") : null;
}

/** The input a program prepared for this review, as its bounded section; empty for a review without one. */
function preparedSectionOf(slug: string, section: string): string {
	const shown = section.trim();
	return shown
		? `\n\n#### Leads from the practice script for \`${slug}\` — starting points to check against the criteria, not verdicts\n${shown}`
		: "";
}

/** The criteria of the turn's practices, inlined exactly as staged: the turn carries what it asks about. */
function criteriaOf(slugs: readonly string[]): string {
	return slugs
		.map((slug) => {
			const criteria = criteriaFileOf(slug) ?? "(criteria file missing)";
			const exhaustive = [...(practiceExhaustiveSources.get(slug) ?? [])];
			const scope =
				exhaustive.length > 0
					? `Exhaustive sources (an absence claim must have searched all of them): ${exhaustive.join(", ")}.\n\n`
					: "";
			return `### Practice \`${slug}\`\n${scope}${criteria}`;
		})
		.join("\n\n");
}

/**
 * Whether the task, the brief and the measuring illustrations are still in the current session's context. A fresh
 * session holds none of them, so its first turn carries them; a compaction summarizes them away, so the turn after
 * it carries them again, and the model quotes the exact text rather than a summary of it.
 */
let openingInContext = false;

/** The shared user context, before any practice-specific criterion, task or recorded draft. */
function assessmentOpening(brief: string): string {
	return `${prompt}\n\n${brief}\n\n`;
}

/**
 * The task and the brief, when the context no longer holds them; nothing otherwise. A measuring session sees only
 * its own practice's draft: other practices' results are not its context. The composer records no observation, so
 * it gets the task and the brief alone.
 */
function openingIfNeeded(brief: string, composing = false): string {
	if (openingInContext) {
		return "";
	}
	const ownDrafts = activeReview?.draft != null;
	return composing
		? assessmentOpening(brief)
		: `${assessmentOpening(brief)}${
				ownDrafts ? `## Recorded so far\n${recordedSoFar(currentTurnSlugs)}\n\n` : ""
			}`;
}

/**
 * Separate single-result examples with this review's artifact paths. Alternative outcomes keep the call
 * shape consistent with one practice per session without making an outcome the expected one.
 */
const RESULT_EXAMPLE = (() => {
	const context = taskEnvelope.paths.contextRoot;
	const example = [
		{
			outcome: "MET",
			summary: "Added test asserts that malformed date input is rejected",
			explanation:
				'The added assertion passes "not-a-date" to DateParser.parse and expects an error.',
			citations: [
				{
					sourceKind: "scm.pull-request.diff",
					artifactPath: `${context}/change.json`,
					path: "Tests/DateParserTests.swift",
					side: "NEW",
					startLine: 12,
					endLine: 12,
					quote: 'XCTAssertThrowsError(try DateParser.parse("not-a-date"))',
				},
			],
			evidence: {},
		},
		{
			outcome: "NOT_MET",
			summary: "New error branch of the parser ships without a test",
			explanation:
				"The change adds a branch that rejects an empty string; no added test feeds the parser one.",
			citations: [],
			problems: [
				{
					severity: "MINOR",
					summary: "Empty-string branch of the parser has no test",
					explanation:
						"The change adds a guard that throws for an empty string; no added test passes one.",
					citations: [
						{
							sourceKind: "scm.pull-request.diff",
							artifactPath: `${context}/change.json`,
							path: "App/DateParser.swift",
							side: "NEW",
							startLine: 30,
							endLine: 30,
							quote: "guard !text.isEmpty else { throw ParseError.empty }",
						},
					],
					basis: {
						type: "introduced",
						head: 0,
						explanation: "The change adds this branch on the new side.",
					},
				},
			],
			evidence: {
				search: {
					consulted: ["scm.pull-request.diff"],
					lookedFor: "an added test that passes an empty string to the parser",
					boundary: "the added test files of this change; tests outside the change were not read",
				},
			},
		},
		{
			outcome: "NOT_APPLICABLE",
			summary: "No completed merge is recorded",
			explanation: "The captured provider record states that this work has not merged.",
			citations: [
				{
					sourceKind: "scm.pull-request.core",
					artifactPath: `${context}/metadata.json`,
					path: `${context}/metadata.json`,
					startLine: 3,
					endLine: 3,
					quote: '"is_merged": false',
				},
			],
			evidence: {
				inapplicability: {
					consulted: ["scm.pull-request.core"],
					subject: "work that has merged",
					ruledOutBy: "the provider records is_merged as false",
				},
			},
		},
	];
	return `## How submit_result takes a result
The arguments are one complete result object for this session's practice, not a list and not a JSON-encoded string. Each block below is a whole alternative call, with an illustrative outcome, not evidence from this work:
${example.map((result) => `\`\`\`json\n${JSON.stringify(result, null, 1)}\n\`\`\``).join("\n\n")}`;
})();

/**
 * A turn of one practice, after the opening when the context does not hold it: its whole criterion first, then
 * the generic illustrations when the context does not hold them, the input its program prepared, and the task. Its
 * work budget is not stated: told its call budget up front, an open model read less and missed more not-met practices.
 */
function practiceTurnText(heading: string, slug: string, brief: string, prepared: string): string {
	const illustrations = openingInContext ? "" : `\n\n${RESULT_EXAMPLE}`;
	return `${openingIfNeeded(brief)}${heading}

${criteriaOf([slug])}${illustrations}${preparedSectionOf(slug, prepared)}

Submit one result for ${slug} with submit_result: the outcome its criterion above decides through its Occasion, Judge and Defer, as the evidence supports it, NOT_APPLICABLE and UNDETERMINED included. What the brief shows is yours to quote; read more when the criterion needs evidence beyond it. This session submits and corrects only this practice's result.`;
}

/** The practices with an admitted NOT_MET result: what the composer has a decision to record on. */
function notMetPractices(results: readonly { practiceSlug: string; outcome: Outcome }[]): string[] {
	return [
		...new Set(
			results.filter((result) => result.outcome === "NOT_MET").map((result) => result.practiceSlug),
		),
	].toSorted();
}

/**
 * Composition's budget: a unit per NOT_MET practice, but never more than a full turn's worth — the
 * lanes cap what it can write, so work beyond that is a loop, not more feedback.
 */
function compositionBudget(notMet: number): Work {
	return turnBudget(Math.min(Math.max(1, notMet), PRACTICES_PER_TURN), PER_PRACTICE_WORK);
}

/** Private decision obligation: a stored unit or withholding may fold several practices in. */
function undecidedPrivatePractices(): string[] {
	const practiceOf = new Map(
		admittedResults.map((result) => [result.practiceResultId, result.practiceSlug]),
	);
	const decided = new Set(
		composedFeedback.units.flatMap((unit) => [
			unit.practiceSlug,
			...unit.basedOn.flatMap((ref) => practiceOf.get(ref.practiceResultId) ?? []),
		]),
	);
	return privatelyComposable().filter((slug) => !decided.has(slug));
}

function recordCompositionFailure(phase: CompositionPhase, reason: CompositionFailureReason): void {
	if (!composedFeedback.compositionFailures.some((failure) => failure.phase === phase)) {
		composedFeedback.compositionFailures.push({ phase, reason });
	}
}

function incompleteCompositionReason(
	trace: TurnTrace,
	safetyExpired: boolean,
): CompositionFailureReason {
	const stops = { budget: "BUDGET", stall: "STALL", safety: "SAFETY", loop: "LOOP" } as const;
	if (trace.stoppedBy !== null) {
		return stops[trace.stoppedBy];
	}
	if (safetyExpired) {
		return "SAFETY";
	}
	if (trace.modelError) {
		return "MODEL_ERROR";
	}
	if (trace.runtimeError) {
		return "RUNTIME_ERROR";
	}
	return "NO_DECISION";
}

/** Retry undecided composition once, under its own work budget and the run's safety line. */
async function askComposerOnceMore(
	session: AgentSession,
	notMet: readonly string[],
	undecided: () => readonly string[],
	request: CompositionRequest,
	buildText: () => string,
	safety: ReturnType<typeof scheduleDeadline>,
): Promise<TurnTrace> {
	console.error(
		`[pi-runner] composition left ${notMet.length} NOT_MET practice(s) undecided — asking once more`,
	);
	const trace = openTurnTrace("composition once more", compositionBudget(notMet.length), {
		owed: () => undecided().length,
		nudge: COMPOSITION_NUDGE,
		endsWhenPaid: true,
	});
	try {
		if (!(await settleSession(session, "composition", ABORT_SETTLE_MS))) {
			throw new Error("the session was still busy when the composition was asked once more");
		}
		if (!safety.expired()) {
			await Promise.race([
				(async () => {
					const prepared = await prepareTurnText(
						session,
						() => `${buildText()}

${finishCompositionText(notMet, request)}`,
					);
					if (safety.expired()) {
						return;
					}
					if (prepared === null) {
						throw new Error("essential composition input exceeds the context window");
					}
					await session.prompt(prepared, {
						preflightResult: (disposition) => {
							if (disposition === "started") {
								openingInContext = true;
							}
						},
					});
				})(),
				safety.elapsed,
			]);
		}
	} catch (error) {
		trace.runtimeError = trace.stoppedBy === null && !safety.expired();
		console.error(`[pi-runner] composition failed: ${errorText(error)}`);
	} finally {
		if (trace.stoppedBy !== null) {
			await settleSession(session, "composition", ABORT_SETTLE_MS);
		}
		closeTurnTrace(trace);
	}
	return trace;
}

function finishCompositionText(notMet: readonly string[], request: CompositionRequest): string {
	// The lanes as they stand: a retry that does not know a lane is full writes units that are skipped.
	const room = PRIVATE_CHANNELS.filter((channel) => request.channels[channel].enabled)
		.map((channel) => {
			const { maxUnits } = request.channels[channel];
			const used = composedFeedback.units.filter(
				(unit) => unit.channel === channel && unit.action !== "WITHHOLD",
			).length;
			return `${channel} ${Math.max(0, maxUnits - used)} of ${maxUnits}`;
		})
		.join(", ");
	return (
		`## Undecided\nThe turn ended with no unit and no WITHHOLD for these practices, each with a ` +
		`NOT_MET result: ${notMet.join(", ")}. For each, persist the unit you decided on, or a ` +
		`WITHHOLD with its reason (NO_MATERIAL_CHANGE, ALREADY_SAID, BELOW_BAR), in one report_feedback ` +
		`call. Room left for new units: ${room}; a lane with none left takes only a WITHHOLD. The ` +
		`admitted results and their observations are in \`work/composition/results.json\`. Use tools only from this ` +
		`point onward; no prose.`
	);
}

async function main() {
	console.error(`[pi-runner] Review: one fresh session per practice, then composition`);
	console.error(
		`[pi-runner] Work per practice: ${PER_PRACTICE_WORK.modelCalls} model calls, ${PER_PRACTICE_WORK.outputTokens} output tokens; ` +
			`safety ceiling ${AGENT_BUDGET_MS}ms, measuring hands in by ${WINDOWS.measureMs}ms`,
	);

	// pi-agent-sandbox.ts has the rationale for running untrusted; both Pi runners in this image
	// share it.
	const settingsManager = SettingsManager.create(CWD, AGENT_DIR, SANDBOX_SETTINGS_MANAGER_OPTIONS);
	// Disable instruction discovery; load only the server-staged orchestrator (see pi-agent-sandbox.ts).
	const orchestratorPath = `${AGENT_DIR}/AGENTS.md`;
	const orchestrator = orchestratorWithPaths(readFileSync(orchestratorPath, "utf8"));
	const modelRuntime = await ModelRuntime.create({
		authPath: `${AGENT_DIR}/auth.json`,
		modelsPath: `${AGENT_DIR}/models.json`,
		allowModelNetwork: false,
	});

	const providerConfig = loadProviderConfig(CWD);
	if (!registerHephaestusProvider(modelRuntime, providerConfig)) {
		throw new Error(
			"Hephaestus provider is not configured — pi-provider.json and proxy credentials are required",
		);
	}
	const registeredModel = modelRuntime.getModel("hephaestus", providerConfig.modelId);
	if (!registeredModel) {
		throw new Error(`Hephaestus model was not registered: ${providerConfig.modelId}`);
	}
	const model = registeredModel;
	outputLimit = model.maxTokens > 0 ? model.maxTokens : Number.POSITIVE_INFINITY;
	console.error(
		`[pi-runner] registered hephaestus provider: apiProtocol=${providerConfig.apiProtocol} ` +
			`model=${providerConfig.modelId} contextWindow=${model.contextWindow}`,
	);
	const { thinkingLevel } = reasoningSetting(providerConfig.reasoningEffort);
	console.error(
		`[pi-runner] reasoning effort: ${providerConfig.reasoningEffort?.toLowerCase() ?? "provider default"}`,
	);

	/**
	 * A native session file under .sessions, bound to what it reviewed by one custom entry. The SDK keeps custom
	 * entries out of the model's context; the worker retains the file for a day as a debug transcript.
	 */
	function nativeSessionManager(phase: SessionPhase, practiceSlug: string | null) {
		const manager = SessionManager.create(CWD, `${CWD}/.sessions`);
		const binding: ReviewSessionBinding = {
			phase,
			practiceSlug,
			practiceRevisionId:
				practiceIndex.find((practice) => practice.slug === practiceSlug)?.revisionId ?? null,
			model: model.id,
			jobId: typeof taskEnvelope.jobId === "string" ? taskEnvelope.jobId : null,
			workspaceId: typeof taskEnvelope.workspaceId === "number" ? taskEnvelope.workspaceId : null,
			openedAt: new Date().toISOString(),
		};
		manager.appendCustomEntry(REVIEW_SESSION_ENTRY, binding);
		return manager;
	}

	const compositionRequest = loadCompositionRequest();
	const streamUsage = newUsageLedger();
	let providerFailures = 0;
	const measurementReply = { received: false };
	let measuring = true;
	const subscribeSession = (trackedSession: AgentSession) =>
		trackedSession.subscribe((event: AgentSessionEvent) => {
			chargeWork(event);
			const label = measuring ? "review" : "composer";
			if (event.type === "tool_execution_start") {
				// A call a codemode script makes is the script's work, not a call the model sent: the loop
				// guards count only the latter, and the trace keeps the former apart.
				if (event.parentToolCallId === undefined) {
					if (measuring) {
						measurementReply.received = true;
					}
					console.error(`[pi-runner] ${label} tool: ${event.toolName}`);
					if (currentTurn) {
						noteToolCall(currentTurn, event.toolName, event.args, measuring);
					}
				} else {
					console.error(`[pi-runner] ${label} tool: codemode → ${event.toolName}`);
					if (currentTurn) {
						const key = `codemode.${event.toolName}`;
						currentTurn.toolCalls[key] = (currentTurn.toolCalls[key] ?? 0) + 1;
					}
				}
			}
			// SDK validation errors do not reach the tool, so log them from the session event.
			if (
				event.type === "tool_execution_end" &&
				event.isError &&
				!answeredRefusals.delete(event.toolCallId)
			) {
				const result: unknown = event.result;
				const reason = firstLine(toolResultText(result));
				const tool =
					event.parentToolCallId === undefined ? event.toolName : `codemode → ${event.toolName}`;
				console.error(`[pi-runner] ${label} tool error: ${tool} — ${reason}`);
				if (currentTurn) {
					currentTurn.toolErrors += 1;
					currentTurn.toolErrorReasons.push(`${tool}: ${reason}`);
				}
			}
			if (event.type === "compaction_end") {
				console.error(
					`[pi-runner] ${label} context compacted (${event.reason})${hasText(event.errorMessage) ? `: ${event.errorMessage}` : ""}`,
				);
				if (!event.aborted && !hasText(event.errorMessage)) {
					openingInContext = false;
				}
				if (currentTurn && !event.aborted) {
					currentTurn.compactions += 1;
				}
			}
			// The SDK rides out a retryable provider failure on its own. A run that took longer, or that
			// gave up after several of these, says so here rather than looking like an idle session.
			if (event.type === "auto_retry_start") {
				if (currentTurn) {
					currentTurn.providerRetries += 1;
				}
				console.error(
					`[pi-runner] ${label} provider call failed, retrying in ${event.delayMs}ms ` +
						`(attempt ${event.attempt}/${event.maxAttempts}): ${event.errorMessage}`,
				);
			}
			if (event.type === "auto_retry_end" && !event.success) {
				if (currentTurn) {
					currentTurn.modelError = true;
					recordUnsuccessfulRetryEnd(currentTurn);
				}
				const finalError = event.finalError ?? "no error given";
				// Only measurement failures can make a review eligible for provider retry.
				if (measuring) {
					providerFailures += 1;
				}
				console.error(
					`[pi-runner] ${label} provider call failed for good after ${event.attempt} retries: ${finalError}`,
				);
			}
			if (event.type === "message_end" && event.message.role === "assistant") {
				addAssistantUsage(streamUsage, event.message);
				const { stopReason } = event.message;
				// An error or abort alone is not a reply; meaningful partial output is. Once answered,
				// later provider failures cannot turn a recording failure into a wholly unreachable review.
				if (
					measuring &&
					((stopReason !== "error" && stopReason !== "aborted") ||
						event.message.content.some(
							(content) =>
								content.type === "toolCall" || (content.type === "text" && !isBlank(content.text)),
						))
				) {
					measurementReply.received = true;
				}
				if (currentTurn) {
					currentTurn.modelError = stopReason === "error";
					recordModelResponse(currentTurn, stopReason, event.message);
				}
				const types = listOrEmpty(event.message.content).map((c) => c.type);
				const toolCalls = types.filter((t) => t === "toolCall").length;
				const { rawStopReason } = event.message;
				// An error can leave a review unanswered even when the SDK does not retry it.
				if (stopReason === "error" && measuring) {
					providerFailures += 1;
				}
				const failure =
					stopReason === "error" || stopReason === "length"
						? `, error=${event.message.errorMessage ?? "none given"}${hasText(rawStopReason) ? `, rawStopReason=${rawStopReason}` : ""}`
						: "";
				console.error(
					`[pi-runner] ${label} assistant msg: stopReason=${stopReason}, toolCalls=${toolCalls}, ` +
						`types=[${types.join(",")}]${failure}`,
				);
			}
		});

	const allSlugs = loadPracticeSlugs();
	// A continuing writer runs no program and derives no change.
	// A continuing writer evaluates no practice, so it reports no coverage of its own.
	if (!continuing) {
		practiceCoverageLedger = new PracticeCoverageLedger(PRACTICE_COVERAGE_PATH, allSlugs);
		deriveChangeView();
	}
	persistRecordedNotes();
	const turns = planTurns(practiceIndex, PRACTICES_PER_TURN);
	const brief = buildBrief(CWD, {
		contextRoot: taskEnvelope.paths.contextRoot,
		repositoryRoot: taskEnvelope.paths.repositoryRoot,
	});
	console.error(
		`[pi-runner] Review: ${allSlugs.length} practice(s) in ${turns.length} turn(s); brief=${brief.length} chars`,
	);

	const measureEnd = PROCESS_START_MS + WINDOWS.measureMs;
	const startMs = Date.now();
	const tooLate = !continuing && Date.now() >= measureEnd;
	if (tooLate) {
		console.error(
			`[pi-runner] FAILED: the run reached its safety ceiling before the session could start`,
		);
	}

	const submitResultTool = buildSubmitResultTool();
	if (tooLate) {
		logPracticeCoverage();
		finalizeOutput();
		process.exit(1);
	}

	const cacheExtension = assessmentCacheExtension(
		providerConfig,
		modelRuntime,
		assessmentOpening(brief),
		taskEnvelope.workspaceId,
		taskEnvelope.jobId,
	);

	async function assessmentLoader() {
		// Extension bindings belong to the native session that uses them.
		const resourceLoader = new DefaultResourceLoader({
			cwd: CWD,
			agentDir: AGENT_DIR,
			settingsManager,
			...SANDBOX_RESOURCE_LOADER_OPTIONS,
			systemPrompt: orchestrator,
			agentsFilesOverride: () => ({ agentsFiles: [] }),
			// "on" keeps every tool callable directly as well; scripts get no model catalog to call.
			extensionFactories: [createCodemodeExtension({ mode: "on", models: false }), cacheExtension],
		});
		await resourceLoader.reload();
		return resourceLoader;
	}

	type SessionOptions = NonNullable<Parameters<typeof createAgentSession>[0]>;
	/**
	 * A fresh native session in this sandbox, with the orchestrator, the evidence tools and the given recording
	 * tool. Reported native assistant usage events go to one stream ledger across these sessions.
	 */
	async function openSession(
		phase: SessionPhase,
		tools: string[],
		customTools: SessionOptions["customTools"],
		resourceLoader?: SessionOptions["resourceLoader"],
	) {
		const { session: fresh, extensionsResult } = await createAgentSession({
			cwd: CWD,
			agentDir: AGENT_DIR,
			tools,
			customTools,
			sessionManager: nativeSessionManager(phase, activePractice),
			settingsManager,
			resourceLoader: resourceLoader ?? (await assessmentLoader()),
			modelRuntime,
			model,
			thinkingLevel,
		});
		for (const error of extensionsResult.errors) {
			console.error(`[pi-runner] extension error: ${error.path}: ${error.error}`);
		}
		resetSessionGuards();
		openingInContext = false;
		lastEventAt = Date.now();
		activeSession = fresh;
		return { session: fresh, unsubscribe: subscribeSession(fresh) };
	}

	/** Keep settlement events subscribed until the native session stops before another scope starts. */
	async function stopBeforeSwitching(session: AgentSession): Promise<void> {
		const deadline = scheduleDeadline(ABORT_SETTLE_MS, () => undefined);
		try {
			const stopped = await Promise.race([
				stopSession(session).then(() => true),
				deadline.elapsed.then(() => false),
			]);
			if (!stopped) {
				throw new Error("Native session did not settle; no later session may start");
			}
		} catch (error) {
			measurementClosed = true;
			throw error;
		} finally {
			clearTimeout(deadline.timer);
		}
	}

	async function closeSession(opened: Awaited<ReturnType<typeof openSession>>): Promise<void> {
		opened.session.abortCompaction();
		try {
			await stopBeforeSwitching(opened.session);
		} finally {
			opened.unsubscribe();
			if (activeSession === opened.session) {
				activeSession = null;
			}
		}
	}

	// Liveness, not a budget: a turn that shows no sign of life for STALL_MS is aborted.
	const stallWatch = setInterval(() => {
		if (currentTurn?.stoppedBy === null && Date.now() - lastEventAt >= STALL_MS) {
			if (measuring) {
				providerFailures += 1;
			}
			stopTurn("stall", `no model or tool event for ${STALL_MS / 1000}s — aborting this turn`);
		}
	}, STALL_CHECK_MS);
	stallWatch.unref();

	const evidenceSources = captureSources();
	/** The change since each practice's previous review, as its inputs established it. */
	const changeSinceOf = new Map<string, ReadonlyMap<string, DiffFile>>();
	const inputReader = new InputReader({
		practicesDir: INPUT_PATHS.practiceScripts,
		contextDir: INPUT_PATHS.contextRoot,
		repo: INPUT_PATHS.repositoryRoot,
		log: (line) => console.error(`[pi-runner]${line}`),
	});
	const boundModels = readBoundModels();
	const directBudget = new ModelBudget(DIRECT_MODEL_TOKENS, MODEL_CONCURRENCY);
	/** A review session that did not stop: the run ends with it, and no later session starts. */
	let sessionStopFailure: Error | null = null;
	/** Every program's execution ends with the measuring window: at its end, or when the attempt is cancelled. */
	const measuringEnded = new AbortController();

	/**
	 * One scope — a catalog group's turn, or the practices no turn reached — under the one work budget its
	 * practice count earns, whatever the scopes before it spent. Each practice's program runs in turn; each review
	 * it asks for opens a fresh session that receives the task, the brief, the practice's complete criteria and the
	 * program's prepared input, and submits only that practice's result. Once the budget, the safety line or a stall
	 * ends the scope, its remaining practices are not reached. Returns why the runner ended it, or null when every
	 * program ended on its own.
	 */
	async function runScope(
		label: string,
		heading: string,
		slugs: readonly string[],
	): Promise<StopReason | null> {
		const safetyMs = measureEnd - Date.now();
		if (safetyMs <= 0) {
			console.error(`[pi-runner] ${label}: the run is near its safety ceiling — skipped`);
			return "safety";
		}
		const budget = turnBudget(slugs.length, PER_PRACTICE_WORK);
		console.error(
			`[pi-runner] ${label}: ${slugs.length} practice(s), up to ${budget.modelCalls} calls and ${budget.outputTokens} output tokens (${tokensPerObservation} tokens per result so far)`,
		);
		const trace = openTurnTrace(label, budget, {
			owed: () => owedPractices(slugs).length,
			nudge: RECORD_NUDGE,
		});
		const safety = scheduleDeadline(safetyMs, () =>
			stopTurn("safety", "the run is near its safety ceiling — aborting this turn"),
		);
		// Native events may stop a turn while awaited work settles or another session opens.
		const stoppedBy = () => trace.stoppedBy;
		const pastSafety = () => safety.expired() || Date.now() >= measureEnd;
		/** The model, not the runner, a provider error or the safety line, ended the session with its result owed. */
		const endedUnrecorded = (slug: string) =>
			owedPractices([slug]).length > 0 &&
			stoppedBy() === null &&
			!pastSafety() &&
			!trace.modelError;
		async function promptPractice(session: AgentSession, text: () => string, slug: string) {
			if (pastSafety()) {
				return;
			}
			const prepared = await prepareTurnText(session, text);
			if (pastSafety()) {
				return;
			}
			if (prepared === null) {
				blockedPractices.set(slug, "too-large");
				console.error(
					`[pi-runner] ${label}: essential input for ${slug} exceeds the context window — not reached`,
				);
				return;
			}
			await session.prompt(prepared, {
				preflightResult: (disposition) => {
					if (disposition === "started") {
						openingInContext = true;
					}
				},
			});
		}
		/**
		 * Asks a session that ended on its own with its result owed to submit it, once, in the session that holds
		 * what it read and within this scope's budget; a fresh session would read it all again. A second ending on
		 * its own closes the review.
		 */
		async function askOnceMore(session: AgentSession, text: () => string, slug: string) {
			if (
				!endedUnrecorded(slug) ||
				spent({ modelCalls: trace.calls, outputTokens: trace.outputTokens }, trace.budget)
			) {
				return;
			}
			console.error(
				`[pi-runner] ${label}: the ${slug} session ended without a submitted result — asking once more`,
			);
			if (!(await settleSession(session, label, ABORT_SETTLE_MS))) {
				throw new Error("the session was still busy when it was asked once more");
			}
			// After a compaction the request carries the practice turn again, as any next turn does.
			const onceMore = () =>
				`${openingInContext ? "" : `${text()}\n\n`}${recordOnceMoreText(slug)}`;
			await Promise.race([promptPractice(session, onceMore, slug), safety.elapsed]);
			if (endedUnrecorded(slug)) {
				blockedPractices.set(slug, "off-format");
				console.error(
					`[pi-runner] ${label}: the ${slug} session ended again without a submitted result — not reached`,
				);
			}
		}
		/** Why a review that ended without a result gives none: never a statement about the work. */
		function noResultReason(slug: string, signal: AbortSignal): UnratedReason {
			if (signal.aborted) {
				return abortReasonOf(signal);
			}
			const stop = stoppedBy();
			if (stop !== null) {
				return UNRATED_BY_STOP[stop];
			}
			if (pastSafety()) {
				return "deadline";
			}
			if (trace.modelError) {
				return "unavailable";
			}
			return blockedPractices.get(slug) ?? "off-format";
		}
		/**
		 * One review of `slug`, as its program asks for it: a fresh session over the program's prepared input,
		 * whose submissions go to this call's draft alone. It returns the draft once the session ends, or says
		 * why there is none.
		 */
		async function reviewPractice(
			slug: string,
			practiceHeading: string,
			options: ReviewOptions,
			signal: AbortSignal,
			reads: Receipts,
		): Promise<Candidate> {
			if (activeReview !== null) {
				throw new UnratedError("refused", "a review is already running");
			}
			if (blockedPractices.has(slug)) {
				throw new UnratedError(
					blockedPractices.get(slug) ?? "off-format",
					`this review asks no more about ${slug}`,
				);
			}
			if (stoppedBy() !== null || pastSafety() || signal.aborted) {
				throw new UnratedError(noResultReason(slug, signal), "the scope ended before the review");
			}
			const leads = await resolveLeads(
				options.leads ?? [],
				evidenceSources,
				changeSinceOf.get(slug),
			);
			const fullInput = `${REVIEW_INPUT_ROOT}/${slug}.json`;
			const preparing =
				leads.length > 0 ||
				Object.keys(options.facts ?? {}).length > 0 ||
				(options.directions ?? []).length > 0 ||
				(options.context ?? []).length > 0 ||
				(options.limitations ?? []).length > 0;
			if (preparing) {
				mkdirSync(`${CWD}/${REVIEW_INPUT_ROOT}`, { recursive: true });
				writeFileSync(`${CWD}/${fullInput}`, JSON.stringify({ ...options, leads }, null, 2));
			}
			const prepared = preparing
				? renderReviewInput(
						options,
						leads,
						taskEnvelope.paths.contextRoot,
						`\`${fullInput}\``,
						changeSinceOf.get(slug) !== undefined,
					)
				: "";
			const review: ActiveReview = { slug, draft: null, reads: newSessionReads() };
			activeReview = review;
			activePractice = slug;
			currentTurnSlugs = [slug];
			// The nudge names the session's practice, so a later session may be told again.
			trace.askedToRecord = false;
			let opened: Awaited<ReturnType<typeof openSession>>;
			try {
				opened = await openSession(
					"practice",
					[...PRACTICE_TOOLS, "submit_result"],
					[submitResultTool],
				);
			} catch (error) {
				activeReview = null;
				activePractice = null;
				throw error;
			}
			const unsubscribeReads = opened.session.subscribe((event) => {
				noteSessionRead(review.reads, event);
			});
			const stop = () => {
				void abortSession(opened.session);
			};
			signal.addEventListener("abort", stop, { once: true });
			const text = () => practiceTurnText(practiceHeading, slug, brief, prepared);
			try {
				signal.throwIfAborted();
				await Promise.race([promptPractice(opened.session, text, slug), safety.elapsed]);
				await askOnceMore(opened.session, text, slug);
			} catch (error) {
				console.error(`[pi-runner] ${label} (${slug}) failed: ${errorText(error)}`);
			} finally {
				signal.removeEventListener("abort", stop);
				const closed = await closeSession(opened).then(
					() => null,
					(error: unknown) => (error instanceof Error ? error : new Error(String(error))),
				);
				unsubscribeReads();
				const receipts = sessionReceipts(review.reads);
				reads.reads += receipts.reads;
				reads.truncatedReads += receipts.truncatedReads;
				reads.failedReads += receipts.failedReads;
				activeReview = null;
				activePractice = null;
				// A session that does not stop leaves nothing safe to start after it: the run ends.
				sessionStopFailure ??= closed;
			}
			if (sessionStopFailure !== null) {
				throw sessionStopFailure;
			}
			signal.throwIfAborted();
			if (review.draft !== null) {
				return review.draft;
			}
			throw new UnratedError(
				noResultReason(slug, signal),
				`the review session of ${slug} ended without a result`,
			);
		}
		/** Run one practice's program to its final result, or to why it has none. */
		async function runProgram(slug: string, practiceHeading: string): Promise<void> {
			const program = programOf(slug);
			const read = await inputReader.read(slug).catch((error: unknown) => ({
				error: `the host could not read them: ${errorText(error)}`,
			}));
			if ("error" in read) {
				console.error(`[pi-runner] ${slug}: its inputs are not valid: ${read.error}`);
				executions.set(slug, {
					...notReachedOutcome(program, "unavailable"),
					status: "failed",
					error: `the review's inputs are not valid: ${read.error}`,
					durationMs: 0,
				});
				return;
			}
			if (read.changeSince !== undefined) {
				changeSinceOf.set(slug, read.changeSince);
			}
			const programReads = noReceipts();
			const sessionReads = noReceipts();
			const stopped = new AbortController();
			// The safety line stops the program as it stops the session; a scope that ends first clears the timer.
			const safetyTimer = setTimeout(
				() => {
					stopped.abort(new UnratedError("deadline", "the run is near its safety ceiling"));
				},
				Math.max(0, measureEnd - Date.now()),
			);
			const outcome = await runPracticeProgram(
				program,
				{
					slug,
					inputs: read.inputs,
					criteria: criteriaFileOf(slug) ?? "",
					...(read.changeSince === undefined ? {} : { changeSince: read.changeSince }),
					files: jobFiles(read.inputs.kind),
					now: new Date(PROCESS_START_MS).toISOString(),
					deadline: measureEnd - RETURN_MS,
				},
				{
					reader: new EvidenceReader(evidenceSources, programReads),
					models: hostModels(boundModels, LLM_PROXY_URL, LLM_PROXY_TOKEN, slug),
					budget: directBudget,
					review: async (options, signal) =>
						reviewPractice(slug, practiceHeading, options, signal, sessionReads),
					signal: AbortSignal.any([stopped.signal, measuringEnded.signal]),
					log: (line) => {
						console.error(`[pi-runner]${line}`);
					},
					receipts: programReads,
				},
			);
			const reached: Receipts = {
				reads: outcome.coverageReached.reads + sessionReads.reads,
				truncatedReads: outcome.coverageReached.truncatedReads + sessionReads.truncatedReads,
				failedReads: outcome.coverageReached.failedReads + sessionReads.failedReads,
			};
			clearTimeout(safetyTimer);
			executions.set(slug, { ...outcome, coverageReached: reached });
			if (sessionStopFailure !== null) {
				throw sessionStopFailure;
			}
			console.error(
				`[pi-runner] ${slug}: ${program.script === null ? "built-in" : "custom"} program ${outcome.status}` +
					`${outcome.stopReason === null ? "" : ` (${outcome.stopReason})`}${outcome.error === undefined ? "" : `: ${firstLine(outcome.error)}`}`,
			);
			if (outcome.candidate !== undefined) {
				reviewState.set(slug, outcome.candidate);
				persistReviewState();
				maybeWriteResultFile();
				persistPracticeCoverage();
				persistRecordedNotes();
			}
		}
		try {
			for (const [index, slug] of slugs.entries()) {
				if (stoppedBy() !== null || pastSafety()) {
					break;
				}
				if (executions.has(slug)) {
					continue;
				}
				await runProgram(
					slug,
					`${heading}\nPractice ${index + 1} of ${slugs.length} in this turn.`,
				);
			}
		} finally {
			clearTimeout(safety.timer);
			activePractice = null;
			currentTurnSlugs = slugs;
			closeTurnTrace(trace);
		}
		if (pastSafety() && stoppedBy() === null) {
			console.error(
				`[pi-runner] ${label}: the run is near its safety ceiling — the rest is not reached`,
			);
			return "safety";
		}
		return stoppedBy();
	}

	if (continuing) {
		await continueAdmittedReview();
	}

	let measureUsage: UsageReport;
	try {
		let stalls = 0;
		for (const [index, turn] of turns.entries()) {
			const stop = await runScope(
				`turn ${index + 1}/${turns.length} (${turn.id})`,
				`## Turn ${index + 1} of ${turns.length}: ${turn.id}`,
				turn.slugs,
			);
			stalls = stop === "stall" ? stalls + 1 : 0;
			if (stop === "safety" || stalls >= 2) {
				console.error(
					`[pi-runner] Measuring ends early: ${stop === "safety" ? "safety ceiling" : "the session stalled twice in a row"}`,
				);
				break;
			}
		}
		// A practice whose program ran has its outcome; one that no program reached is run now.
		const unfinished = allSlugs.filter(
			(slug) => !executions.has(slug) && !blockedPractices.has(slug),
		);
		if (unfinished.length > 0 && stalls < 2 && Date.now() < measureEnd) {
			console.error(
				`[pi-runner] Finishing ${unfinished.length} practice(s): ${unfinished.join(", ")}`,
			);
			await runScope(
				"finish",
				"## Unfinished practices\nNo turn recorded a result for this practice; evaluate it now.",
				unfinished,
			);
		}
	} finally {
		measuring = false;
		// No program runs past this point: one still running would only be stopped at its deadline.
		measuringEnded.abort(new UnratedError("deadline", "measuring ended"));
		const measureDurationMs = Date.now() - startMs;
		measureUsage = extractUsageFromSession({}, streamUsage);
		accumulateUsage(null, measureUsage);
		runnerDebug.attempts.push({
			label: "measure",
			durationMs: measureDurationMs,
			assistantMessages: measureUsage.assistantMessages,
			stopReasons: measureUsage.stopReasons,
			usage: measureUsage,
			resultFilePresent: hasPersistedReviewState(),
		});
		persistRunnerDebug();
		persistUsage();
		console.error(
			`[pi-runner] Measured: ${(measureDurationMs / 1000).toFixed(1)}s, calls=${measureUsage.totalCalls}, ` +
				`stopped=[${runnerDebug.turns
					.flatMap((turn) => (turn.stoppedBy === null ? [] : [`${turn.label}: ${turn.stoppedBy}`]))
					.join("; ")}], results=${reviewState.size}`,
		);
	}

	const notReached = missingSlugs(allSlugs, [...reviewState.keys()]);
	logPracticeCoverage();
	if (notReached.length > 0) {
		console.error(
			`[pi-runner] PARTIAL: ${notReached.length} of ${allSlugs.length} practice(s) without a result: ${notReached.join(", ")}`,
		);
	}

	if (!maybeWriteResultFile()) {
		if (providerFailures > 0 && !measurementReply.received) {
			console.error(
				`[pi-runner] UNREACHABLE: this review reached no practice, and ${providerFailures} model call(s) ` +
					`went unanswered — the provider, not the work, is what this run could not read`,
			);
			finalizeOutput();
			process.exit(PROVIDER_UNREACHABLE_EXIT);
		}
		console.error(`[pi-runner] FAILED: this review reached no practice at all`);
		finalizeOutput();
		process.exit(1);
	}

	measurementClosed = true;
	// Prepared review input serves measurement only. Composition never receives it, and from here it cannot read it.
	rmSync(`${CWD}/${REVIEW_INPUT_ROOT}`, { recursive: true, force: true });
	await admitResults();
	if (compositionRequest) {
		closeUnroutedPrivateLanes(compositionRequest);
	}
	persistComposedFeedback();
	maybeWriteResultFile();
	if (compositionRequest && admittedResults.length > 0) {
		const markRemaining = (reason: CompositionFailureReason) => {
			if (
				compositionRequest.channels.IN_CONTEXT.enabled &&
				publicResults(admittedResults).length > 0 &&
				composedFeedback.review === null
			) {
				recordCompositionFailure("PUBLIC_REVIEW", reason);
			}
			if (
				PRIVATE_CHANNELS.some((channel) => compositionRequest.channels[channel].enabled) &&
				undecidedPrivatePractices().length > 0
			) {
				recordCompositionFailure("PRIVATE_FEEDBACK", reason);
			}
		};
		const safetyMs = AGENT_BUDGET_MS - (Date.now() - PROCESS_START_MS);
		if (safetyMs <= 0) {
			markRemaining("SAFETY");
			console.error(
				"[pi-runner] The run reached its safety ceiling before composition — preserving admitted results",
			);
		} else {
			const safety = scheduleDeadline(safetyMs, () => {
				stopTurn(
					"safety",
					"the run is near its safety ceiling — preserving results and composed feedback so far",
				);
			});
			try {
				// A review on the work is owed only for a public NOT_MET result. MET results and
				// abstentions alone have nothing to say there, and open no session.
				const reviewable = publicResults(admittedResults);
				if (compositionRequest.channels.IN_CONTEXT.enabled && reviewable.length > 0) {
					await composeReview(compositionRequest, reviewable, notReached, safety);
					await completeReviewOnWork(safety);
				}
				if (privatelyComposable().length > 0 && !safety.expired()) {
					await composePrivately(compositionRequest, safety);
				}
			} catch (error) {
				markRemaining(safety.expired() ? "SAFETY" : "RUNTIME_ERROR");
				throw error;
			} finally {
				if (safety.expired()) {
					markRemaining("SAFETY");
				}
				clearTimeout(safety.timer);
				persistComposedFeedback();
				// Every session's calls are in the one stream ledger; no single session's messages hold them all.
				const combinedUsage = extractUsageFromSession({}, streamUsage);
				accumulateUsage(measureUsage, combinedUsage);
				persistUsage();
			}
		}
	}
	console.error(`[pi-runner] SUCCESS: result.json holds ${reviewState.size} result(s)`);
	finalizeOutput();
	process.exit(0);

	/**
	 * An earlier attempt of this review measured, was admitted, and lost its writer. This one opens no practice session
	 * and admits nothing: it composes only the review on the work, from the admission the server staged and the inputs
	 * that attempt had. Private feedback is not composed here, and says so.
	 */
	async function continueAdmittedReview(): Promise<void> {
		measuring = false;
		measurementClosed = true;
		acceptAdmission(parseJson(readFileSync(CONTINUATION_PATH, "utf8")));
		// The original selection, less what was admitted: what that attempt did not reach stays not reached.
		const unreachedSlugs = missingSlugs(
			allSlugs,
			admittedResults.map((item) => item.practiceSlug),
		);
		console.error(
			`[pi-runner] Continuing an admitted review: ${admittedResults.length} result(s), ` +
				`${unreachedSlugs.length} practice(s) not reached; composing the review on the work only`,
		);
		if (
			compositionRequest &&
			PRIVATE_CHANNELS.some((channel) => compositionRequest.channels[channel].enabled) &&
			undecidedPrivatePractices().length > 0
		) {
			recordCompositionFailure("PRIVATE_FEEDBACK", "PRIVATE_NOT_RECOVERED");
		}
		persistComposedFeedback();
		const reviewable = publicResults(admittedResults);
		if (compositionRequest?.channels.IN_CONTEXT.enabled === true && reviewable.length > 0) {
			const safetyMs = AGENT_BUDGET_MS - (Date.now() - PROCESS_START_MS);
			if (safetyMs <= 0) {
				recordCompositionFailure("PUBLIC_REVIEW", "SAFETY");
			} else {
				const safety = scheduleDeadline(safetyMs, () => {
					stopTurn(
						"safety",
						"the run is near its safety ceiling — preserving the composed review so far",
					);
				});
				const unfinished = (reason: CompositionFailureReason) => {
					if (composedFeedback.review === null) {
						recordCompositionFailure("PUBLIC_REVIEW", reason);
					}
				};
				try {
					await composeReview(compositionRequest, reviewable, unreachedSlugs, safety);
					await completeReviewOnWork(safety);
				} catch (error) {
					unfinished(safety.expired() ? "SAFETY" : "RUNTIME_ERROR");
					throw error;
				} finally {
					if (safety.expired()) {
						unfinished("SAFETY");
					}
					clearTimeout(safety.timer);
					persistComposedFeedback();
					accumulateUsage(null, extractUsageFromSession({}, streamUsage));
					persistUsage();
				}
			}
		}
		console.error(
			`[pi-runner] SUCCESS: composed from the ${admittedResults.length} admitted result(s)`,
		);
		finalizeOutput();
		process.exit(0);
	}

	/**
	 * The review on the work, in a fresh session of its own whose only tool stores the whole review, with every input
	 * inline. Private history is not staged into it, and it can read nothing else from the workspace.
	 */
	async function composeReview(
		request: CompositionRequest,
		reviewable: readonly Record<string, unknown>[],
		notReachedSlugs: readonly string[],
		safety: ReturnType<typeof scheduleDeadline>,
	): Promise<void> {
		const lineNotes = request.inContextPlacementKinds.includes("DIFF");
		const reviewLoader = new DefaultResourceLoader({
			cwd: CWD,
			agentDir: AGENT_DIR,
			settingsManager,
			...PUBLIC_REVIEW_RESOURCE_LOADER_OPTIONS,
			systemPrompt: `${readFileSync(FEEDBACK_STYLE_PATH, "utf8")}\n\n${readFileSync(REVIEW_COMPOSER_PROMPT_PATH, "utf8")}`,
			agentsFilesOverride: () => ({ agentsFiles: [] }),
			extensionFactories: [],
		});
		await reviewLoader.reload();
		// Read now, not at capture: feedback delivered while this review ran was said here too. A failed read writes no
		// review rather than one that could repeat what was just delivered.
		let read: Awaited<ReturnType<typeof readPublicFeedbackHistory>>;
		try {
			read = await readPublicFeedbackHistory();
		} catch (error) {
			console.error(`[pi-runner] The review on the work was not composed: ${errorText(error)}`);
			recordCompositionFailure("PUBLIC_REVIEW", safety.expired() ? "SAFETY" : "RUNTIME_ERROR");
			return;
		}
		const framing = {
			repositoryFullName: taskEnvelope.repositoryFullName,
			pullRequestNumber: taskEnvelope.pullRequestNumber,
		};
		const captured = buildPublicReviewHistory(
			CWD,
			taskEnvelope.paths.contextRoot,
			folderIndex,
			framing,
		);
		const alreadySaid = priorPublicFeedback(
			read.history,
			THIS_WORK,
			captured.capturedAt,
			undefined,
			read.readAt,
		);
		runnerDebug.publicHistory = {
			readAt: read.readAt,
			workCapturedAt: captured.capturedAt,
			entries: alreadySaid.feedback.map(
				({ id, deliveredAt, eligibleForAlreadySaid, eligibleForPriorAdvice }) => ({
					id,
					deliveredAt,
					eligibleForAlreadySaid,
					eligibleForPriorAdvice,
				}),
			),
			omissions: alreadySaid.omissions,
		};
		persistRunnerDebug();
		const restable = reviewableById(reviewable);
		const state: PublicReviewState = { final: false, reviewContext: "LOST" };
		const practices = practiceContext(reviewable);
		/** The opening turn, whole: what a compaction removed. */
		const restore = () => {
			state.reviewContext = "RESTORING";
			return text;
		};
		const { session: reviewSession } = await createAgentSession({
			cwd: CWD,
			agentDir: AGENT_DIR,
			tools: PUBLIC_REVIEW_TOOLS,
			customTools: [
				buildReviewTool(
					lineNotes,
					restable,
					priorAdviceWitnesses(alreadySaid.feedback, captured.statements),
					state,
					restore,
				),
			],
			sessionManager: nativeSessionManager("public-review", null),
			settingsManager,
			resourceLoader: reviewLoader,
			modelRuntime,
			model,
			thinkingLevel,
		});
		// Pi requires every tool to terminate a mixed batch, then AgentSession drains queued nudges.
		// Preserve its boundary; a persisted final review also ends this isolated session's internal queue.
		const { finishTurn } = reviewSession.agent;
		reviewSession.agent.finishTurn = async (turn, signal) => {
			const decision = await finishTurn?.(turn, signal);
			if (state.final) {
				reviewSession.clearQueue();
				return { action: "end" };
			}
			return decision ?? undefined;
		};
		const unsubscribeReview = subscribeSession(reviewSession);
		const unsubscribeContext = reviewSession.subscribe((event) => {
			if (event.type === "compaction_end" && !event.aborted && !hasText(event.errorMessage)) {
				state.reviewContext = "LOST";
			} else if (event.type === "turn_start" && state.reviewContext === "RESTORING") {
				// Pi begins the next model turn after the current tool batch has ended: only then can the model read
				// the reference that a refusal restored.
				state.reviewContext = "HELD";
			}
		});
		activeSession = reviewSession;
		composerTool = "report_review";
		// Admission's own rows: the projection the review rests on drops the digests a source is checked against.
		const repository = nodePath.resolve(CWD, taskEnvelope.paths.repositoryRoot);
		const primarySource = buildPrimarySourceReference(
			CWD,
			taskEnvelope.paths.contextRoot,
			taskEnvelope.paths.repositoryRoot,
			folderIndex,
			admittedResults,
			{
				blob: (revision, file, limit) => readPinnedBlob(repository, revision, file, limit),
				diff: (base, head, limit) => pinnedDiff(repository, base, head, limit),
				checkedOut: () => checkedOutCommit(repository),
			},
		);
		const text = buildReviewTurn({
			sameWork: [
				buildSameWorkContext(CWD, taskEnvelope.paths.contextRoot, folderIndex, framing),
				primarySource,
			]
				.filter((part) => part !== "")
				.join("\n\n"),
			results: reviewable,
			qualifications: publicQualifications(admittedResults),
			alreadySaid: alreadySaid.feedback,
			ownHistoryOmissions: alreadySaid.omissions,
			ownHistoryReadAt: read.readAt,
			captured,
			practices,
			notReached: notReachedSlugs,
			lineNotes,
			stagedCriteria: criteriaFileOf,
		});
		// The composition owes one final review, whatever it decides: a review that withholds every result is final only when sent.
		const owed = () => (state.final ? 0 : Math.max(1, undecidedByReview(reviewable).length));
		const budget = compositionBudget(owed());
		let started = false;
		const wasStarted = () => started;
		const trace = openTurnTrace("review composition", budget, { owed, nudge: REVIEW_NUDGE });
		let finalTrace = trace;
		try {
			try {
				if (safety.expired()) {
					trace.stoppedBy = "safety";
					throw new Error("the run reached its safety ceiling before the review was due");
				}
				await Promise.race([
					(async () => {
						const prepared = await prepareTurnText(reviewSession, () => text);
						if (safety.expired()) {
							return;
						}
						if (prepared === null) {
							throw new Error("essential review input exceeds the context window");
						}
						await reviewSession.prompt(prepared, {
							preflightResult: (disposition) => {
								if (disposition === "started") {
									started = true;
									state.reviewContext = "HELD";
								}
							},
						});
					})(),
					safety.elapsed,
				]);
			} catch (error) {
				trace.runtimeError = trace.stoppedBy === null && !safety.expired();
				console.error(`[pi-runner] review composition failed: ${errorText(error)}`);
			} finally {
				if (trace.stoppedBy !== null) {
					await settleSession(reviewSession, "review composition", ABORT_SETTLE_MS);
				}
				closeTurnTrace(trace);
			}
			const left = undecidedByReview(reviewable);
			if (
				wasStarted() &&
				!trace.modelError &&
				!trace.runtimeError &&
				(trace.stoppedBy === null || trace.stoppedBy === "loop") &&
				!state.final &&
				(await settleSession(reviewSession, "review composition", ABORT_SETTLE_MS)) &&
				!safety.expired()
			) {
				console.error(
					left.length > 0
						? `[pi-runner] the review left ${left.length} NOT_MET result(s) undecided — asking once more`
						: "[pi-runner] the review is not final — asking once more",
				);
				const retry = openTurnTrace("review composition once more", budget, {
					owed,
					nudge: REVIEW_NUDGE,
					endsWhenPaid: true,
				});
				finalTrace = retry;
				try {
					await Promise.race([
						(async () => {
							// Decided when the prompt is prepared, which may compact: a prompt that follows a compaction
							// carries the reference again.
							const prepared = await prepareTurnText(
								reviewSession,
								() =>
									`${state.reviewContext === "HELD" ? "" : `${text}\n\n`}${finishReviewText(left)}`,
							);
							if (safety.expired()) {
								return;
							}
							if (prepared === null) {
								throw new Error("essential review input exceeds the context window");
							}
							await reviewSession.prompt(prepared, {
								preflightResult: (disposition) => {
									if (disposition === "started") {
										state.reviewContext = "HELD";
									}
								},
							});
						})(),
						safety.elapsed,
					]);
				} catch (error) {
					retry.runtimeError = retry.stoppedBy === null && !safety.expired();
					console.error(`[pi-runner] review composition failed: ${errorText(error)}`);
				} finally {
					if (retry.stoppedBy !== null) {
						await settleSession(reviewSession, "review composition", ABORT_SETTLE_MS);
					}
					closeTurnTrace(retry);
				}
			}
		} finally {
			try {
				await stopBeforeSwitching(reviewSession);
				if (!state.final) {
					recordCompositionFailure(
						"PUBLIC_REVIEW",
						incompleteCompositionReason(finalTrace, safety.expired()),
					);
				}
				persistComposedFeedback();
			} finally {
				unsubscribeReview();
				unsubscribeContext();
			}
			activeSession = null;
			composerTool = "report_feedback";
		}
	}

	/**
	 * The private lanes, in a fresh session opened after measurement ended: it receives the task, the brief, the
	 * admitted results with their observations and the person's authorized history, reads with the evidence tools, and records only
	 * with report_feedback. No measuring transcript is in its context.
	 */
	async function composePrivately(
		request: CompositionRequest,
		safety: ReturnType<typeof scheduleDeadline>,
	): Promise<void> {
		if (!PRIVATE_CHANNELS.some((channel) => request.channels[channel].enabled)) {
			return;
		}
		const feedbackTool = buildFeedbackTool(
			composablePracticeSlugs(),
			request,
			admittedResults,
			stagedPreparedTargets(),
			stagedDeliveredPractices(),
		);
		const privateLoader = new DefaultResourceLoader({
			cwd: CWD,
			agentDir: AGENT_DIR,
			settingsManager,
			...SANDBOX_RESOURCE_LOADER_OPTIONS,
			systemPrompt: `${readFileSync(FEEDBACK_STYLE_PATH, "utf8")}\n\n${readFileSync(COMPOSER_PROMPT_PATH, "utf8")}`,
			agentsFilesOverride: () => ({ agentsFiles: [] }),
			extensionFactories: [createCodemodeExtension({ mode: "on", models: false })],
		});
		await privateLoader.reload();
		composerTool = "report_feedback";
		// A lane closed after admission (closeUnroutedPrivateLanes) leaves only the lanes still open.
		{
			currentTurnSlugs = [];
			const opened = await openSession(
				"private-feedback",
				[...PRACTICE_TOOLS, "report_feedback"],
				[feedbackTool],
				privateLoader,
			);
			const { session } = opened;
			let finalTrace: TurnTrace;
			try {
				finalTrace = await composeInto(session);
			} finally {
				await closeSession(opened);
			}
			if (undecidedPrivatePractices().length > 0) {
				recordCompositionFailure(
					"PRIVATE_FEEDBACK",
					incompleteCompositionReason(finalTrace, safety.expired()),
				);
			}
		}

		async function composeInto(session: AgentSession): Promise<TurnTrace> {
			const undecided = undecidedPrivatePractices;
			let started = false;
			const wasStarted = () => started;
			const trace = openTurnTrace("composition", compositionBudget(privatelyComposable().length), {
				owed: () => undecided().length,
				nudge: COMPOSITION_NUDGE,
			});
			let finalTrace = trace;
			try {
				if (safety.expired()) {
					trace.stoppedBy = "safety";
					throw new Error("the run reached its safety ceiling before composition was due");
				}
				const compositionTurn = buildCompositionTurn(request, admittedResults, notReached);
				await Promise.race([
					(async () => {
						const compositionText = await prepareTurnText(
							session,
							() => `${openingIfNeeded(brief, true)}${compositionTurn}`,
						);
						if (safety.expired()) {
							return;
						}
						if (compositionText === null) {
							throw new Error("essential composition input exceeds the context window");
						}
						await session.prompt(compositionText, {
							preflightResult: (disposition) => {
								if (disposition === "started") {
									openingInContext = true;
									started = true;
								}
							},
						});
					})(),
					safety.elapsed,
				]);
			} catch (error) {
				trace.runtimeError = trace.stoppedBy === null && !safety.expired();
				console.error(`[pi-runner] composition failed: ${errorText(error)}`);
			} finally {
				if (trace.stoppedBy !== null) {
					await settleSession(session, "composition", ABORT_SETTLE_MS);
				}
				closeTurnTrace(trace);
			}
			// A composer that ended on its own, or was cut off by a loop guard, is asked once more for the
			// NOT_MET practices it left undecided; otherwise they have no composed next step.
			const left = undecided();
			if (
				wasStarted() &&
				!trace.modelError &&
				!trace.runtimeError &&
				(trace.stoppedBy === null || trace.stoppedBy === "loop") &&
				left.length > 0 &&
				!safety.expired()
			) {
				finalTrace = await askComposerOnceMore(
					session,
					left,
					undecided,
					request,
					() =>
						openingInContext
							? ""
							: `${openingIfNeeded(brief, true)}${buildCompositionTurn(request, admittedResults, notReached)}`,
					safety,
				);
			}
			return finalTrace;
		}
	}
}

function finalizeOutputQuietly() {
	try {
		finalizeOutput();
	} catch (error) {
		console.error(`[pi-runner] output could not be finalized: ${errorText(error)}`);
	}
}

process.on("uncaughtException", (err) => {
	console.error(`[pi-runner] FATAL: ${errorText(err)}`);
	finalizeOutputQuietly();
	process.exit(2);
});

process.on("unhandledRejection", (reason) => {
	console.error(`[pi-runner] UNHANDLED REJECTION: ${errorText(reason)}`);
	finalizeOutputQuietly();
	process.exit(2);
});

async function run(): Promise<void> {
	try {
		await main();
	} catch (error) {
		console.error(
			`[pi-runner] FATAL: ${errorText(error)}\n${error instanceof Error ? (error.stack ?? "") : ""}`,
		);
		unreachedReason = "error";
		finalizeOutputQuietly();
		// Preserve the retryable exit code for admission transport failures.
		process.exit(error instanceof AdmissionUnreachableError ? SERVER_UNREACHABLE_EXIT : 2);
	}
}

void run();
