import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import nodePath from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import { isDeepStrictEqual } from "node:util";
import { DIFF_SOURCE, TREE_SOURCE } from "./work/practice-scripts/kits/scm/sources.ts";

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
import {
	prepareFeedback,
	type FeedbackComposition,
	type FeedbackPreparationReceipt,
} from "./pi-feedback-preparation.ts";
import { folderCitationIndex, citableTextIndex } from "./pi-folder-index.ts";
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
	trimmedText,
	normalizeSubmission,
	resolveQuote,
	resolveCapturedText,
	submissionCitations,
	validateEvidenceSources,
	validateInapplicabilityScope,
	validateSearchScope,
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
	DESTINATIONS,
	type ComposedFeedbackEnvelope,
	type ComposedFeedbackPiece,
	type ComposedReview,
	type EvidenceRef,
	type PracticePageSupport,
	type ConversationEligibility,
	PRIOR_ADVICE_REASONS,
	PRIVATE_DESTINATIONS,
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
	readPracticePageSupport,
	readConversationEligibility,
	readReview,
	prepareReviewArguments,
	reviewToolParameters,
	undeliverableFeedback,
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
import { hasText, isBlank, isRecord } from "./pi-text.ts";
import { prepareResultArguments } from "./pi-tool-arguments.ts";
import { prepareTurnText } from "./pi-turn-context.ts";
import { parseDiff } from "./work/practice-scripts/kits/scm/diff-parser.ts";
import type { DiffFile } from "./work/practice-scripts/kits/scm/types.ts";
import { MODEL_CONCURRENCY } from "./work/practice-scripts/lib/core/concurrency.ts";
import {
	boundModelsSchema,
	CANDIDATE_LIMITS,
	compositionRequestSchema,
	OUTCOMES,
	parseCandidate,
	SEVERITIES,
	type BoundModels,
	type Candidate,
	type Citation,
	type CitationIdentity,
	type Coverage,
	type Omission,
	type Outcome,
	type PracticeInputs,
	isDecimalId,
	type ReadReceipt,
	validateCitationCapabilities,
	type ReviewOptions,
} from "./work/practice-scripts/lib/core/contract.ts";
import {
	EvidenceReader,
	noReceipts,
	type EvidenceSources,
	type Receipts,
} from "./work/practice-scripts/lib/core/evidence-reader.ts";
import type { JobFiles } from "./work/practice-scripts/lib/core/ipc.ts";
import { renderReviewInput, resolveLeads } from "./work/practice-scripts/lib/core/leads.ts";
import { hostModels, ModelBudget } from "./work/practice-scripts/lib/core/models-host.ts";
import {
	UnratedError,
	type UnratedReason,
	z,
} from "./work/practice-scripts/lib/core/practice-script.ts";
import {
	notReached as notReachedOutcome,
	runPracticeProgram,
	type PracticeProgram,
	type ProgramOutcome,
} from "./work/practice-scripts/lib/core/program-host.ts";
import {
	practiceIndexSchema,
	runtimeIdSchema,
} from "./work/practice-scripts/lib/core/runtime-inputs.ts";
import { InputReader } from "./work/practice-scripts/lib/core/script-inputs.ts";
import { abortReasonOf, RETURN_MS } from "./work/practice-scripts/lib/core/script-run.ts";

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

/** The list, or the one item that was sent where a list of them was asked for. */
function listOrItem(value: unknown): unknown[] {
	if (Array.isArray(value)) {
		return value;
	}
	return isRecord(value) ? [value] : [];
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

/** The task this runner was started for. */
interface TaskEnvelope {
	schemaVersion: number;
	reviewId: string;
	jobId: string;
	attempt: number;
	workspaceId: string;
	paths: ReturnType<typeof taskPaths>;
	prompt: string;
	repositoryFullName: unknown;
	pullRequestNumber: unknown;
}

interface DestinationBounds {
	enabled: boolean;
	maxPieces: number;
}

type CopyForm = "LINE_NOTE" | "FILE_NOTE";

/** The task-declared composition request, once its bounds have been clamped to what this run may do. */
interface CompositionRequest {
	destinations: Record<Destination, DestinationBounds>;
	onWorkCopyForms: CopyForm[];
	minDistinctItems: number;
}

interface AdmittedCitation {
	index: number;
	[key: string]: unknown;
}

interface AdmittedChild {
	problemId: string;
	citations: AdmittedCitation[];
	[key: string]: unknown;
}

/**
 * One admitted result as admission projected it: one practice's verdict and its problems. Composition rests on a
 * result by its resultId and on one problem by its problemId. A result's own citations never anchor a
 * note; a problem's may. Validate consumed fields; preserve other server fields for the composer.
 */
interface AdmittedResult {
	resultId: string;
	practiceSlug: string;
	outcome: Outcome;
	citations: AdmittedCitation[];
	problems: AdmittedChild[];
	[key: string]: unknown;
}

function isAdmittedCitation(value: unknown): value is AdmittedCitation {
	return isRecord(value) && typeof value.index === "number";
}

/** The server sets every one of these; a missing one stops the runner before it does any work. */
function requiredEnv(name: string): string {
	const value = process.env[name];
	if (!hasText(value)) {
		throw new Error(`${name} env var is required`);
	}
	return value;
}

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

interface TurnTrace {
	phase: SessionPhase;
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

function compareSlugs(a: string, b: string): number {
	if (a === b) {
		return 0;
	}
	return a < b ? -1 : 1;
}

interface Validated {
	candidate: Candidate;
	notes: string[];
}

function incompleteCoverageText(coverage: Coverage): string {
	const omitted = coverage.complete
		? []
		: coverage.omitted.map(
				(omission) =>
					`${omission.count} ${omission.what}${omission.items === undefined ? "" : ` (${omission.items.join(", ")})`}`,
			);
	return (
		`a MET result needs complete coverage, and this session's reads did not complete: ${omitted.join("; ")}. ` +
		"Read the rest of what was cut short, with an offset or a narrower search, and submit again; or submit what the " +
		"evidence supports: NOT_MET for a problem it proves, or COULD_NOT_DETERMINE with the open question"
	);
}

function validateDomainBasis(
	submission: NormalizedSubmission,
	assignment: PracticeInputs | undefined,
): void {
	const scan = assignment?.scope.kind === "SCAN";

	for (const problem of submission.problems ?? []) {
		if (scan && problem.basis.type !== "state") {
			throw new Error("basis_not_allowed: a scan needs state basis");
		}
		if (!scan && assignment?.kind === "scm.pull_request" && problem.basis.type === "state") {
			throw new Error(
				"state_basis_not_allowed: an ITEM change needs introduced, worsened or duty basis",
			);
		}
	}
}

/** Infer an omitted diff side by exact quote match, preferring NEW; never override a supplied side. */
function resolveOnEitherSide(citation: Citation, content: string): ReturnType<typeof resolveQuote> {
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

/** A draft disposition; its practice slug is its stable reference within this review. */
type Recorded =
	| (({ kind: "stored" } | { kind: "revised" }) & {
			slug: string;
			negative: boolean;
			filled: string[];
	  })
	| { kind: "duplicate"; slug: string }
	| { kind: "refused"; slug: string; reason: string };

interface TurnDemand {
	owed: () => number;
	nudge: string;
	endsWhenPaid?: true;
}

function problemsIn(result: unknown): number {
	const details = isRecord(result) ? result.details : undefined;
	if (!isRecord(details)) {
		return 0;
	}
	return [details.inserted, details.duplicates, details.refused]
		.map((count) => (typeof count === "number" ? count : 0))
		.reduce((sum, count) => sum + count, 0);
}

/**
 * What one review session's evidence reads reached, from the host's own receipts of its tools: every read,
 * the reads its tool cut short and the session did not read on from, and the reads that failed.
 */
interface SessionReads {
	reads: number;
	cutFiles: Set<string>;
	cutSearches: string[];
	failed: string[];
	pending: Map<string, unknown>;
}

function newSessionReads(): SessionReads {
	return { reads: 0, cutFiles: new Set(), cutSearches: [], failed: [], pending: new Map() };
}

function sessionReceipts(reads: SessionReads): Receipts {
	return {
		reads: reads.reads,
		truncatedReads: reads.cutFiles.size + reads.cutSearches.length,
		failedReads: reads.failed.length,
	};
}

// Each review owns its draft; only the script's final return becomes a candidate.
interface ActiveReview {
	slug: string;
	draft: Candidate | null;
	reads: SessionReads;
	reader?: EvidenceReader;
	domainReads: Receipts;
}

interface SubmitResultDetails {
	inserted: number;
	revised: number;
	duplicates: number;
	refused: number;
}

function governedScanRead(reader: EvidenceReader) {
	return defineTool({
		name: "read",
		label: "Read pinned file",
		description:
			"Read a captured repository file at the pinned commit. Files are context for the assigned file only.",
		parameters: {
			type: "object",
			additionalProperties: false,
			required: ["path"],
			properties: {
				path: { type: "string" },
				offset: { type: "integer", minimum: 1 },
				limit: { type: "integer", minimum: 1 },
			},
		},
		execute: async (_id, params: { path: string; offset?: number; limit?: number }, signal) => {
			const read = await reader.answer(
				{
					op: "read",
					path: params.path,
					...(params.offset === undefined ? {} : { offsetLine: params.offset }),
					...(params.limit === undefined ? {} : { maxLines: params.limit }),
				},
				signal,
			);
			return {
				content: [
					{
						type: "text" as const,
						text: read.status === "FOUND" ? read.text : `${params.path}: ${read.status}`,
					},
				],
				details: {
					status: read.status,
					...(read.status === "FOUND" ? { truncated: read.truncated } : {}),
				},
			};
		},
	});
}

function recordOnceMoreText(slug: string): string {
	return (
		`## Not recorded\nThis session ended without a result for ${slug}: text outside a ` +
		`submit_result call records nothing. Submit the outcome its criterion and the evidence you read ` +
		`support, in one submit_result call. If a required source is missing, truncated or blocked, that ` +
		`is a collection gap: submit nothing.`
	);
}

type Destination = (typeof DESTINATIONS)[number];

type PrivateDestination = (typeof PRIVATE_DESTINATIONS)[number];

type FeedbackAction = (typeof ACTIONS)[number];

function isDestination(value: unknown): value is Destination {
	return DESTINATIONS.some((destination) => destination === value);
}

function optionalString(value: unknown): string | undefined {
	return typeof value === "string" ? value : undefined;
}

interface ConversationNotes {
	situation?: string;
	capability?: string;
	evidenceSummary?: string;
	inConversationLearning?: string;
	alreadySaid?: string;
}

/**
 * A private-destination piece; per-piece validation in readFeedbackPiece/validateFeedback. As read, basedOn holds the ids the
 * composer named; as stored, the references they resolve to.
 */
interface FeedbackPieceOf<Basis> extends ComposedFeedbackPiece {
	destination: PrivateDestination;
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

type ReadFeedbackPiece = FeedbackPieceOf<string[]>;

type FeedbackPiece = FeedbackPieceOf<EvidenceRef[]>;

function feedbackBody(piece: Pick<FeedbackPiece, "title" | "body" | "nextStep" | "notes">): string {
	const sections = [piece.title, piece.body, piece.nextStep];
	for (const [field, label] of [
		["situation", "Situation"],
		["capability", "Capability"],
		["evidenceSummary", "Evidence"],
		["inConversationLearning", "In conversation"],
		["alreadySaid", "Earlier feedback"],
	] as const) {
		const text = piece.notes?.[field];
		if (hasText(text)) {
			sections.push(`${label}: ${text}`);
		}
	}
	return sections.filter(hasText).join("\n\n");
}

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
	resultId: string;
	practiceSlug: string;
	outcome: Outcome;
	citations: LeanCitation[];
	problems: {
		problemId: string;
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
	feedback: FeedbackPiece[];
	review: ComposedReview | null;
	preparation: FeedbackPreparationReceipt | null;
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

function resultsById(results: readonly AdmittedResult[]): Map<string, AdmittedResult> {
	return new Map(results.map((result) => [result.resultId, result]));
}

// The composer references admitted results and problems; it cannot author verdicts, citations, or locations.
interface ReportFeedbackDetails {
	stored: number;
	destination?: Destination;
	total?: number;
}

function skipped(text: string): { stored: boolean; text: string } {
	return { stored: false, text };
}

interface ReportReviewDetails {
	stored: number;
}

// A completed response can contain more calls; final composition must reject all subsequent writes.
interface PublicReviewState {
	final: boolean;
	reviewContext: "LOST" | "RESTORING" | "HELD";
}

function finishReviewText(undecided: readonly string[]): string {
	const owed =
		undecided.length > 0
			? `## Undecided\nThe review leaves these NOT_MET results undecided: ${undecided.join(", ")}.`
			: "## Unfinished\nThe review on this work is not final yet.";
	return `${owed}\nStore the final review with one report_review call. A review that says nothing is still one final report_review call. No prose outside the calls.`;
}

function validateConversationPiece(piece: ReadFeedbackPiece): string | null {
	if (hasText(piece.body) || hasText(piece.nextStep)) {
		return "CONVERSATION takes notes{situation,capability,evidenceSummary,inConversationLearning}, not body/nextStep - nothing for this destination is read out; skipped.";
	}
	const { notes } = piece;
	for (const field of [
		"situation",
		"capability",
		"evidenceSummary",
		"inConversationLearning",
	] as const) {
		if (isBlank(notes?.[field])) {
			return `CONVERSATION needs notes.${field}; skipped.`;
		}
	}
	return null;
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

function coordinatesOf(citation: unknown): unknown {
	if (!isRecord(citation)) {
		return citation;
	}
	const { quote: _quote, verification: _verification, ...coordinates } = citation;
	return coordinates;
}

/** A staged file as a titled block of the composition turn, or a pointer to it when it is too large. */
const COMPOSITION_BLOCK_LIMIT = 32_000;

function stagedBlock(label: string, file: string, limit = COMPOSITION_BLOCK_LIMIT): string {
	if (!existsSync(file)) {
		return "";
	}
	const text = readFileSync(file, "utf8").trim();
	return text.length > limit
		? `### ${label} — too large to show here; read \`${file}\`\n`
		: `### ${label}\n\`\`\`json\n${text}\n\`\`\`\n`;
}

/** The cause behind a wrapped error, in parentheses, or nothing when the error carries none. */
function causeText(error: unknown): string {
	return error instanceof Error && error.cause !== undefined ? ` (${errorText(error.cause)})` : "";
}

async function answerText(response: Response): Promise<string> {
	const problem: unknown = await response.json().catch(() => null);
	const detail = isRecord(problem) ? problem.detail : null;
	return typeof detail === "string" && detail.length > 0
		? detail
		: "the server gave no problem detail";
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

function preparedSectionOf(slug: string, section: string): string {
	const shown = section.trim();
	return shown
		? `\n\n#### Leads from the practice script for \`${slug}\` — starting points to check against the criteria, not verdicts\n${shown}`
		: "";
}

function notMetPractices(results: readonly { practiceSlug: string; outcome: Outcome }[]): string[] {
	return [
		...new Set(
			results.filter((result) => result.outcome === "NOT_MET").map((result) => result.practiceSlug),
		),
	].toSorted();
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

export async function runReviewJob(): Promise<void> {
	/** Accept array, single-item and serialized-list forms; validate each parsed item separately. */
	function submittedList(value: unknown): { items: unknown[] } | { error: string } {
		if (typeof value !== "string") {
			return { items: listOrItem(value) };
		}
		try {
			return { items: listOrItem(JSON.parse(value)) };
		} catch (error) {
			const at = /position (?<position>\d+)/u.exec(errorText(error))?.groups?.position;
			const near =
				at === undefined
					? ""
					: `; it breaks here: ${JSON.stringify(value.slice(Math.max(0, Number(at) - 60), Number(at)))} ⟵ ${JSON.stringify(value.slice(Number(at), Number(at) + 20))}`;
			return {
				error: `the list arrived as a string that is not valid JSON (${errorText(error)})${near}. Fix that spot — usually a quote inside a text left unescaped — or send the array itself`,
			};
		}
	}

	function isAdmittedChild(value: unknown): value is AdmittedChild {
		return (
			isRecord(value) &&
			typeof value.problemId === "string" &&
			value.problemId !== "" &&
			Array.isArray(value.citations) &&
			value.citations.every(isAdmittedCitation)
		);
	}

	function admittedResultOf(value: unknown): AdmittedResult {
		if (
			!isRecord(value) ||
			!isRecord(value.evidence) ||
			!Array.isArray(value.evidence.citations) ||
			!Array.isArray(value.problems)
		) {
			throw new Error("invalid admitted result DTO");
		}
		const { evidence, problems, ...fields } = value;
		const projected = {
			...fields,
			evidence,
			citations: value.evidence.citations.map((citation, index) => {
				if (!isRecord(citation)) {
					throw new Error("invalid result citation DTO");
				}
				return { ...citation, index };
			}),
			problems: problems.map((problem) => {
				if (!isRecord(problem) || typeof problem.id !== "string" || problem.id === "") {
					throw new Error("invalid admitted problem DTO");
				}
				const { id, ...rest } = problem;
				return { ...rest, problemId: id };
			}),
		};
		if (!isAdmittedResult(projected)) {
			throw new Error("result admission returned an invalid contract");
		}
		return projected;
	}

	function isAdmittedResult(value: unknown): value is AdmittedResult {
		return (
			isRecord(value) &&
			typeof value.resultId === "string" &&
			value.resultId !== "" &&
			typeof value.practiceSlug === "string" &&
			OUTCOMES.some((outcome) => outcome === value.outcome) &&
			Array.isArray(value.citations) &&
			value.citations.every(isAdmittedCitation) &&
			Array.isArray(value.problems) &&
			value.problems.every(isAdmittedChild) &&
			(value.outcome === "NOT_MET") === value.problems.length > 0
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
	const PRACTICE_SCRIPT_REPORT_PATH = outputPath(OUTPUT, "practice-scripts.json");
	const PRACTICE_SCRIPT_MODELS_PATH = `${CWD}/practice-script-models.json`;
	/** Each review's whole input, for the session to read beyond its bounded section; never composition's. */
	const REVIEW_INPUT_ROOT = "work/review-input";
	/** The runner's own record of what this review has recorded so far; read back after a compaction. */
	const NOTES_PATH = `${CWD}/work/notes/review.md`;

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
	function readPracticeIndex() {
		return practiceIndexSchema.parse(JSON.parse(readFileSync(INPUT_PATHS.practiceIndex, "utf8")));
	}

	const folderIndex: unknown = JSON.parse(readFileSync(INPUT_PATHS.manifest, "utf8"));
	const { availableSourceKinds, sourceFileSources, citationCapabilities } =
		folderCitationIndex(folderIndex);
	const capturedTexts = citableTextIndex(folderIndex);
	const availableSourceKindValues = [...availableSourceKinds].toSorted();
	const practiceIndex = readPracticeIndex();
	const admittedPractices = new Set(practiceIndex.map((practice) => practice.slug));
	// ABSENT is sound only over sources the practice declares exhaustive.
	const practiceExhaustiveSources = new Map(
		practiceIndex.map((practice) => [practice.slug, new Set(practice.exhaustiveSources)]),
	);

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

	const UNRATED_BY_STOP: Readonly<Record<StopReason, UnratedReason>> = {
		budget: "budget",
		safety: "deadline",
		stall: "unavailable",
		loop: "off-format",
	};

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
	const reviewState = new Map<string, Candidate>();
	const executions = new Map<string, ProgramOutcome>();
	let unreachedReason: "deadline" | "budget" | "error" = "deadline";

	const assignedInputs = new Map<string, PracticeInputs>();
	const assignmentReceipts = new Map<string, readonly ReadReceipt[]>();
	function admissionRequest() {
		return {
			contractVersion: 3,
			reviewId: taskEnvelope.reviewId,
			jobId: taskEnvelope.jobId,
			attempt: taskEnvelope.attempt,
			results: [...reviewState]
				.toSorted(([a], [b]) => compareSlugs(a, b))
				.map(([practiceSlug, candidate]) => {
					const assigned = assignedInputs.get(practiceSlug);
					if (assigned === undefined) {
						throw new Error(`no frozen assignment for ${practiceSlug}`);
					}
					return {
						practiceSlug,
						itemId: assigned.scope.item.id,
						itemVersion: assigned.scope.version,
						candidate,
						readReceipts: assignmentReceipts.get(practiceSlug) ?? [],
					};
				}),
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
		required: ["consulted", "prerequisite", "ruledOutBy"],
		properties: {
			consulted: {
				type: "array",
				minItems: 1,
				items: { type: "string", enum: availableSourceKindValues },
				description:
					"Evidence source kinds you read to reach this conclusion, e.g. scm.pull-request.diff.",
			},
			prerequisite: {
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
		required: ["sourceKind", "sourcePath", "path", "startLine"],
		properties: {
			sourceKind: { type: "string", enum: availableSourceKindValues },
			// Validate source membership at submission; large enums can exceed the SDK schema compiler's capacity.
			sourcePath: {
				type: "string",
				minLength: 1,
				description:
					"The captured sourceFile the citation reads, exactly as the capture manifest names it.",
			},
			path: { type: "string", minLength: 1 },
			side: {
				type: ["string", "null"],
				enum: ["OLD", "NEW", null],
				description:
					"For a quote of the change: sourcePath names the pinned change (change.json), path is the file as named on that side, and the lines are the [L<n>] coordinates of work/change/diff.patch.",
			},
			revision: {
				type: ["string", "null"],
				minLength: 1,
				maxLength: 1024,
				description:
					"For repository text: sourcePath names the captured .git/HEAD, path is repository-relative, and revision optionally selects a full commit SHA; omission means the captured HEAD.",
			},
			startLine: {
				type: "integer",
				minimum: 1,
				maximum: 2_147_483_647,
				description:
					"The 1-based line of the quoted text in the sourceFile; for a quote of the change, the [L<n>] coordinate of work/change/diff.patch.",
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
					"this criterion's subject and applicability, then, when it does, how the cited evidence meets or falls " +
					"short of what this criterion judges, leaving to other practices what it assigns to them. Describe " +
					"evidence, not advice, intent, confidence, or hidden chain-of-thought.",
			},
			citations: {
				type: "array",
				items: citationSchema,
				description:
					"The lines that decide the outcome. Required for MET, DOES_NOT_APPLY and COULD_NOT_DETERMINE; a NOT_MET result may leave it empty, since each problem cites its own lines.",
			},
			problems: {
				anyOf: [{ type: "array", minItems: 1, items: problemSchema }, { type: "null" }],
				description:
					"NOT_MET only, and required there: each problem the evidence shows, each with its own severity, citations and basis. null for every other outcome.",
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
					"The warrant the outcome owes, and nothing else: search for a MET or NOT_MET or COULD_NOT_DETERMINE that rests on something " +
					"missing — what you looked for, where, and what the search did not cover; inapplicability for DOES_NOT_APPLY; " +
					"openQuestions, a list of open questions, for COULD_NOT_DETERMINE and, beside its problems, for a NOT_MET. Use null for " +
					"branches that do not apply.",
				properties: {
					search: { anyOf: [searchSchema, { type: "null" }] },
					inapplicability: { anyOf: [inapplicabilitySchema, { type: "null" }] },
					openQuestions: {
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

	const continuing = existsSync(CONTINUATION_PATH);

	function maybeWriteResultFile(): boolean {
		if (!continuing && reviewState.size === 0) {
			return false;
		}
		writeFileSync(RESULT_PATH, JSON.stringify(admissionRequest(), null, 2));
		return true;
	}

	function programOf(slug: string): PracticeProgram {
		const file = nodePath.join(INPUT_PATHS.practiceScripts, `${slug}.ts`);
		return existsSync(file)
			? { script: { path: file, source: readFileSync(file) } }
			: { script: null };
	}

	function persistExecutionReport(): void {
		const entries = practiceIndex.map(
			({ slug }) =>
				[
					slug,
					executions.get(slug) ?? notReachedOutcome(programOf(slug), unreachedReason),
				] as const,
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

	function validateConversationAttribution(
		submission: NormalizedSubmission,
		assignment: PracticeInputs | undefined,
	): void {
		const thread = `${CWD}/${CONVERSATION_THREAD}`;
		if (
			submission.outcome === "NOT_MET" &&
			assignment?.kind === "chat.thread" &&
			assignment.about !== null &&
			existsSync(thread)
		) {
			const turns = readFileSync(thread, "utf8");
			const { developerId } = assignment.about;
			if (
				(submission.problems ?? []).some(
					(problem) => !citesReviewedTurn(problem.citations, turns, developerId),
				)
			) {
				throw new Error(
					`each problem of a NOT_MET of this conversation review must cite a turn of the participant under review: quote a ` +
						`line inside a turn of ${CONVERSATION_THREAD} whose accountId equals about.developerId. The other turns are ` +
						"context, and a lapse that only they show is not this participant's",
				);
			}
		}
	}

	function validateSubmission(raw: unknown, slug: string, coverage: Coverage): Validated {
		const notes: string[] = [];
		const submission = normalizeSubmission(raw, notes, (sourceFile) =>
			sourceFileSources.get(sourceFile),
		);
		const citations = submissionCitations(submission);
		const assignmentScope = assignedInputs.get(slug)?.scope;
		if (assignmentScope?.kind === "SCAN") {
			const contextPaths = practiceIndex.find((practice) => practice.slug === slug)?.contextPaths;
			const allowed = new Set([assignmentScope.item.path, ...(contextPaths ?? [])]);
			if (citations.some((citation) => !allowed.has(citation.path))) {
				throw new Error("a scan citation is outside its frozen context");
			}
		}

		for (const citation of citations) {
			const stagedAtPath = sourceFileSources.get(citation.path);
			if (stagedAtPath !== undefined && citation.sourcePath !== citation.path) {
				notes.push(
					`${citation.path} is a source file of its own, staged by ${stagedAtPath}; recorded against it, not ${citation.sourcePath}`,
				);
				citation.sourcePath = citation.path;
				citation.sourceKind = stagedAtPath;
			}
			const staged = sourceFileSources.get(citation.sourcePath);
			if (staged !== undefined && staged !== citation.sourceKind) {
				notes.push(
					`${citation.sourcePath} is staged by ${staged}, not ${citation.sourceKind}; recorded as ${staged}`,
				);
				citation.sourceKind = staged;
			}
			const flags = citationCapabilities.get(citation.sourceKind);
			if (flags === undefined) {
				throw new Error(
					`the source catalog declares no citation capabilities for ${citation.sourceKind}`,
				);
			}
			const capabilityError = validateCitationCapabilities(citation, flags);
			if (capabilityError !== undefined) {
				throw new Error(capabilityError);
			}
		}
		validateEvidenceSources(
			submission,
			availableSourceKinds,
			sourceFileSources,
			`${taskEnvelope.paths.repositoryRoot.replace(/\/+$/u, "")}/`,
		);
		validateSearchScope(
			submission,
			practiceExhaustiveSources.get(slug) ?? new Set(),
			availableSourceKinds,
		);
		validateInapplicabilityScope(submission, availableSourceKinds);
		const assignment = assignedInputs.get(slug);
		const scan = assignment?.scope.kind === "SCAN";
		validateDomainBasis(submission, assignment);

		if (
			(submission.outcome === "DOES_NOT_APPLY" || submission.outcome === "COULD_NOT_DETERMINE") &&
			!scan &&
			availableSourceKinds.has(DIFF_SOURCE) &&
			practiceIndex
				.find((practice) => practice.slug === slug)
				?.readsSources.includes(DIFF_SOURCE) === true &&
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
			const registered = capturedText(citation);
			const resolved =
				registered === null
					? resolveCited(citation, citedContent(citation))
					: resolveCapturedText(citation, registered);
			if ("mismatch" in resolved) {
				throw new Error(
					`citation does not match ${citation.path}:${citation.startLine}-${citation.endLine} ` +
						`(${citation.side ?? "text"}) in '${citation.sourcePath}': ${resolved.mismatch}. Copy the exact ` +
						`sourceFile text and, for the change, the [L<n>] coordinates and OLD/NEW side of ${CHANGE_ROOT}/diff.patch ` +
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
		validateConversationAttribution(submission, assignment);
		const change = capturedChange();
		const parsed = parseCandidate(
			{ ...submission, coverage },
			(citation): CitationIdentity | undefined => {
				if (citation.sourceKind === TREE_SOURCE) {
					const repository = citationRepository(citation);
					const revision = citation.revision ?? checkedOutCommit(repository);
					return revision === null
						? undefined
						: { source: `${repository}:${citation.path}`, version: revision };
				}
				if (citation.sourceKind === DIFF_SOURCE && change !== null) {
					return {
						source: `${INPUT_PATHS.repositoryRoot}:${citation.path}`,
						version: citation.side === "OLD" ? change.base : change.head,
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

	/** What a repository read returns for a file that is not text: git's own rule, a NUL in the opening bytes. */
	const BINARY = Symbol("binary");

	function capturedText(citation: Citation): string | null {
		const matches = capturedTexts.filter(
			(entry) =>
				entry.source === citation.sourceKind &&
				entry.path === citation.path &&
				entry.side === (citation.side ?? "NEW") &&
				(citation.revision === undefined || entry.revision === citation.revision),
		);
		if (matches.length === 0) {
			return null;
		}
		if (matches.length !== 1) {
			throw new Error("citation names ambiguous captured text; specify its revision");
		}
		const proof = matches[0];
		if (proof === undefined) {
			throw new Error("the captured text proof is absent");
		}
		const bytes = readFileSync(nodePath.resolve(CWD, proof.stagedPath));
		if (createHash("sha256").update(bytes).digest("hex") !== proof.sha256) {
			throw new Error("the captured text digest changed");
		}
		return new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(bytes);
	}

	/** What a citation quotes: the checkout at HEAD or at the named revision, or the derived change view. */
	function citedContent(citation: Citation): string | typeof BINARY | null {
		if (citation.sourceKind === TREE_SOURCE) {
			if (citation.revision === undefined) {
				return readCheckoutFile(citation.path, citationRepository(citation));
			}
			const blob = readPinnedBlob(
				citationRepository(citation),
				citation.revision,
				citation.path,
				64 * 1024 * 1024,
			);
			return blob.kind === "regular" ? asText(blob.bytes) : null;
		}
		if (citation.sourceKind === DIFF_SOURCE) {
			return readFileSync(`${CWD}/${CHANGE_ROOT}/diff.patch`, "utf8");
		}
		return readFileSync(`${CWD}/${citation.sourcePath}`, "utf8");
	}

	/** The citation resolved against what it quotes, or the mismatch that says why it cannot be. */
	function resolveCited(
		citation: Citation,
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

	function citationRepository(citation: Citation): string {
		const match = /^repos\/(?<repository>[A-Za-z0-9_-]+)\/\.git\/HEAD$/u.exec(citation.sourcePath);
		if (!match?.groups || typeof match.groups.repository !== "string") {
			throw new Error("Repository citation requires a captured repository HEAD");
		}
		return nodePath.resolve(CWD, "repos", match.groups.repository);
	}

	/** The blob at a repository-relative path in a revision of the checkout's history, or null. */
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

	const RECORDING_TOOLS: ReadonlySet<string> = new Set([
		"submit_result",
		"report_feedback",
		"report_review",
	]);

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

	let turnDemand: TurnDemand | null = null;

	let tokensPerProblem = 1500;
	/** Output tokens of the last assistant message, when that message carries a recording call. */
	let recordingMessageTokens = 0;
	/** When the session last showed any sign of life; what the stall watchdog reads. */
	let lastEventAt = Date.now();

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
				shouldRecordNow(used, turn.budget, turnDemand.owed(), tokensPerProblem)
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
			const written = problemsIn(event.result);
			if (written > 0 && recordingMessageTokens > 0) {
				tokensPerProblem = Math.round((tokensPerProblem + recordingMessageTokens / written) / 2);
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
		console.error(
			`[pi-runner] ${turn.label}: a call ran into the output limit — telling the model`,
		);
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

	const READ_TOOLS: ReadonlySet<string> = new Set(["read", "grep", "find", "ls"]);
	const OMISSION_ITEMS = CANDIDATE_LIMITS.omissionItems;
	const ABSENT_PATH = /\bENOENT\b|^Path not found: /u;

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
			if (!ABSENT_PATH.test(toolResultText(result))) {
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
			// The tool cuts at its own limit with a truncation detail, and at the session's `limit` with only a
			// "more lines in file" notice. Only a read that reaches the end of the file clears an earlier cut.
			if (cut || /more lines in file/u.test(toolResultText(result))) {
				reads.cutFiles.add(target);
			} else {
				reads.cutFiles.delete(target);
			}
			return;
		}
		if (cut) {
			reads.cutSearches.push(named);
		}
	}

	function sessionCoverage(
		reads: SessionReads,
		reader?: EvidenceReader,
		domainReads: Receipts = noReceipts(),
	): Coverage {
		const native = sessionReceipts(reads);
		const receipts = {
			reads: native.reads + domainReads.reads,
			truncatedReads: native.truncatedReads + domainReads.truncatedReads,
			failedReads: native.failedReads + domainReads.failedReads,
		};
		const cut = [...reads.cutFiles, ...reads.cutSearches];
		const omitted: Omission[] = [
			...(cut.length > 0
				? [
						{
							what: "reads cut short at the tool's output limit",
							count: cut.length,
							reason: "truncated" as const,
							items: cut
								.slice(0, OMISSION_ITEMS)
								.map((piece) => piece.slice(0, CANDIDATE_LIMITS.warrantFieldChars)),
						},
					]
				: []),
			...(reads.failed.length > 0
				? [
						{
							what: "reads that failed",
							count: reads.failed.length,
							reason: "unavailable" as const,
							items: reads.failed
								.slice(0, OMISSION_ITEMS)
								.map((piece) => piece.slice(0, CANDIDATE_LIMITS.warrantFieldChars)),
						},
					]
				: []),
		];
		if (domainReads.truncatedReads > 0) {
			omitted.push({
				what: "governed file reads cut short",
				count: domainReads.truncatedReads,
				reason: "truncated",
			});
		}
		if (domainReads.failedReads > 0) {
			omitted.push({
				what: "governed file reads that failed",
				count: domainReads.failedReads,
				reason: "unavailable",
			});
		}
		const [first, ...rest] = omitted;
		return first === undefined
			? { source: "host", ...receipts, receipts: reader?.readReceipts ?? [], complete: true }
			: {
					source: "host",
					...receipts,
					receipts: reader?.readReceipts ?? [],
					complete: false,
					omitted: [first, ...rest],
				};
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
			validated = validateSubmission(
				raw,
				slug,
				sessionCoverage(review.reads, review.reader, review.domainReads),
			);
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

	function persistRecordedNotes(): void {
		try {
			mkdirSync(nodePath.dirname(NOTES_PATH), { recursive: true });
			writeFileSync(NOTES_PATH, `# Current results\n\n${recordedSoFar()}\n`);
		} catch (error) {
			console.error(`[pi-runner] notes could not be written: ${errorText(error)}`);
		}
	}

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

	// Each measuring session may submit only its assigned practice's result.
	let activePractice: string | null = null;

	function owedPractices(slugs: readonly string[] = currentTurnSlugs): string[] {
		const review = activeReview;
		return review === null || review.draft !== null || blockedPractices.has(review.slug)
			? []
			: slugs.filter((slug) => slug === review.slug);
	}

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

	// Batch tools validate each piece independently; whole-call validation would discard valid siblings.
	function documentedShape(schema: unknown): unknown {
		if (Array.isArray(schema)) {
			return schema.map(documentedShape);
		}
		if (!isRecord(schema)) {
			return schema;
		}
		const out: Record<string, unknown> = {};
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
				if (stored === 0) {
					return refusal(toolCallId, text);
				}
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

	function capturedChange(): { base: string; head: string; files: Map<string, DiffFile> } | null {
		const change = readChange(INPUT_PATHS.contextRoot);
		const view = `${CWD}/${CHANGE_ROOT}/diff.patch`;
		if (change === null || !existsSync(view)) {
			return null;
		}
		return { ...change, files: parseDiff(readFileSync(view, "utf8")) };
	}

	function captureSources(): EvidenceSources {
		const repository = INPUT_PATHS.repositoryRoot;
		const checkout = existsSync(`${repository}/.git`) ? repository : null;
		const change = capturedChange();
		return {
			repo: checkout,
			headRevision: change?.head ?? (checkout === null ? null : checkedOutCommit(checkout)),
			treeSourceFile: `${taskEnvelope.paths.repositoryRoot}/.git/HEAD`,
			contextDir: existsSync(INPUT_PATHS.contextRoot) ? INPUT_PATHS.contextRoot : null,
			contextReference: taskEnvelope.paths.contextRoot,
			change,
			sourceKindOf: (sourcePath) => sourceFileSources.get(sourcePath),
		};
	}

	function sourcesForPractice(slug: string, sources: EvidenceSources): EvidenceSources {
		const inputs = assignedInputs.get(slug);
		if (inputs?.scope.kind !== "SCAN") {
			return sources;
		}
		const contextPaths = practiceIndex.find((practice) => practice.slug === slug)?.contextPaths;
		const assigned = inputs.scope.item.path;
		if (contextPaths === undefined || assigned === undefined) {
			throw new UnratedError("unavailable", "scan context grants were not captured");
		}
		return { ...sources, permittedPaths: new Set([assigned, ...contextPaths]) };
	}

	function jobFiles(kind: string): JobFiles | null {
		const sources = capturedChange();
		const repository = INPUT_PATHS.repositoryRoot;
		const head =
			sources?.head ?? (existsSync(`${repository}/.git`) ? checkedOutCommit(repository) : null);
		if (
			!["scm.pull_request", "scm.pull_request_review", "scm.file"].includes(kind) ||
			head === null
		) {
			return null;
		}
		return { head: { revision: head }, change: sources };
	}

	function readBoundModels(): BoundModels {
		if (!existsSync(PRACTICE_SCRIPT_MODELS_PATH)) {
			return {};
		}
		try {
			return boundModelsSchema.parse(JSON.parse(readFileSync(PRACTICE_SCRIPT_MODELS_PATH, "utf8")));
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
		`Stop reading: the admitted results with their problems and the history are in this turn's prompt and in ` +
		`work/composition/results.json, and nothing else decides a piece. Persist the feedback you have ` +
		`with report_feedback now — and a WITHHOLD with its reason for each NOT_MET practice you decided ` +
		`to stay quiet about. Use tools only from this point onward; no planning prose.`;

	/** What the composer of the review on the work is told near its budget's end. */
	const REVIEW_NUDGE =
		`Everything this review may rest on is in this session. Store the final review now with one report_review call: ` +
		`first one decision for each NOT_MET result, RAISE or a withholding reason, then the complete summary and ` +
		`any line notes, which speak about each raised result, itself or through its problems, and no withheld one. ` +
		`No prose outside the calls.`;

	/** Calls a composition may make before its first recording call; at this one it is nudged to persist. */
	const COMPOSITION_EXPLORATION_NUDGE = 12;

	const PERSIST_DISCIPLINE =
		`Submit the outcome the evidence supports, MET, NOT_MET, DOES_NOT_APPLY, or COULD_NOT_DETERMINE; there is no quota. ` +
		`Use tools only from this point onward; no planning prose.`;

	const RECORD_NUDGE = `This turn has the work left to write its result and no more. Stop exploring. Submit what the inspected evidence supports for this session's practice with submit_result. ${PERSIST_DISCIPLINE}`;

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
			parsed = JSON.parse(raw);
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
		if (
			typeof envelope.reviewId !== "string" ||
			typeof envelope.jobId !== "string" ||
			!runtimeIdSchema.safeParse(envelope.reviewId).success ||
			!runtimeIdSchema.safeParse(envelope.jobId).success ||
			typeof envelope.attempt !== "number" ||
			!Number.isSafeInteger(envelope.attempt) ||
			envelope.attempt < 0 ||
			!isDecimalId(envelope.workspaceId)
		) {
			console.error(
				"[pi-runner] task requires reviewId/jobId UUIDs, a nonnegative attempt and a decimal workspaceId",
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
			reviewId: envelope.reviewId,
			jobId: envelope.jobId,
			attempt: envelope.attempt,
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
	const PRACTICE_PAGE_SUPPORT_PATH = `${CWD}/work/composition/in-app-support.json`;

	let practicePageSupport: PracticePageSupport | null = null;
	let conversationEligibility: ConversationEligibility | null = null;

	let routedDestinations: ReadonlyMap<string, readonly PrivateDestination[]> | null = null;

	function routedTo(destination: PrivateDestination, practiceSlug: string): boolean {
		if (destination === "PRACTICE_PAGE") {
			return (
				practicePageSupport?.state === "COMPLETE" &&
				practicePageSupport.practices.some(
					(practice) => practice.practiceSlug === practiceSlug && practice.decision === "ADMIT",
				)
			);
		}
		if (conversationEligibility?.state !== "COMPLETE") {
			return false;
		}
		const admitted = new Set(
			conversationEligibility.results
				.filter((route) => route.decision === "ADMIT")
				.map((route) => route.resultId),
		);
		return admittedResults.some(
			(result) =>
				result.practiceSlug === practiceSlug &&
				result.outcome === "NOT_MET" &&
				admitted.has(result.resultId),
		);
	}

	function destinationRefusal(destination: PrivateDestination, practiceSlug: string): string {
		if (destination === "PRACTICE_PAGE") {
			const decision =
				practicePageSupport?.state === "COMPLETE"
					? practicePageSupport.practices.find((practice) => practice.practiceSlug === practiceSlug)
							?.decision
					: undefined;
			return decision ?? "not routed";
		}
		const ids = new Set(
			admittedResults
				.filter((result) => result.practiceSlug === practiceSlug)
				.map((result) => result.resultId),
		);
		const decisions =
			conversationEligibility?.state === "COMPLETE"
				? conversationEligibility.results
						.filter((route) => ids.has(route.resultId))
						.map((route) => route.decision)
				: [];
		return decisions.length > 0 ? [...new Set(decisions)].join(", ") : "not routed";
	}
	const PUBLIC_FEEDBACK_HISTORY_PATH = `${CWD}/work/composition/public-feedback-history.json`;
	let compositionAdmitted = false;
	let admissionDigest: string | null = null;

	// Echo the exact composition inputs so Java validates references against the same snapshot.
	const composedFeedback: ComposedFeedback = {
		compositionFailures: [],
		contractVersion: REVIEW_CONTRACT_VERSION,
		admissionDigest: null,
		results: [],
		preparedTargets: [],
		feedback: [],
		review: null,
		preparation: null,
	};

	// Populate in place because the composer tool captures this array before admission supplies ids.
	const admittedResults: AdmittedResult[] = [];

	// A malformed composition request must fail the run; guessing destinations can disclose private feedback.
	function loadCompositionRequest(): CompositionRequest | null {
		if (!existsSync(COMPOSITION_REQUEST_PATH)) {
			return null;
		}
		const parsed = compositionRequestSchema.safeParse(
			JSON.parse(readFileSync(COMPOSITION_REQUEST_PATH, "utf8")),
		);
		if (!parsed.success) {
			throw new Error(
				`composition request breaks the contract: ${parsed.error.issues.map((issue) => `${issue.path.join(".")}: ${issue.message}`).join("; ")}`,
			);
		}
		const request = parsed.data;
		const closed: DestinationBounds = { enabled: false, maxPieces: 0 };
		const destinations: Record<Destination, DestinationBounds> = {
			ON_WORK: request.destinations.ON_WORK ?? closed,
			PRACTICE_PAGE: request.destinations.PRACTICE_PAGE ?? closed,
			CONVERSATION: request.destinations.CONVERSATION ?? closed,
		};
		if (
			!DESTINATIONS.some(
				(destination) =>
					destinations[destination].enabled && destinations[destination].maxPieces > 0,
			)
		) {
			return null;
		}
		if (destinations.ON_WORK.enabled && request.onWorkCopyForms.length === 0) {
			throw new Error("composition request opens ON_WORK without a copy form");
		}
		return {
			destinations,
			onWorkCopyForms: request.onWorkCopyForms,
			minDistinctItems: request.minDistinctItems,
		};
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
			const prepared: unknown = JSON.parse(readFileSync(PREPARED_FEEDBACK_PATH, "utf8"));
			const entries = isRecord(prepared) ? jsonArray(prepared.prepared) : [];
			return entries.flatMap((entry) => {
				if (!isRecord(entry)) {
					return [];
				}
				const { threadKey, destination, practiceSlug } = entry;
				return typeof threadKey === "string" &&
					threadKey.trim() &&
					isDestination(destination) &&
					typeof practiceSlug === "string" &&
					practiceSlug.trim()
					? [{ threadKey, destination, practiceSlug }]
					: [];
			});
		} catch (error) {
			console.error(
				`[pi-runner] prepared feedback unreadable for composition: ${errorText(error)}`,
			);
			return [];
		}
	}

	/**
	 * The `destination:practice` pairs the staged delivered feedback can show as already said. The server chose the person
	 * and the records this review may read; a record counts only when it is current, not withdrawn, carries its staged
	 * words and a delivery time, and rests on a NOT_MET problem of the practice. Prepared feedback reached no one, and
	 * a history that cannot be read does not establish prior communication.
	 */
	function stagedDeliveredPractices(): Set<string> {
		const file = `${nodePath.dirname(PREPARED_FEEDBACK_PATH)}/feedback.json`;
		try {
			if (!existsSync(file)) {
				return new Set();
			}
			const history: unknown = JSON.parse(readFileSync(file, "utf8"));
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
					const { destination, recordedClaimCurrentness, withdrawn, body, deliveredAt } = entry;
					if (
						(destination !== "PRACTICE_PAGE" && destination !== "CONVERSATION") ||
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
							? [`${destination}:${trimmedText(support.practiceSlug)}`]
							: [],
					);
				}),
			);
		} catch (error) {
			console.error(
				`[pi-runner] delivered feedback unreadable for composition: ${errorText(error)}`,
			);
			return new Set();
		}
	}

	function leanResults(results: readonly AdmittedResult[]): LeanResult[] {
		return results.map((result) => ({
			resultId: result.resultId,
			practiceSlug: result.practiceSlug,
			outcome: result.outcome,
			citations: result.citations.map(leanCitation),
			problems: result.problems.map((problem) => ({
				problemId: problem.problemId,
				severity: problem.severity,
				anchorable: problem.anchorable,
				citations: problem.citations.map(leanCitation),
			})),
		}));
	}

	function persistComposedFeedback(): void {
		for (const piece of undeliverableFeedback(composedFeedback)) {
			const { destination, practiceSlug, supersedesThreadKey } = piece;
			console.error(
				destination === undefined || practiceSlug === undefined || supersedesThreadKey === undefined
					? `[pi-runner] composed feedback the server cannot deliver: a SUPERSEDE piece lacks the destination, ` +
							`practice or thread it replaces: ${JSON.stringify({ destination, practiceSlug, supersedesThreadKey })}`
					: `[pi-runner] composed feedback the server cannot deliver: ${destination}/${practiceSlug} ` +
							`supersedes '${supersedesThreadKey}', which this envelope does not list as staged`,
			);
		}
		const feedback = composedFeedback.feedback.toSorted(
			(a, b) => Number(a.action === "WITHHOLD") - Number(b.action === "WITHHOLD"),
		);
		writeFileSync(FEEDBACK_PATH, JSON.stringify({ ...composedFeedback, feedback }, null, 2));
	}

	function buildFeedbackTool(
		practiceSlugs: string[],
		request: CompositionRequest,
		results: readonly AdmittedResult[],
		preparedTargets: PreparedFeedbackTarget[],
		deliveredPractices: ReadonlySet<string>,
	) {
		composedFeedback.preparedTargets = preparedTargets;
		const enabledDestinations = PRIVATE_DESTINATIONS.filter(
			(destination) => request.destinations[destination].enabled,
		);
		const usedPerDestination: Record<PrivateDestination, number> = {
			PRACTICE_PAGE: 0,
			CONVERSATION: 0,
		};
		const seen = new Set<string>();

		const store = (value: unknown): { stored: boolean; text: string } => {
			const read = readFeedbackPiece(value, practiceSlugs, results);
			if (typeof read === "string") {
				return { stored: false, text: read };
			}
			const piece = read;
			// Read current admitted results per call; the tool exists before admission fills them.
			const admitted = resultsById(results);
			const bounds = request.destinations[piece.destination];
			if (!bounds.enabled) {
				return skipped(
					`${piece.destination} is not a destination this run may write for (open: ${enabledDestinations.join(", ")}); skipped.`,
				);
			}
			const key = `${piece.destination}:${piece.practiceSlug}`;
			if (seen.has(key)) {
				return skipped(
					`already have a ${piece.destination} piece for ${piece.practiceSlug}, and the first one stands; nothing to resend for it. Skipped.`,
				);
			}
			const delivers = piece.action !== "WITHHOLD";
			if (delivers && usedPerDestination[piece.destination] >= bounds.maxPieces) {
				return skipped(
					`${piece.destination} holds at most ${bounds.maxPieces} piece(s) and is full: this one does not fit — WITHHOLD it with BELOW_BAR or leave it out. Skipped.`,
				);
			}
			const rejection = validateFeedback(piece, admitted, preparedTargets, deliveredPractices);
			if (rejection !== null) {
				return skipped(rejection);
			}
			if (!(routedDestinations?.get(piece.practiceSlug) ?? []).includes(piece.destination)) {
				return skipped(
					`${piece.destination} cannot carry feedback about ${piece.practiceSlug} this run ` +
						`(${destinationRefusal(piece.destination, piece.practiceSlug)}); write no piece for it there, not even a WITHHOLD. Skipped.`,
				);
			}
			if (piece.destination === "PRACTICE_PAGE" && delivers) {
				const pieces = negativePiecesOfWork(piece.practiceSlug);
				if (pieces < request.minDistinctItems) {
					return skipped(
						`PRACTICE_PAGE needs a pattern across at least ${request.minDistinctItems} pieces of work, and ` +
							`${piece.practiceSlug} has support on ${pieces} (the PRACTICE_PAGE support read); WITHHOLD it ` +
							`with BELOW_BAR. Skipped.`,
					);
				}
			}
			seen.add(key);
			if (delivers) {
				usedPerDestination[piece.destination] += 1;
			}
			composedFeedback.feedback.push({
				...piece,
				basedOn: evidenceRefs(piece.basedOn, namedEvidence(admitted)),
			});
			return {
				stored: true,
				text: `stored a ${piece.destination} piece for ${piece.practiceSlug} (${piece.action}); ${usedPerDestination[piece.destination]}/${bounds.maxPieces} used on that destination.`,
			};
		};

		return defineTool({
			name: "report_feedback",
			exposure: "model-only",
			label: "Report Feedback",
			description:
				"Persist feedback feedback for the developer's practice pages and the mentor conversation, one per " +
				"destination and practice. Send every piece you have ready in one call; each is stored or skipped on " +
				"its own, with the reason, and a skipped piece can be sent again corrected without re-sending the " +
				"stored ones. This is an intervention, not a measurement: it takes no outcome, severity or " +
				"confidence, and no citation you typed yourself.",
			parameters: {
				type: "object",
				required: ["feedback"],
				properties: {
					feedback: documentedShape(
						listSchema(
							{
								type: "object",
								required: ["destination", "practiceSlug", "basedOn", "action"],
								properties: {
									destination: {
										type: "string",
										enum: enabledDestinations,
										description:
											"Which surface this piece is for. Each has its own level and its own rules.",
									},
									practiceSlug: {
										type: "string",
										enum: practiceSlugs,
										description:
											"The primary practice whose intervention this piece advances. Related results may support it.",
									},
									basedOn: {
										type: "array",
										minItems: 1,
										items: { type: "string", minLength: 1 },
										description:
											"What this rests on: admitted ids from this run, a result's resultId or, for one located problem, a problem's problemId. Include related practices only when they describe the same underlying event.",
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
											"PRACTICE_PAGE only: the evidenced pattern across the work and why it matters; the future way of working belongs to nextStep. Grounded in current results and their problems; never quote a line or claim change over time from the pre-run history. Markdown, read verbatim.",
									},
									nextStep: {
										type: "string",
										maxLength: FEEDBACK_TEXT_BOUNDS.nextStep,
										description:
											"PRACTICE_PAGE only: the one repeatable way of working for the next piece of work; the body does not state it. Name the missing decision, not a heading/template unless the practice requires one; never provide paste-ready prose.",
									},
									notes: {
										type: "object",
										additionalProperties: false,
										required: [
											"situation",
											"capability",
											"evidenceSummary",
											"inConversationLearning",
										],
										description:
											"CONVERSATION only. Notes TO the mentor, which composes the whole turn itself, later, " +
											"with the live conversation in front of it. Write what it needs to know, never a " +
											"sentence for it to say: anything phrased as a line of dialogue will be spoken, and " +
											"will sound like a script.",
										properties: {
											situation: {
												type: "string",
												maxLength: FEEDBACK_TEXT_BOUNDS.situation,
												description:
													"The concern the problems have in common, as it can be observed, stated once rather than " +
													"listing each item again. Your words about it, not words for it - third person, never " +
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
													"Where the concern occurred, compactly: each supporting work and problem located once. " +
													"Summarise rather than inventing a quote; the original result evidence is " +
													"staged separately for the mentor to inspect.",
											},
											inConversationLearning: {
												type: "string",
												maxLength: FEEDBACK_TEXT_BOUNDS.inConversationLearning,
												description:
													"Understanding the developer articulates in a way that can be observed before the conversation ends: a distinction, decision, question, or self-check they say. Not a promise, future output, message text, or compliance target.",
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
							"feedback",
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
				const submitted = submittedList(isRecord(params) ? params.feedback : null);
				if ("error" in submitted) {
					return refusal(toolCallId, `feedback refused — ${submitted.error}`);
				}
				if (submitted.items.length === 0) {
					return refusal(
						toolCallId,
						"feedback refused — the list is empty; send every piece you have ready in it",
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
				if (stored === 0) {
					return refusal(toolCallId, text);
				}
				return {
					content: [{ type: "text", text }],
					details: { stored, total: composedFeedback.feedback.length },
					terminate: turnDemand?.endsWhenPaid === true && turnDemand.owed() === 0,
				};
			},
		});
	}

	function reviewableById(
		reviewable: readonly Record<string, unknown>[],
	): Map<string, ReviewedResult> {
		return new Map(
			reviewable.map((result) => [
				String(result.resultId),
				{
					practiceSlug: String(result.practiceSlug),
					summaryOnly: result.summaryOnly === true,
					outcome: result.outcome,
					problems: jsonArray(result.problems)
						.filter(isRecord)
						.map((problem) => ({
							problemId: String(problem.problemId),
							citations: jsonArray(problem.citations).filter(isRecord),
						})),
				},
			]),
		);
	}

	const REVIEW_FINAL = "The review on this work is final; this composition accepts nothing more.";

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
					? `a summary resting on ${summary.basedOn.length} result(s) and problem(s)`
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

	function undecidedByReview(reviewable: readonly Record<string, unknown>[]): string[] {
		const decided = decidedByReview(composedFeedback.review);
		return reviewable
			.filter((result) => result.outcome === "NOT_MET")
			.map((result) => String(result.resultId))
			.filter((id) => !decided.has(id));
	}

	function practiceContext(results: readonly Record<string, unknown>[]): ReviewPractice[] {
		const slugs = new Set(results.map((result) => String(result.practiceSlug)));
		return practiceIndex
			.filter((practice) => slugs.has(practice.slug))
			.map((practice) => ({
				slug: practice.slug,
				name: practice.name,
				...(practice.whyItMatters === undefined ? {} : { whyItMatters: practice.whyItMatters }),
				knownLimitations: practice.knownLimitations,
				...(practice.versionId === undefined ? {} : { versionId: practice.versionId }),
			}));
	}

	const FEEDBACK_TEXT_BOUNDS = {
		title: 255,
		body: 8000,
		nextStep: 2000,
		supersedesThreadKey: 64,
		situation: 4000,
		capability: 2000,
		evidenceSummary: 4000,
		inConversationLearning: 2000,
		alreadySaid: 2000,
	} as const;

	const optionalFeedbackText = (field: keyof typeof FEEDBACK_TEXT_BOUNDS) =>
		z
			.string()
			.trim()
			.max(FEEDBACK_TEXT_BOUNDS[field])
			.nullish()
			.transform((value) => (value === "" ? undefined : (value ?? undefined)));
	const feedbackPieceSchema = z.strictObject({
		destination: z.enum(PRIVATE_DESTINATIONS),
		practiceSlug: z.string().trim().min(1),
		action: z.enum(ACTIONS),
		basedOn: z.array(z.string().trim().min(1)).default([]),
		supersedesThreadKey: optionalFeedbackText("supersedesThreadKey"),
		withholdReason: z
			.enum(WITHHOLD_REASONS)
			.nullish()
			.transform((value) => value ?? undefined),
		title: optionalFeedbackText("title"),
		body: optionalFeedbackText("body"),
		nextStep: optionalFeedbackText("nextStep"),
		notes: z
			.strictObject({
				situation: optionalFeedbackText("situation"),
				capability: optionalFeedbackText("capability"),
				evidenceSummary: optionalFeedbackText("evidenceSummary"),
				inConversationLearning: optionalFeedbackText("inConversationLearning"),
				alreadySaid: optionalFeedbackText("alreadySaid"),
			})
			.nullish()
			.transform((value) => value ?? undefined),
	});

	function readFeedbackPiece(
		value: unknown,
		practiceSlugs: readonly string[],
		results: readonly AdmittedResult[],
	): ReadFeedbackPiece | string {
		const parsed = feedbackPieceSchema.safeParse(value);
		if (!parsed.success) {
			return parsed.error.issues
				.map((issue) => `${issue.path.join(".") || "piece"}: ${issue.message}`)
				.join("; ");
		}
		const piece = parsed.data;
		if (!practiceSlugs.includes(piece.practiceSlug)) {
			return `practiceSlug '${piece.practiceSlug}' is not a practice with an admitted result in this run (those are: ${practiceSlugs.join(", ")}); skipped.`;
		}
		if (piece.basedOn.length === 0) {
			const ids = results
				.filter((result) => result.practiceSlug === piece.practiceSlug)
				.map((result) => result.resultId);
			return `basedOn is required: the admitted result or problem id(s) this piece rests on (${piece.practiceSlug}'s results: ${ids.join(", ")}); skipped.`;
		}
		if (piece.action === "NEW" && feedbackBody(piece).length > 8000) {
			return "the complete feedback text must be at most 8000 characters; shorten it and send it again";
		}
		return piece;
	}

	const THIS_WORK = ((): string | undefined => {
		const metadataPath = nodePath.join(INPUT_PATHS.contextRoot, "metadata.json");
		if (!existsSync(metadataPath) || !isRecord(folderIndex)) {
			return undefined;
		}
		const metadata: unknown = JSON.parse(readFileSync(metadataPath, "utf8"));
		if (!isRecord(metadata)) {
			return undefined;
		}
		const kind = folderIndex.itemKind;
		if (kind !== "scm.pull_request" && kind !== "scm.issue") {
			return undefined;
		}
		return workIdentity(kind, metadata[kind === "scm.pull_request" ? "pr_url" : "html_url"]);
	})();

	// Enforce snapshot-dependent constraints here for fast model correction; Java rechecks them.
	function negativePiecesOfWork(practiceSlug: string): number {
		if (practicePageSupport?.state !== "COMPLETE") {
			return 0;
		}
		const support = practicePageSupport.practices.find(
			(practice) => practice.practiceSlug === practiceSlug,
		);
		return new Set(
			(support?.occurrences ?? []).map(
				(occurrence) => `${occurrence.itemKind}:${occurrence.itemId}`,
			),
		).size;
	}

	// Practice-page feedback describes a pattern across work, not a quotation of this item.
	function validatePracticePagePiece(
		piece: ReadFeedbackPiece,
		named: ReadonlyMap<string, { result: AdmittedResult }>,
	): string | null {
		const { body } = piece;
		if (!hasText(body?.trim())) {
			return "PRACTICE_PAGE needs a body; skipped.";
		}
		const normalizedBody = normalizeQuotedText(body);
		const repeatsCurrentEvidence = piece.basedOn.some((id) => {
			const result = named.get(id)?.result;
			return [
				...(result?.citations ?? []),
				...(result?.problems ?? []).flatMap((problem) => problem.citations),
			].some((citation) => {
				const quote = normalizeQuotedText(optionalString(citation.quote) ?? "");
				return quote.length >= 12 && normalizedBody.includes(quote);
			});
		});
		if (repeatsCurrentEvidence) {
			return "PRACTICE_PAGE describes a pattern across items; do not copy a current item quote into it. Skipped.";
		}
		return null;
	}

	function validateFeedback(
		piece: ReadFeedbackPiece,
		results: ReadonlyMap<string, AdmittedResult>,
		preparedTargets: readonly PreparedFeedbackTarget[],
		deliveredPractices: ReadonlySet<string>,
	): string | null {
		const evidenceError = validateFeedbackEvidence(piece.practiceSlug, piece.basedOn, results);
		if (evidenceError !== null) {
			return evidenceError;
		}
		const named = namedEvidence(results);
		if (piece.action === "WITHHOLD") {
			const reason = piece.withholdReason;
			if (!hasText(reason)) {
				return "WITHHOLD needs a withholdReason; skipped.";
			}
			// The withholding settles each NOT_MET practice in basedOn (undecidedPrivatePractices), so each needs a record here.
			const unrecorded = notMetPractices(
				piece.basedOn.flatMap((id) => named.get(id)?.result ?? []),
			).filter((slug) => !deliveredPractices.has(`${piece.destination}:${slug}`));
			if ([...PRIOR_ADVICE_REASONS].some((prior) => prior === reason) && unrecorded.length > 0) {
				return (
					`${reason} rests on feedback recorded as delivered on ${piece.destination} for ${unrecorded.join(", ")} in the ` +
					`delivered-feedback history: current, not withdrawn, with its words, for each NOT_MET practice in basedOn. The captured history does not establish that prerequisite; prepared ` +
					`feedback and other destinations do not count. Decide this destination on its own evidence: the piece it supports, ` +
					`or WITHHOLD with BELOW_BAR. Skipped.`
				);
			}
			return null;
		}
		if (isBlank(piece.title)) {
			return "A piece that is not a WITHHOLD needs a title; skipped.";
		}
		if (piece.action === "SUPERSEDE") {
			if (!hasText(piece.supersedesThreadKey)) {
				return "SUPERSEDE needs a supersedesThreadKey; skipped.";
			}
			if (
				!preparedTargets.some(
					(target) =>
						target.threadKey === piece.supersedesThreadKey &&
						target.destination === piece.destination &&
						target.practiceSlug === piece.practiceSlug,
				)
			) {
				return `No queued message has threadKey '${piece.supersedesThreadKey}'; it must come from the task-declared prepared-feedback file. Skipped.`;
			}
		}
		if (piece.destination === "CONVERSATION") {
			return validateConversationPiece(piece);
		}
		if (piece.notes) {
			return "Only CONVERSATION feedback may carry a notes block; skipped.";
		}
		if (isBlank(piece.nextStep)) {
			return `${piece.destination} needs a nextStep; skipped.`;
		}
		return validatePracticePagePiece(piece, named);
	}

	function composerView(result: AdmittedResult): Record<string, unknown> {
		const citations = result.citations.map(coordinatesOf);
		if (result.outcome !== "NOT_MET" && result.outcome !== "MET") {
			const { resultId, practiceSlug, outcome } = result;
			return {
				resultId,
				practiceSlug,
				outcome,
				evidence: qualificationEvidence(result),
				citations,
			};
		}
		// MET counterevidence keeps the same qualifications as NOT_MET.
		const { citations: _admitted, example, problems, ...rest } = result;
		return {
			...rest,
			evidence: isRecord(result.evidence) ? { ...result.evidence, citations } : result.evidence,
			...(isRecord(example)
				? { example: { ...example, citation: coordinatesOf(example.citation) } }
				: {}),
			citations,
			problems: problems.map((problem) => ({
				...problem,
				citations: problem.citations.map(coordinatesOf),
			})),
		};
	}

	// Composition receives criteria, but not measuring leads or source directions that could initiate another assessment.
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
			history = JSON.parse(readFileSync(file, "utf8"));
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
			summary: review.summary
				? { basedOn: review.summary.basedOn, ...bodyAt("summary.body") }
				: null,
			inline: review.inline.map(({ body: _body, ...note }, index) => ({
				...note,
				...bodyAt(`inline[${index}].body`),
			})),
			withheld: review.withheld,
		};
		return `### The review composed for this work\nThis decision is not a receipt of provider delivery. It may have entered public handling, whose final gates can still withhold it. Only delivered-history records establish prior communication. Private destinations remain independently useful and may make the same supported point. ${stored ? "Read the located bodies if their wording matters." : "Its wording is not available as a staged file here."}\n\`\`\`json\n${JSON.stringify(facts, null, 1)}\n\`\`\`\n`;
	}

	function buildCompositionTurn(
		request: CompositionRequest,
		results: readonly AdmittedResult[],
		notReached: readonly string[] = [],
	): string {
		const openDestinations = PRIVATE_DESTINATIONS.filter(
			(destination) => request.destinations[destination].enabled,
		)
			.map(
				(destination) => `${destination} (at most ${request.destinations[destination].maxPieces})`,
			)
			.join(", ");
		const closed = PRIVATE_DESTINATIONS.filter(
			(destination) => !request.destinations[destination].enabled,
		);
		const closedNote =
			closed.length > 0
				? ` Closed this turn, so write nothing for them: ${closed.join(", ")}.`
				: "";
		const coverageNote = notReachedNote(notReached);
		const routing = PRIVATE_DESTINATIONS.filter(
			(destination) => request.destinations[destination].enabled,
		)
			.map(
				(destination) =>
					`${destination}: ${privatelyComposable()
						.filter((slug) => routedDestinations?.get(slug)?.includes(destination) === true)
						.join(", ")}`,
			)
			.join("; ");
		const admitted = JSON.stringify({ results: results.map(composerView) }, null, 1);
		const onTheWork = plannedReviewFacts();
		const historyRoot = nodePath.dirname(PREPARED_FEEDBACK_PATH);
		const context = [
			stagedBlock(
				"The composition request (destinations, limits, copy forms)",
				COMPOSITION_REQUEST_PATH,
			),
			stagedBlock(
				"What earlier reviews recorded about this person",
				`${historyRoot}/problems.json`,
			),
			request.destinations.PRACTICE_PAGE.enabled
				? stagedBlock(
						"PRACTICE_PAGE support: occurrences a new card may cite at this read",
						PRACTICE_PAGE_SUPPORT_PATH,
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
The review just finished. The criteria its NOT_MET practices were assessed against come first, as staged; its ${results.length} admitted result(s) follow them, one per practice. The MET and NOT_MET ones carry their rationale and their citations by coordinates; each NOT_MET one also carries its problems, the located problems it found, each with its own citations. The abstentions carry the limit they recorded, an open question or why the practice did not apply, and their citations by coordinates. The full record, quoted lines included, is \`work/composition/results.json\`. The history follows them.

${notMetCriteria(results)}
\`\`\`json
${admitted}
\`\`\`

${context}
${onTheWork}
Destinations open this turn: ${openDestinations}.${closedNote}
The practices each open destination may carry: ${routing}. Write no piece, not even a WITHHOLD, for a practice on a destination that does not list it.
A pattern claim needs at least ${request.minDistinctItems} distinct pieces of work.

${coverageNote}Persist the feedback with report_feedback — every piece you have in one call. Writing nothing on a destination is a correct and common outcome; say in one line why, and stop.`;
	}

	/** A transport failure or a server that is not answering yet; a refusal is a decision, not a blip. */
	class AdmissionUnreachableError extends Error {
		name = "AdmissionUnreachableError";
	}

	/** Retry transport failures, not slow admission or explicit refusals. */
	const ADMISSION_ATTEMPTS = 4;

	// Admission verifies every cited blob against the Git object, in a container the server starts, so
	// one attempt may take minutes; the cap only bounds a server that went silent without closing.
	const ADMISSION_ATTEMPT_TIMEOUT_MS = 10 * 60_000;

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
				throw new Error(
					`result admission did not answer within ${ADMISSION_ATTEMPT_TIMEOUT_MS}ms`,
					{
						cause: error,
					},
				);
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
		practicePageSupport = readPracticePageSupport(answer.practicePageSupport, admittedResults);
		if (practicePageSupport?.state === "COMPLETE") {
			writeFileSync(PRACTICE_PAGE_SUPPORT_PATH, JSON.stringify(practicePageSupport, null, 2));
		}
		conversationEligibility = readConversationEligibility(
			answer.conversationEligibility,
			admittedResults,
		);
	}

	async function prepareComposedFeedback(
		safety: ReturnType<typeof scheduleDeadline>,
	): Promise<void> {
		if (admissionDigest === null || safety.expired()) {
			return;
		}
		const composition: FeedbackComposition[] = [];
		const { review } = composedFeedback;
		if (review !== null) {
			const refs = [
				...(review.summary?.basedOn ?? []),
				...review.inline.flatMap((note) => note.basedOn),
			];
			const resultIds = [...new Set(refs.map((ref) => ref.resultId))];
			const quiet = resultIds.length === 0;
			const selected = quiet
				? [...new Set(review.withheld.flatMap((piece) => piece.basedOn.map((ref) => ref.resultId)))]
				: resultIds;
			if (selected.length > 0) {
				composition.push({
					destination: "ON_WORK",
					resultIds: selected,
					summary: review.summary?.body ?? null,
					notes: review.inline.map((note) => ({
						problemId: note.anchor.problemId,
						body: note.body,
					})),
					withheldReason: quiet ? "COMPOSER_WITHHELD" : null,
				});
			}
		}
		for (const piece of composedFeedback.feedback) {
			composition.push({
				destination: piece.destination,
				resultIds: [...new Set(piece.basedOn.map((ref) => ref.resultId))],
				summary: piece.action === "WITHHOLD" ? null : feedbackBody(piece),
				notes: [],
				withheldReason: piece.action === "WITHHOLD" ? "COMPOSER_WITHHELD" : null,
			});
		}
		if (composition.length === 0) {
			return;
		}
		const result = await prepareFeedback({
			url: `${LLM_PROXY_URL}/prepare-feedback`,
			token: LLM_PROXY_TOKEN,
			payload: {
				reviewId: taskEnvelope.reviewId,
				jobId: taskEnvelope.jobId,
				attempt: taskEnvelope.attempt,
				basisResultIds: [...new Set(composition.flatMap((piece) => piece.resultIds))],
				composition,
			},
			timeoutMs: Math.min(
				ADMISSION_ATTEMPT_TIMEOUT_MS,
				AGENT_BUDGET_MS - (Date.now() - PROCESS_START_MS),
			),
		});
		if (result.state === "REFUSED") {
			// The server made a final decision: returning this package at terminal must not bypass it.
			composedFeedback.review = null;
			composedFeedback.feedback = [];
			if (composition.some((piece) => piece.destination === "ON_WORK")) {
				recordCompositionFailure("PUBLIC_REVIEW", "SERVER_REFUSED");
			}
			if (composition.some((piece) => piece.destination !== "ON_WORK")) {
				recordCompositionFailure("PRIVATE_FEEDBACK", "SERVER_REFUSED");
			}
			persistComposedFeedback();
		}
		if (result.state === "PREPARED") {
			composedFeedback.preparation = result.receipt;
			persistComposedFeedback();
		}
		// An acknowledgement can be lost after preparation commits; reconcile the unchanged immutable package.
		console.error(`[pi-runner] feedback preparation: ${result.state}`);
	}

	/**
	 * Holds an admission answer as the composition's ground, whether this attempt's own admission returned it or the
	 * server staged an earlier attempt's for a continuing writer: both answer the one contract.
	 */
	function acceptAdmission(admitted: unknown): Record<string, unknown> {
		if (
			!isRecord(admitted) ||
			admitted.contractVersion !== 3 ||
			typeof admitted.admissionDigest !== "string" ||
			!Array.isArray(admitted.admitted)
		) {
			throw new Error("result admission returned an invalid contract");
		}
		for (const withheld of jsonArray(admitted.withheld)) {
			if (isRecord(withheld)) {
				console.error(
					`[pi-runner] admission withheld ${logValue(withheld.practiceSlug)}: ${logValue(withheld.reason)}`,
				);
			}
		}
		admittedResults.splice(0, admittedResults.length, ...admitted.admitted.map(admittedResultOf));
		admissionDigest = admitted.admissionDigest;
		compositionAdmitted = true;
		composedFeedback.admissionDigest = admissionDigest;
		composedFeedback.results = leanResults(admittedResults);
		mkdirSync(`${CWD}/work/composition`, { recursive: true });
		writeFileSync(COMPOSITION_RESULTS_PATH, JSON.stringify({ results: admittedResults }, null, 2));
		return admitted;
	}

	// Private composition requires a complete server routing read; missing routing closes the destination.
	function closeUnroutedPrivateDestinations(request: CompositionRequest): void {
		const notMet = notMetPractices(admittedResults);
		for (const destination of PRIVATE_DESTINATIONS) {
			if (!request.destinations[destination].enabled || notMet.length === 0) {
				continue;
			}
			const read = destination === "PRACTICE_PAGE" ? practicePageSupport : conversationEligibility;
			if (read?.state !== "COMPLETE") {
				request.destinations[destination].enabled = false;
				console.error(
					`[pi-runner] ${destination} closed for this run: ${destination === "PRACTICE_PAGE" ? "support context" : "routing"} ${read === null ? "missing or malformed" : read.state}`,
				);
				if (read?.state !== "REFUSED") {
					recordCompositionFailure("PRIVATE_FEEDBACK", "RUNTIME_ERROR");
				}
			} else if (!notMet.some((slug) => routedTo(destination, slug))) {
				request.destinations[destination].enabled = false;
				console.error(
					`[pi-runner] ${destination} closed for this run: no practice is routed to it`,
				);
			}
		}
		routedDestinations = new Map(
			notMet.map((slug) => [
				slug,
				PRIVATE_DESTINATIONS.filter(
					(destination) => request.destinations[destination].enabled && routedTo(destination, slug),
				),
			]),
		);
	}

	function privatelyComposable(): string[] {
		const notMet = notMetPractices(admittedResults);
		const routed = routedDestinations;
		return routed === null ? notMet : notMet.filter((slug) => (routed.get(slug) ?? []).length > 0);
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
			answer.contractVersion !== 3 ||
			typeof answer.readAt !== "string" ||
			!Number.isFinite(Date.parse(answer.readAt)) ||
			history === null ||
			!isRecord(history.item) ||
			!isDecimalId(history.item.id) ||
			typeof history.item.kind !== "string" ||
			(history.item.url !== null &&
				history.item.url !== undefined &&
				typeof history.item.url !== "string") ||
			!Array.isArray(history.feedback)
		) {
			throw new Error("public feedback history read returned an invalid contract");
		}
		const { item } = history;
		const feedback = history.feedback.map((entry: unknown) => {
			if (
				!isRecord(entry) ||
				typeof entry.id !== "string" ||
				entry.destination !== "ON_WORK" ||
				typeof entry.deliveredAt !== "string" ||
				!Number.isFinite(Date.parse(entry.deliveredAt)) ||
				!["CURRENT", "STALE", "UNVERIFIABLE"].includes(String(entry.currentness)) ||
				(entry.body !== null && typeof entry.body !== "string") ||
				typeof entry.bodyUnavailable !== "boolean" ||
				typeof entry.withdrawn !== "boolean" ||
				!Array.isArray(entry.basedOn) ||
				!Array.isArray(entry.copies)
			) {
				throw new Error("public feedback history read returned an invalid contract");
			}
			return {
				...entry,
				item,
				publicEligible: true,
				recordedClaimCurrentness: entry.currentness,
				...(entry.bodyUnavailable ? { wordsUnavailable: "COPY_AUTHORITY_UNAVAILABLE" } : {}),
			};
		});
		const read = { readAt: answer.readAt, history: { ...history, feedback } };
		writeFileSync(PUBLIC_FEEDBACK_HISTORY_PATH, JSON.stringify(read, null, 2));
		return read;
	}

	function noteToolCall(
		turn: TurnTrace,
		toolName: string,
		args: unknown,
		measuring: boolean,
	): void {
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
			void steer(
				activeSession,
				composerTool === "report_review" ? REVIEW_NUDGE : COMPOSITION_NUDGE,
			);
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

	function openTurnTrace(
		phase: SessionPhase,
		label: string,
		budget: Work,
		demand: TurnDemand,
	): TurnTrace {
		resetSessionGuards();
		lastEventAt = Date.now();
		currentTurn = {
			phase,
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

	/** How long an aborted turn waits for the session to go idle before the next turn is sent. */
	const ABORT_SETTLE_MS = 30_000;

	/** Where the practice's criteria are staged for this review. */
	function criteriaPathOf(slug: string): string {
		return `${nodePath.dirname(INPUT_PATHS.practiceIndex)}/${slug}.md`;
	}

	function criteriaFileOf(slug: string): string | null {
		const file = criteriaPathOf(slug);
		return existsSync(file) ? readFileSync(file, "utf8") : null;
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

	// Restore only the current practice's draft after compaction; other drafts are outside its context.
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
						sourcePath: `${context}/change.json`,
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
								sourcePath: `${context}/change.json`,
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
				outcome: "DOES_NOT_APPLY",
				summary: "No completed merge is recorded",
				explanation: "The captured provider record states that this work has not merged.",
				citations: [
					{
						sourceKind: "scm.pull-request.core",
						sourcePath: `${context}/metadata.json`,
						path: `${context}/metadata.json`,
						startLine: 3,
						endLine: 3,
						quote: '"is_merged": false',
					},
				],
				evidence: {
					inapplicability: {
						consulted: ["scm.pull-request.core"],
						prerequisite: "work that has merged",
						ruledOutBy: "the provider records is_merged as false",
					},
				},
			},
		];
		return `## How submit_result takes a result
The arguments are one complete result object for this session's practice, not a list and not a JSON-encoded string. Each block below is a whole alternative call, with an illustrative outcome, not evidence from this work:
${example.map((result) => `\`\`\`json\n${JSON.stringify(result, null, 1)}\n\`\`\``).join("\n\n")}`;
	})();

	function practiceTurnText(
		heading: string,
		slug: string,
		brief: string,
		prepared: string,
	): string {
		const illustrations = openingInContext ? "" : `\n\n${RESULT_EXAMPLE}`;
		return `${openingIfNeeded(brief)}${heading}

${criteriaOf([slug])}${illustrations}${preparedSectionOf(slug, prepared)}

Submit one result for ${slug} with submit_result: the outcome its criterion above decides through its applicability, Judge and Defer, as the evidence supports it, DOES_NOT_APPLY and COULD_NOT_DETERMINE included. What the brief shows is yours to quote; read more when the criterion needs evidence beyond it. This session submits and corrects only this practice's result.`;
	}

	function compositionBudget(notMet: number): Work {
		return turnBudget(Math.min(Math.max(1, notMet), PRACTICES_PER_TURN), PER_PRACTICE_WORK);
	}

	function undecidedPrivatePractices(): string[] {
		const practiceOf = new Map(
			admittedResults.map((result) => [result.resultId, result.practiceSlug]),
		);
		const decided = new Set(
			composedFeedback.feedback.flatMap((piece) => [
				piece.practiceSlug,
				...piece.basedOn.flatMap((ref) => practiceOf.get(ref.resultId) ?? []),
			]),
		);
		return privatelyComposable().filter((slug) => !decided.has(slug));
	}

	function recordCompositionFailure(
		phase: CompositionPhase,
		reason: CompositionFailureReason,
	): void {
		if (!composedFeedback.compositionFailures.some((failure) => failure.phase === phase)) {
			composedFeedback.compositionFailures.push({ phase, reason });
		}
	}

	/** Retry undecided composition once, under its own work budget and the run's safety line. */
	async function askComposerOnceMore(
		session: AgentSession,
		phase: SessionPhase,
		notMet: readonly string[],
		undecided: () => readonly string[],
		request: CompositionRequest,
		buildText: () => string,
		safety: ReturnType<typeof scheduleDeadline>,
	): Promise<TurnTrace> {
		console.error(
			`[pi-runner] composition left ${notMet.length} NOT_MET practice(s) undecided — asking once more`,
		);
		const trace = openTurnTrace(phase, "composition once more", compositionBudget(notMet.length), {
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
		const room = PRIVATE_DESTINATIONS.filter(
			(destination) => request.destinations[destination].enabled,
		)
			.map((destination) => {
				const { maxPieces } = request.destinations[destination];
				const used = composedFeedback.feedback.filter(
					(piece) => piece.destination === destination && piece.action !== "WITHHOLD",
				).length;
				return `${destination} ${Math.max(0, maxPieces - used)} of ${maxPieces}`;
			})
			.join(", ");
		return (
			`## Undecided\nThe turn ended with no piece and no WITHHOLD for these practices, each with a ` +
			`NOT_MET result: ${notMet.join(", ")}. For each, persist the piece you decided on, or a ` +
			`WITHHOLD with its reason (NO_MATERIAL_CHANGE, ALREADY_SAID, BELOW_BAR), in one report_feedback ` +
			`call. Room left for new items: ${room}; a destination with none left takes only a WITHHOLD. The ` +
			`admitted results and their problems are in \`work/composition/results.json\`. Use tools only from this ` +
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
		const settingsManager = SettingsManager.create(
			CWD,
			AGENT_DIR,
			SANDBOX_SETTINGS_MANAGER_OPTIONS,
		);
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
				practiceVersionId:
					practiceIndex.find((practice) => practice.slug === practiceSlug)?.versionId ?? null,
				model: model.id,
				jobId: typeof taskEnvelope.jobId === "string" ? taskEnvelope.jobId : null,
				workspaceId: isDecimalId(taskEnvelope.workspaceId) ? taskEnvelope.workspaceId : null,
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
									content.type === "toolCall" ||
									(content.type === "text" && !isBlank(content.text)),
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
				extensionFactories: [
					createCodemodeExtension({ mode: "on", models: false }),
					cacheExtension,
				],
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
			return { session: fresh, phase, unsubscribe: subscribeSession(fresh) };
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
		const inputReader = new InputReader({ practicesDir: INPUT_PATHS.practiceScripts });
		const boundModels = readBoundModels();
		const directBudget = new ModelBudget(DIRECT_MODEL_TOKENS, MODEL_CONCURRENCY);
		let sessionStopFailure: Error | null = null;
		const measuringEnded = new AbortController();

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
				`[pi-runner] ${label}: ${slugs.length} practice(s), up to ${budget.modelCalls} calls and ${budget.outputTokens} output tokens (${tokensPerProblem} tokens per result so far)`,
			);
			const phase: SessionPhase = "practice";
			const trace = openTurnTrace(phase, label, budget, {
				owed: () => owedPractices(slugs).length,
				nudge: RECORD_NUDGE,
			});
			const safety = scheduleDeadline(safetyMs, () =>
				stopTurn("safety", "the run is near its safety ceiling — aborting this turn"),
			);
			// Native events may stop a turn while awaited work settles or another session opens.
			const stoppedBy = () => trace.stoppedBy;
			const pastSafety = () => safety.expired() || Date.now() >= measureEnd;
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
				const leads = await resolveLeads(options.leads ?? [], evidenceSources, signal);
				signal.throwIfAborted();
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
					? renderReviewInput(options, leads, taskEnvelope.paths.contextRoot, `\`${fullInput}\``)
					: "";
				const domainReads = noReceipts();
				const assignment = assignedInputs.get(slug);
				const isScan = assignment?.scope.kind === "SCAN";
				const reader = isScan
					? new EvidenceReader(sourcesForPractice(slug, evidenceSources), domainReads)
					: undefined;
				let assignedText = "";
				if (isScan && reader !== undefined) {
					const { path } = assignment.scope.item;
					if (path === undefined) {
						throw new UnratedError("unavailable", "the scan has no assigned file");
					}
					const file = await reader.answer({ op: "read", path }, signal);
					if (file.status !== "FOUND" || file.truncated) {
						throw new UnratedError(
							file.status === "FOUND" ? "too-large" : "unavailable",
							"the assigned file cannot be read completely",
						);
					}
					assignedText = `\n## Assigned scan file\nPath: ${path}\nCommit: ${evidenceSources.headRevision ?? "unavailable"}\nSource: ${TREE_SOURCE}\nSource path: ${evidenceSources.treeSourceFile}\n${file.text}\n`;
				}
				const review: ActiveReview = {
					slug,
					draft: null,
					reads: newSessionReads(),
					reader,
					domainReads,
				};
				activeReview = review;
				activePractice = slug;
				currentTurnSlugs = [slug];
				trace.askedToRecord = false;
				let opened: Awaited<ReturnType<typeof openSession>>;
				try {
					opened = await openSession(
						"practice",
						isScan ? ["read", "submit_result"] : [...PRACTICE_TOOLS, "submit_result"],
						reader === undefined
							? [submitResultTool]
							: [submitResultTool, governedScanRead(reader)],
					);
				} catch (error) {
					activeReview = null;
					activePractice = null;
					throw error;
				}
				const unsubscribeReads = opened.session.subscribe((event) => {
					if (!isScan) {
						noteSessionRead(review.reads, event);
					}
				});
				const { finishTurn } = opened.session.agent;
				let unresolvedRefusal = false;
				opened.session.agent.finishTurn = async (turn, finishSignal) => {
					const decision = await finishTurn?.(turn, finishSignal);
					for (const result of turn.toolResults) {
						if (result.toolName === "submit_result") {
							unresolvedRefusal = result.isError;
						}
					}
					if (!unresolvedRefusal && review.draft !== null) {
						opened.session.clearQueue();
						return { action: "end" };
					}
					return decision ?? undefined;
				};
				const stop = () => {
					void abortSession(opened.session);
				};
				signal.addEventListener("abort", stop, { once: true });
				const text = () =>
					`${practiceTurnText(practiceHeading, slug, brief, prepared)}${assignedText}`;
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
					reads.reads += receipts.reads + domainReads.reads;
					reads.truncatedReads += receipts.truncatedReads + domainReads.truncatedReads;
					reads.failedReads += receipts.failedReads + domainReads.failedReads;
					activeReview = null;
					activePractice = null;
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
				assignedInputs.set(slug, read.inputs);
				if (
					read.inputs.scope.kind === "SCAN" &&
					practiceIndex.find((practice) => practice.slug === slug)?.contextPaths === undefined
				) {
					executions.set(slug, {
						...notReachedOutcome(program, "unavailable"),
						status: "withheld",
						durationMs: 0,
					});
					return;
				}
				const programReads = noReceipts();
				const sessionReads = noReceipts();
				const stopped = new AbortController();
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
						files: jobFiles(read.inputs.kind),
						now: new Date(PROCESS_START_MS).toISOString(),
						deadline: measureEnd - RETURN_MS,
					},
					{
						reader: new EvidenceReader(sourcesForPractice(slug, evidenceSources), programReads),
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
					assignmentReceipts.set(slug, outcome.readReceipts ?? []);
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
						.flatMap((turn) =>
							turn.stoppedBy === null ? [] : [`${turn.label}: ${turn.stoppedBy}`],
						)
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
		// Prepared review input serves measurement only; composition must not receive it.
		rmSync(`${CWD}/${REVIEW_INPUT_ROOT}`, { recursive: true, force: true });
		await admitResults();
		if (compositionRequest) {
			closeUnroutedPrivateDestinations(compositionRequest);
		}
		persistComposedFeedback();
		maybeWriteResultFile();
		if (
			compositionRequest &&
			admittedResults.length > 0 &&
			![...assignedInputs.values()].some((inputs) => inputs.scope.kind === "SCAN")
		) {
			const markRemaining = (reason: CompositionFailureReason) => {
				if (
					compositionRequest.destinations.ON_WORK.enabled &&
					publicResults(admittedResults).length > 0 &&
					composedFeedback.review === null
				) {
					recordCompositionFailure("PUBLIC_REVIEW", reason);
				}
				if (
					PRIVATE_DESTINATIONS.some(
						(destination) => compositionRequest.destinations[destination].enabled,
					) &&
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
					const reviewable = publicResults(admittedResults);
					if (compositionRequest.destinations.ON_WORK.enabled && reviewable.length > 0) {
						await composeReview(compositionRequest, reviewable, notReached, safety);
					}
					if (privatelyComposable().length > 0 && !safety.expired()) {
						await composePrivately(compositionRequest, safety);
					}
					await prepareComposedFeedback(safety);
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
			acceptAdmission(JSON.parse(readFileSync(CONTINUATION_PATH, "utf8")));
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
				PRIVATE_DESTINATIONS.some(
					(destination) => compositionRequest.destinations[destination].enabled,
				) &&
				undecidedPrivatePractices().length > 0
			) {
				recordCompositionFailure("PRIVATE_FEEDBACK", "PRIVATE_NOT_RECOVERED");
			}
			persistComposedFeedback();
			const reviewable = publicResults(admittedResults);
			if (compositionRequest?.destinations.ON_WORK.enabled === true && reviewable.length > 0) {
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
						await prepareComposedFeedback(safety);
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

		// Public composition receives admitted public results in a fresh session without private history.
		async function composeReview(
			request: CompositionRequest,
			reviewable: readonly Record<string, unknown>[],
			notReachedSlugs: readonly string[],
			safety: ReturnType<typeof scheduleDeadline>,
		): Promise<void> {
			const lineNotes = request.onWorkCopyForms.includes("LINE_NOTE");
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
			const restore = () => {
				state.reviewContext = "RESTORING";
				return text;
			};
			const phase: SessionPhase = "public-review";
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
				sessionManager: nativeSessionManager(phase, null),
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
			const owed = () => (state.final ? 0 : Math.max(1, undecidedByReview(reviewable).length));
			const budget = compositionBudget(owed());
			let started = false;
			const wasStarted = () => started;
			const trace = openTurnTrace(phase, "review composition", budget, {
				owed,
				nudge: REVIEW_NUDGE,
			});
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
					const retry = openTurnTrace(phase, "review composition once more", budget, {
						owed,
						nudge: REVIEW_NUDGE,
						endsWhenPaid: true,
					});
					finalTrace = retry;
					try {
						await Promise.race([
							(async () => {
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

		// Private composition starts after measurement in a fresh session with server-routed support.
		async function composePrivately(
			request: CompositionRequest,
			safety: ReturnType<typeof scheduleDeadline>,
		): Promise<void> {
			if (!PRIVATE_DESTINATIONS.some((destination) => request.destinations[destination].enabled)) {
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
					finalTrace = await composeInto(session, opened.phase);
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

			async function composeInto(session: AgentSession, phase: SessionPhase): Promise<TurnTrace> {
				const undecided = undecidedPrivatePractices;
				let started = false;
				const wasStarted = () => started;
				const trace = openTurnTrace(
					phase,
					"composition",
					compositionBudget(privatelyComposable().length),
					{
						owed: () => undecided().length,
						nudge: COMPOSITION_NUDGE,
					},
				);
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
						phase,
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

	await run();
}

if (import.meta.main) {
	await runReviewJob();
}
