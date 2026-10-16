import {
	type CapturedPublicStatement,
	type PublicReviewHistory,
	SAME_WORK_LIMITS,
} from "./pi-review-brief.ts";
import { unwrapToolArguments } from "./pi-tool-validation.ts";

import { isJsonObject as isRecord } from "./work/practice-scripts/lib/core/json.ts";
import { z } from "./work/practice-scripts/lib/core/practice-script.ts";

export const DESTINATIONS = ["ON_WORK", "PRACTICE_PAGE", "CONVERSATION"] as const;
export type Destination = (typeof DESTINATIONS)[number];

/** The developer's practice pages and the mentor conversation; the reviewed work takes a review instead. */
export const PRIVATE_DESTINATIONS = ["PRACTICE_PAGE", "CONVERSATION"] as const;
export type PrivateDestination = (typeof PRIVATE_DESTINATIONS)[number];

export const ACTIONS = ["NEW", "SUPERSEDE", "WITHHOLD"] as const;
export type FeedbackAction = (typeof ACTIONS)[number];

export const WITHHOLD_REASONS = ["NO_MATERIAL_CHANGE", "ALREADY_SAID", "BELOW_BAR"] as const;
export type WithholdReason = (typeof WITHHOLD_REASONS)[number];

/** The reasons that say this work already received the advice, so each names where it was given. */
export const PRIOR_ADVICE_REASONS: ReadonlySet<WithholdReason> = new Set([
	"ALREADY_SAID",
	"NO_MATERIAL_CHANGE",
]);

/** ComposedReview.CONTRACT_VERSION: the server delivers no review from an envelope without it. */
export const REVIEW_CONTRACT_VERSION = 3;

export const REVIEW_TARGETS = { summaryChars: 600, inlineChars: 300, summaryPoints: 3 } as const;

/** Leave room for necessary qualifications; refuse excessive text for correction, never cut it. */
export const REVIEW_LIMITS = {
	summaryChars: REVIEW_TARGETS.summaryChars * 2,
	inlineChars: REVIEW_TARGETS.inlineChars * 2,
	inlineNotes: 30,
} as const;

/** Partial view for coherence checks; pi-runner.ts owns the complete tool schema. */
export interface ComposedFeedbackPiece {
	action?: FeedbackAction;
	destination?: Destination;
	practiceSlug?: string;
	supersedesThreadKey?: string;
	[key: string]: unknown;
}

export interface PreparedFeedbackTarget {
	threadKey: string;
	feedbackId: string;
	destination: Destination;
	practiceSlug: string;
}

/**
 * What a text or a piece rests on, as the server records it: one admitted result, or one problem of it. The
 * composer names either id; the reference always carries the result the id belongs to.
 */
export interface EvidenceRef {
	resultId: string;
	problemId?: string;
}

/** A line note's place: one citation of one problem. A result's own citations never anchor a note. */
export interface ReviewAnchor {
	problemId: string;
	citationIndex: number;
}

export interface ReviewSummary {
	body: string;
	basedOn: EvidenceRef[];
}

export interface ReviewInlineNote {
	body: string;
	basedOn: EvidenceRef[];
	anchor: ReviewAnchor;
}

export interface ReviewWithheld {
	basedOn: EvidenceRef[];
	reason: WithholdReason;
	witnessIds: string[];
}

/**
 * Two cutoffs, two questions. A statement delivered before this work was captured is advice the work could have
 * answered; one delivered later, before the review was composed, was still said here, but nothing about the captured
 * work can respond to it.
 */
export interface PriorAdviceWitness {
	eligibleForPriorAdvice: boolean;
	eligibleForAlreadySaid: boolean;
}

/** What the reviewed work is told: one summary, the notes it places on lines, and what it leaves unsaid. */
export interface ComposedReview {
	summary: ReviewSummary | null;
	inline: ReviewInlineNote[];
	withheld: ReviewWithheld[];
}

export interface ComposedFeedbackEnvelope {
	contractVersion?: number;
	admissionDigest?: string | null;
	results?: unknown[];
	preparedTargets?: PreparedFeedbackTarget[];
	feedback?: ComposedFeedbackPiece[];
	review?: ComposedReview | null;
}

export interface ReviewedProblem {
	problemId: string;
	citations: readonly Record<string, unknown>[];
}

export interface ReviewedResult {
	summaryOnly?: boolean;
	practiceSlug: string;
	outcome: unknown;
	problems: readonly ReviewedProblem[];
}

export interface NamedEvidence<R> {
	result: R;
	ref: EvidenceRef;
}

export function namedEvidence<R extends { problems: readonly { problemId: string }[] }>(
	results: ReadonlyMap<string, R>,
): Map<string, NamedEvidence<R>> {
	const named = new Map<string, NamedEvidence<R>>();
	for (const [resultId, result] of results) {
		named.set(resultId, { result, ref: { resultId } });
		for (const { problemId } of result.problems) {
			named.set(problemId, { result, ref: { resultId, problemId } });
		}
	}
	return named;
}

export function evidenceRefs(
	ids: readonly string[],
	named: ReadonlyMap<string, { ref: EvidenceRef }>,
): EvidenceRef[] {
	const refs = new Map<string, EvidenceRef>();
	for (const id of ids) {
		const ref = named.get(id)?.ref;
		if (ref === undefined) {
			throw new Error(`${id} names no admitted result or problem`);
		}
		refs.set(`${ref.resultId}\u0000${ref.problemId ?? ""}`, ref);
	}
	return [...refs.values()];
}

/** Unevaluated practices support neither positive nor negative claims. */
export function notReachedNote(notReached: readonly string[]): string {
	if (notReached.length === 0) {
		return "";
	}
	const subject =
		notReached.length === 1 ? "one of its practices" : `${notReached.length} of its practices`;
	return (
		`\nThis review did not settle ${subject}: ${notReached.join(", ")}. ` +
		`Say nothing about them, for or against, and do not describe this review as complete.\n\n`
	);
}

// Private feedback requires this practice's admitted Not met result; history alone authorizes no claim.
export function validateFeedbackEvidence(
	primaryPractice: string,
	basedOn: readonly string[],
	results: ReadonlyMap<
		string,
		{ practiceSlug: string; outcome: unknown; problems: readonly { problemId: string }[] }
	>,
): string | null {
	const named = namedEvidence(results);
	const unknown = basedOn.find((id) => !named.has(id));
	if (unknown !== undefined) {
		const own = [...results]
			.filter(([, result]) => result.practiceSlug === primaryPractice)
			.map(
				([id, result]) =>
					`${id}${result.problems.length > 0 ? ` (its problems: ${result.problems.map((problem) => problem.problemId).join(", ")})` : ""}`,
			);
		const hint =
			own.length > 0
				? `the admitted result of ${primaryPractice} is ${own.join(", ")}`
				: `no admitted result belongs to ${primaryPractice}`;
		return `Evidence '${unknown}' does not name an admitted result or problem from this run (basedOn takes a result's \`resultId\` or a problem's \`problemId\`; ${hint}); skipped.`;
	}
	if (
		!basedOn.some((id) => {
			const result = named.get(id)?.result;
			return result?.practiceSlug === primaryPractice && result.outcome === "NOT_MET";
		})
	) {
		return `At least one basedOn id must name the NOT_MET result of the primary practice '${primaryPractice}' or one of its problems; skipped.`;
	}
	if (basedOn.some((id) => named.get(id)?.result.outcome !== "NOT_MET")) {
		return "Every supporting result must be NOT_MET for private feedback; skipped.";
	}
	return null;
}

export function undeliverableFeedback(
	envelope?: ComposedFeedbackEnvelope | null,
): ComposedFeedbackPiece[] {
	const prepared = new Set(
		envelope?.preparedTargets?.map(
			(target) => `${target.threadKey}\u0000${target.destination}\u0000${target.practiceSlug}`,
		),
	);
	return (envelope?.feedback ?? []).filter((piece) => {
		if (piece.action !== "SUPERSEDE") {
			return false;
		}
		const target = piece.supersedesThreadKey;
		return (
			target === undefined ||
			piece.destination === undefined ||
			piece.practiceSlug === undefined ||
			!prepared.has(`${target}\u0000${piece.destination}\u0000${piece.practiceSlug}`)
		);
	});
}

/** One occurrence a PRACTICE_PAGE card may cite, as the server selected it after admission: one NOT_MET result. */
export interface PracticePageSupportOccurrence {
	resultId: string;
	itemKind: string;
	itemId: string;
	outcome: "NOT_MET";
	origin: "EVENT" | "REQUEST" | "SCHEDULE" | "BACKFILL";
	summary: string;
	explanation: string | null;
	evidence: unknown;
	practiceVersionId: string | null;
	recordedAt: string;
}

export const PRACTICE_PAGE_DECISIONS = [
	"ADMIT",
	"FEEDBACK_OFF",
	"GATE_REFUSED",
	"NO_EVIDENCE",
	"UNCORROBORATED",
	"BACKFILL_HELD",
	"RECENTLY_SURFACED",
	"UNATTRIBUTABLE_SUBJECT",
] as const;

export const CONVERSATION_DECISIONS = [
	"ADMIT",
	"FEEDBACK_OFF",
	"MENTOR_UNAVAILABLE",
	"GATE_REFUSED",
	"UNATTRIBUTABLE_SUBJECT",
	"HAS_INLINE_ANCHOR",
	"ALREADY_DELIVERED_ON_WORK",
	"REVIEWER_DEFERRED",
	"BACKFILL_QUIET",
] as const;

const FEEDBACK_STOP_REASONS = [
	"VOLUME_CAPPED",
	"COMPOSER_WITHHELD",
	"NOTHING_NEW",
	"REPEATS_DELIVERED_NOTE",
	"REACTED_DISAGREE",
	"REACTED_NOT_APPLICABLE",
	"CONVERSATION_EXPIRED",
	"DELIVERY_UNCONFIRMED",
	"ITEM_GONE",
	"ITEM_CLOSED",
	"ITEM_MERGED",
	"ITEM_STATE_INELIGIBLE",
	"ISSUE_SNAPSHOT_CHANGED",
	"PUBLIC_SUBJECT_INELIGIBLE",
	"NO_AI",
	"DELIVERY_OFF",
	"PERSON_SUPPRESSED",
	"RECIPIENT_UNAVAILABLE",
	"EMPTY_AFTER_SANITIZE",
	"INSTANCE_SILENCED",
	"FEEDBACK_OFF",
	"WORKSPACE_DISABLED",
	"WORKSPACE_DELIVERY_PAUSED",
	"STALE_ROLLOUT_REVISION",
	"OUTSIDE_CURRENT_SCOPE",
	"APPROVAL_STALE",
	"APPROVAL_NO_LONGER_ELIGIBLE",
	"PRACTICE_REQUIRES_APPROVAL",
	"BACKFILL_QUIET",
	"RESULT_MARKED_INCORRECT",
	"REVIEWED_VERSION_CHANGED",
	"REVIEW_CANCELLED",
	"EVIDENCE_NOT_PERMITTED",
	"NO_EVIDENCE",
	"UNCORROBORATED",
] as const;

type FeedbackStopReason = (typeof FEEDBACK_STOP_REASONS)[number];

export interface PracticePagePracticeSupport {
	practiceSlug: string;
	decision: (typeof PRACTICE_PAGE_DECISIONS)[number];
	stopReason: FeedbackStopReason | null;
	occurrences: PracticePageSupportOccurrence[];
}

export type PracticePageSupport =
	| { state: "COMPLETE"; readAt: string; practices: PracticePagePracticeSupport[] }
	| { state: "UNAVAILABLE" | "REFUSED"; readAt: string; refusal: string | null };

export type ConversationEligibility =
	| {
			state: "COMPLETE";
			readAt: string;
			results: {
				resultId: string;
				decision: (typeof CONVERSATION_DECISIONS)[number];
				stopReason: FeedbackStopReason | null;
			}[];
	  }
	| { state: "UNAVAILABLE" | "REFUSED"; readAt: string };

interface RoutedResult {
	resultId: string;
	practiceSlug: string;
	outcome: unknown;
}

const instantSchema = z
	.string()
	.refine((value) => Number.isFinite(Date.parse(value)), "expected an instant");
const supportOccurrenceSchema = z.strictObject({
	resultId: z.string().min(1),
	itemKind: z.string().min(1),
	itemId: z.string().regex(/^[1-9][0-9]*$/u),
	outcome: z.literal("NOT_MET"),
	origin: z.enum(["EVENT", "REQUEST", "SCHEDULE", "BACKFILL"]),
	summary: z.string(),
	explanation: z
		.string()
		.nullish()
		.transform((value) => value ?? null),
	evidence: z.unknown().default(null),
	practiceVersionId: z
		.string()
		.regex(/^[1-9][0-9]*$/u)
		.nullish()
		.transform((value) => value ?? null),
	recordedAt: instantSchema,
});
const pageSupportSchema = z.discriminatedUnion("state", [
	z.strictObject({
		state: z.literal("COMPLETE"),
		readAt: instantSchema,
		practices: z.array(
			z
				.strictObject({
					practiceSlug: z.string(),
					decision: z.enum(PRACTICE_PAGE_DECISIONS),
					stopReason: z.enum(FEEDBACK_STOP_REASONS).nullable(),
					occurrences: z.array(supportOccurrenceSchema),
				})
				.refine((value) => value.decision !== "GATE_REFUSED" || value.stopReason !== null, {
					path: ["stopReason"],
					message: "a gate refusal requires its recorded reason",
				}),
		),
	}),
	z.strictObject({
		state: z.enum(["UNAVAILABLE", "REFUSED"]),
		readAt: instantSchema,
		refusal: z
			.string()
			.nullish()
			.transform((value) => value ?? null),
	}),
]);
const conversationEligibilitySchema = z.discriminatedUnion("state", [
	z.strictObject({
		state: z.literal("COMPLETE"),
		readAt: instantSchema,
		results: z.array(
			z
				.strictObject({
					resultId: z.string(),
					decision: z.enum(CONVERSATION_DECISIONS),
					stopReason: z.enum(FEEDBACK_STOP_REASONS).nullable(),
				})
				.refine((value) => value.decision !== "GATE_REFUSED" || value.stopReason !== null, {
					path: ["stopReason"],
					message: "a gate refusal requires its recorded reason",
				}),
		),
	}),
	z.strictObject({
		state: z.enum(["UNAVAILABLE", "REFUSED"]),
		readAt: instantSchema,
		results: z.array(z.never()).default([]),
	}),
]);

export function readPracticePageSupport(
	value: unknown,
	current: readonly RoutedResult[],
): PracticePageSupport | null {
	const parsed = pageSupportSchema.safeParse(value);
	if (!parsed.success) {
		return null;
	}
	const support = parsed.data;
	if (support.state !== "COMPLETE") {
		return support;
	}
	const expected = new Set(
		current.filter((result) => result.outcome === "NOT_MET").map((result) => result.practiceSlug),
	);
	for (const practice of support.practices) {
		if (
			!expected.delete(practice.practiceSlug) ||
			(practice.decision !== "ADMIT" && practice.occurrences.length > 0)
		) {
			return null;
		}
	}
	return expected.size === 0 ? support : null;
}

export function readConversationEligibility(
	value: unknown,
	current: readonly RoutedResult[],
): ConversationEligibility | null {
	const parsed = conversationEligibilitySchema.safeParse(value);
	if (!parsed.success) {
		return null;
	}
	const eligibility = parsed.data;
	if (eligibility.state !== "COMPLETE") {
		return { state: eligibility.state, readAt: eligibility.readAt };
	}
	const expected = new Set(
		current.filter((result) => result.outcome === "NOT_MET").map((result) => result.resultId),
	);
	for (const result of eligibility.results) {
		if (!expected.delete(result.resultId)) {
			return null;
		}
	}
	return expected.size === 0 ? eligibility : null;
}

/** Kind and provider URL distinguish work without treating a display number as its identity. */
export function workIdentity(kind: unknown, url: unknown): string | undefined {
	return typeof kind === "string" &&
		kind.trim() !== "" &&
		typeof url === "string" &&
		url.trim() !== ""
		? `${kind}:${url}`
		: undefined;
}

/** Where the server writes its own markers into a comment; a text carrying one would be read as the server's. */
const RESERVED_MARKER = "<!--";

/** The tail a broken JSON envelope leaves on a text: DeveloperTextSanitizer.ENVELOPE_TAIL. */
const ENVELOPE_TAIL = /["'\\]*[}\]]["'\\]+\s*$/u;

/** A list as sent: omitted or null is empty, and anything else that is not a list is null, never read as one item. */
function listOf(value: unknown): unknown[] | null {
	if (Array.isArray(value)) {
		const list: unknown[] = value;
		return list;
	}
	return value === undefined || value === null ? [] : null;
}

function idsOf(value: unknown): string[] | null {
	const parsed = feedbackIdsSchema.safeParse(value);
	return parsed.success ? parsed.data : null;
}

const NOT_AN_ID_LIST =
	"basedOn must be an array of result and problem id strings; a text resting on anything else is refused whole";

type Named = ReadonlyMap<string, NamedEvidence<ReviewedResult>>;

function textProblem(value: unknown, target: number, example: string): string | null {
	if (typeof value !== "string" || value.trim() === "") {
		return "body is required: the complete text, as the developer will read it";
	}
	if (value.length > target * 2) {
		return (
			`body must be at most ${target * 2} characters; this one is ${value.length}. Aim for ${target} characters. ` +
			`Keep the supported concern, evidence, qualifications and next action; remove repetition. ` +
			`Corrected text example (synthetic; use this work's facts): ${JSON.stringify(example)}. Send the whole review again`
		);
	}
	if (value.includes(RESERVED_MARKER)) {
		return "body may not contain an HTML comment (`<!--`); the server writes its own markers there";
	}
	if (ENVELOPE_TAIL.test(value)) {
		return "body ends in quote and bracket characters left over from a JSON envelope; end it on its last sentence";
	}
	return null;
}

function supportProblem(basedOn: readonly string[] | null, named: Named): string | null {
	if (basedOn === null) {
		return NOT_AN_ID_LIST;
	}
	if (basedOn.length === 0) {
		return "basedOn is required: the id of every admitted result or problem this text speaks about";
	}
	const unknown = basedOn.filter((id) => !named.has(id));
	if (unknown.length > 0) {
		return `basedOn names ${unknown.join(", ")}, which is not one of the NOT_MET results or problems this review may rest on (basedOn takes their \`resultId\` or \`problemId\`)`;
	}
	const notConcerns = basedOn.filter((id) => named.get(id)?.result.outcome !== "NOT_MET");
	return notConcerns.length > 0
		? `basedOn names ${notConcerns.join(", ")}, which is not a NOT_MET result or one of its problems. The review on the work speaks only about NOT_MET results and their problems; it never acknowledges a MET result`
		: null;
}

function anchorProblem(
	anchor: ReviewAnchor,
	basedOn: readonly string[],
	named: Named,
	anchors: ReadonlySet<string>,
): string | null {
	const { problemId, citationIndex } = anchor;
	const entry = named.get(problemId);
	if (entry === undefined) {
		return `anchor names ${problemId}, which is not a problem this review may rest on`;
	}
	const problem = entry.result.problems.find((candidate) => candidate.problemId === problemId);
	if (problem === undefined) {
		return `anchor names ${problemId}, which is a result: a note sits on a citation of one of its problems, named by its problemId`;
	}
	if (!basedOn.includes(problemId) && !basedOn.includes(entry.ref.resultId)) {
		return `anchor names ${problemId}, which this note's basedOn does not, itself or through its result`;
	}
	const citation = problem.citations[citationIndex];
	if (citation === undefined) {
		return `${problemId} has no citation ${citationIndex}`;
	}
	if (citation.anchorable !== true) {
		return `citation ${citationIndex} of ${problemId} is not a line of this change, so no note can sit on it`;
	}
	return anchors.has(`${problemId}#${citationIndex}`)
		? `another note already sits on citation ${citationIndex} of ${problemId}`
		: null;
}

export const DISPOSITIONS = ["RAISE", ...WITHHOLD_REASONS] as const;
export type Disposition = (typeof DISPOSITIONS)[number];

function spokenOf(value: Record<string, unknown>, named: Named): Set<string> {
	const summary = isRecord(value.summary) ? (idsOf(value.summary.basedOn) ?? []) : [];
	const notes = (listOf(value.inline) ?? []).flatMap((note) =>
		isRecord(note) ? (idsOf(note.basedOn) ?? []) : [],
	);
	return new Set([...summary, ...notes].flatMap((id) => named.get(id)?.ref.resultId ?? []));
}

function coverageProblems(
	decided: ReadonlyMap<string, Disposition>,
	named: ReadonlySet<string>,
	spoken: ReadonlySet<string>,
	results: ReadonlyMap<string, ReviewedResult>,
): string[] {
	const errors: string[] = [];
	const missing = [...results]
		.filter(([id, result]) => result.outcome === "NOT_MET" && !named.has(id))
		.map(([id]) => id);
	if (missing.length > 0) {
		errors.push(
			`${missing.join(", ")} has no decision; decide each NOT_MET result: RAISE it, or withhold it with its reason`,
		);
	}
	for (const [id, disposition] of decided) {
		if (disposition === "RAISE" && !spoken.has(id)) {
			errors.push(
				`${id} is decided RAISE, and no text speaks about it or its problems; speak about it, or decide again`,
			);
		} else if (disposition !== "RAISE" && spoken.has(id)) {
			errors.push(
				`${id} is withheld as ${disposition}, and a text speaks about it or its problems; a result is raised or withheld, never both`,
			);
		}
	}
	return errors;
}

function inlineSupportProblem(basedOn: string[] | null, named: Named): string | null {
	return basedOn !== null && basedOn.some((id) => named.get(id)?.result.summaryOnly === true)
		? "this note rests on a practice that requires summary location"
		: null;
}

export interface ReviewContext {
	/** The statements a withholding may name as where this work already received the advice. */
	witnesses: ReadonlyMap<string, PriorAdviceWitness>;
}

/** ALREADY_SAID needs a recorded communication; NO_MATERIAL_CHANGE also needs advice available at capture. */
function witnessProblems(
	reason: WithholdReason,
	witnessIds: readonly string[] | null,
	witnesses: ReadonlyMap<string, PriorAdviceWitness>,
): string[] {
	if (witnessIds === null) {
		return ["witnessIds must be an array of witnessId strings from what was already said"];
	}
	const problems: string[] = [];
	const unknown = witnessIds.filter((id) => !witnesses.has(id));
	if (unknown.length > 0) {
		problems.push(
			`witnessIds names ${unknown.join(", ")}, which is not a statement shown under what was already said on this work`,
		);
	}
	const flag = reason === "ALREADY_SAID" ? "eligibleForAlreadySaid" : "eligibleForPriorAdvice";
	const ineligible = witnessIds.filter(
		(id) => witnesses.has(id) && witnesses.get(id)?.[flag] !== true,
	);
	if (ineligible.length > 0) {
		problems.push(
			`witnessIds names ${ineligible.join(", ")}, which is shown as context but cannot stand as advice for ${reason} (${flag} is false)`,
		);
	}
	if (PRIOR_ADVICE_REASONS.has(reason) && witnessIds.length === 0) {
		problems.push(
			`${reason} names in witnessIds where this work already received the advice: the witnessId of a statement marked ${flag}; without one, raise it or choose another reason`,
		);
	}
	return problems;
}

/** The summary as sent, whole, or every reason it cannot be stored; an omitted or null summary is none. */
const feedbackIdsSchema = z.array(z.string().trim().min(1));
const reviewSummarySchema = z.strictObject({ body: z.string(), basedOn: feedbackIdsSchema });
const reviewDecisionSchema = z.strictObject({
	resultId: z.string().trim().min(1),
	disposition: z.enum(DISPOSITIONS),
	witnessIds: feedbackIdsSchema.nullish().transform((value) => value ?? []),
});
const inlineNoteSchema = z.strictObject({
	body: z.string(),
	basedOn: feedbackIdsSchema,
	anchor: z.strictObject({
		problemId: z.string().trim().min(1),
		citationIndex: z.union([
			z.int().nonnegative(),
			z.string().trim().regex(/^\d+$/u).transform(Number),
		]),
	}),
});
const reviewToolInputSchema = z.strictObject({
	decisions: z.array(reviewDecisionSchema),
	summary: reviewSummarySchema.nullish(),
	inline: z.array(inlineNoteSchema).nullish(),
});
const schemaErrors = (issues: readonly z.core.$ZodIssue[], prefix: string) =>
	issues.map(
		(issue) =>
			`${prefix}${issue.path.length === 0 ? "" : `.${issue.path.join(".")}`}: ${issue.message}`,
	);

function readSummary(
	value: z.infer<typeof reviewSummarySchema> | null | undefined,
	named: Named,
): { summary: ReviewSummary | null; errors: string[] } {
	if (value === undefined || value === null) {
		return { summary: null, errors: [] };
	}
	const { body, basedOn } = value;
	const problems = [
		textProblem(
			body,
			REVIEW_TARGETS.summaryChars,
			"The description names the change but omits its purpose. Add the reason so reviewers can judge the scope.",
		),
		supportProblem(basedOn, named),
	].filter((problem): problem is string => problem !== null);
	if (problems.length > 0) {
		return { summary: null, errors: problems.map((problem) => `summary: ${problem}`) };
	}
	return { summary: { body, basedOn: evidenceRefs(basedOn, named) }, errors: [] };
}

function readDecisions(
	value: z.infer<typeof reviewDecisionSchema>[],
	results: ReadonlyMap<string, ReviewedResult>,
	evidence: Named,
	witnesses: ReadonlyMap<string, PriorAdviceWitness>,
): { decided: Map<string, Disposition>; named: Set<string>; errors: string[] } {
	const decided = new Map<string, Disposition>();
	const named = new Set<string>();
	const errors: string[] = [];
	for (const [index, decision] of value.entries()) {
		const label = `decisions #${index + 1}`;
		const { resultId: id, disposition, witnessIds } = decision;
		const problems: string[] = [];
		const result = results.get(id);
		const parent = evidence.get(id)?.ref.resultId;
		if (result === undefined) {
			problems.push(
				parent === undefined
					? `${id} is not one of the results this review may rest on`
					: `${id} is a problem of ${parent}; a decision names its result's resultId`,
			);
		} else if (result.outcome !== "NOT_MET") {
			problems.push(`${id} is not a NOT_MET result; only a result with problems takes a decision`);
		} else if (named.has(id)) {
			problems.push(`${id} already has a decision; each NOT_MET result takes one`);
		}
		if (result?.outcome === "NOT_MET") {
			named.add(id);
		}
		if (disposition === "RAISE") {
			if (witnessIds.length > 0) {
				problems.push("a raised result names no witness");
			}
		} else {
			problems.push(...witnessProblems(disposition, witnessIds, witnesses));
		}
		errors.push(...problems.map((problem) => `${label}: ${problem}`));
		if (problems.length === 0) {
			decided.set(id, disposition);
		}
	}
	return { decided, named, errors };
}

/** Validate before the SDK can coerce a malformed text or list into an empty review. */
export function prepareReviewArguments(value: unknown): Record<string, unknown> {
	const parsed = reviewToolInputSchema.parse(unwrapToolArguments(value));
	return { ...parsed, summary: parsed.summary ?? null, inline: parsed.inline ?? [] };
}

/** Store the review whole: a refused part cannot be dropped while retaining decisions written with it. */
export function readReview(
	value: unknown,
	results: ReadonlyMap<string, ReviewedResult>,
	lineNotes: boolean,
	context: ReviewContext,
): { review: ComposedReview } | { errors: string[] } {
	const parsed = reviewToolInputSchema.safeParse(value);
	if (!parsed.success) {
		return { errors: schemaErrors(parsed.error.issues, "review") };
	}
	const input = parsed.data;
	const errors: string[] = [];
	const named = namedEvidence(results);
	const decisions = readDecisions(input.decisions, results, named, context.witnesses);
	errors.push(...decisions.errors);

	const read = readSummary(input.summary, named);
	errors.push(...read.errors);
	const { summary } = read;

	const notes = input.inline ?? [];
	if (notes.length > REVIEW_LIMITS.inlineNotes) {
		errors.push(
			`inline: at most ${REVIEW_LIMITS.inlineNotes} line notes; this review has ${notes.length}`,
		);
	}
	if (notes.length > 0 && !lineNotes) {
		errors.push("inline: this work has no lines to place a note on; say it in the summary");
	}
	const inline: ReviewInlineNote[] = [];
	const anchors = new Set<string>();
	for (const [index, raw] of notes.entries()) {
		const label = `inline #${index + 1}`;
		const {
			body,
			basedOn,
			anchor: { problemId, citationIndex },
		} = raw;
		const problems = [
			textProblem(
				body,
				REVIEW_TARGETS.inlineChars,
				"This branch uses the token without a check. Validate it before use so an invalid token cannot reach the request.",
			),
			supportProblem(basedOn, named),
		].filter((problem): problem is string => problem !== null);
		const placementProblem = inlineSupportProblem(basedOn, named);
		if (placementProblem !== null) {
			problems.push(placementProblem);
		}
		const anchorError = anchorProblem({ problemId, citationIndex }, basedOn, named, anchors);
		if (anchorError !== null) {
			problems.push(anchorError);
		}
		errors.push(...problems.map((problem) => `${label}: ${problem}`));
		if (problems.length === 0) {
			anchors.add(`${problemId}#${citationIndex}`);
			inline.push({
				body,
				basedOn: evidenceRefs([...basedOn, problemId], named),
				anchor: { problemId, citationIndex },
			});
		}
	}

	errors.push(
		...coverageProblems(decisions.decided, decisions.named, spokenOf(input, named), results),
	);
	const withheld: ReviewWithheld[] = [...decisions.decided].flatMap(([id, disposition]) =>
		disposition === "RAISE"
			? []
			: [
					{
						basedOn: [{ resultId: id }],
						reason: disposition,
						witnessIds:
							input.decisions.find((decision) => decision.resultId === id)?.witnessIds ?? [],
					},
				],
	);
	return errors.length > 0 ? { errors } : { review: { summary, inline, withheld } };
}

export function decidedByReview(review: ComposedReview | null | undefined): Set<string> {
	if (!review) {
		return new Set();
	}
	return new Set(
		[
			...(review.summary?.basedOn ?? []),
			...review.inline.flatMap((note) => note.basedOn),
			...review.withheld.flatMap((decision) => decision.basedOn),
		].map((ref) => ref.resultId),
	);
}

export function publicResults(
	results: readonly Record<string, unknown>[],
): Record<string, unknown>[] {
	return results
		.filter((result) => result.publicEligible === true && result.outcome === "NOT_MET")
		.map(({ citations, example, problems, ...rest }) => ({
			...rest,
			...(example === undefined
				? {}
				: {
						example: isRecord(example)
							? { ...example, citation: withoutDigest(example.citation) }
							: example,
					}),
			citations: withoutDigests(citations),
			problems: (Array.isArray(problems) ? problems : []).map((problem: unknown) =>
				isRecord(problem) ? { ...problem, citations: withoutDigests(problem.citations) } : problem,
			),
		}));
}

function withoutDigest(citation: unknown): unknown {
	if (!isRecord(citation)) {
		return citation;
	}
	const { verification: _verification, ...shown } = citation;
	return shown;
}

function withoutDigests(citations: unknown): unknown[] {
	return (Array.isArray(citations) ? citations : []).map(withoutDigest);
}

/** The native outcome-specific grounds of an abstention; generic prose is not a decided assessment. */
export function qualificationEvidence(result: {
	outcome: unknown;
	evidence?: unknown;
}): Record<string, unknown> {
	const branch = result.outcome === "COULD_NOT_DETERMINE" ? "openQuestions" : "inapplicability";
	const evidence = isRecord(result.evidence) ? result.evidence : {};
	return { [branch]: evidence[branch] };
}

/**
 * The abstentions admission marked publicEligible, each as the limit it recorded with the lines it cited: a bound on
 * what the review may claim, distinct from the practices it never reached. They carry no id: no text rests on them,
 * and the review decides nothing about them.
 */
export function publicQualifications(
	results: readonly Record<string, unknown>[],
): Record<string, unknown>[] {
	return results
		.filter(
			(result) =>
				result.publicEligible === true &&
				(result.outcome === "DOES_NOT_APPLY" || result.outcome === "COULD_NOT_DETERMINE"),
		)
		.map((result) => ({
			practiceSlug: result.practiceSlug,
			outcome: result.outcome,
			evidence: qualificationEvidence({ outcome: result.outcome, evidence: result.evidence }),
			citations: withoutDigests(result.citations),
		}));
}

export interface OwnPriorFeedback {
	/** Null when the history names no feedback id: such an entry is context only. */
	witnessId: string | null;
	id?: unknown;
	itemVersion?: unknown;
	basedOn?: unknown;
	deliveredAt: unknown;
	body: unknown;
	recordedClaimCurrentness: unknown;
	withdrawn: unknown;
	wordsUnavailable?: "COPY_AUTHORITY_UNAVAILABLE";
	eligibleForPriorAdvice: boolean;
	eligibleForAlreadySaid: boolean;
}

export interface OwnHistoryOmissions {
	oversizedEntries: number;
	budgetEntries: number;
}

export interface OwnPriorFeedbackView {
	feedback: OwnPriorFeedback[];
	omissions: OwnHistoryOmissions;
}

function ownHistoryText(
	feedback: readonly OwnPriorFeedback[],
	omissions?: OwnHistoryOmissions,
	readAt?: string,
): string {
	const asRead = readAt === undefined ? "" : `, as read at ${readAt}`;
	const shown =
		feedback.length === 0
			? `No same-work delivered feedback is shown here${asRead}.\n`
			: `What Hephaestus delivered on this work${asRead}:\n\`\`\`json\n${JSON.stringify({ alreadySaid: feedback }, null, 1)}\n\`\`\`\n`;
	const omitted =
		omissions !== undefined && (omissions.oversizedEntries > 0 || omissions.budgetEntries > 0)
			? `Prior feedback omitted whole: ${omissions.oversizedEntries} entries exceed the per-entry limit; ${omissions.budgetEntries} exceed the total history limit. Their contents are not shown and cannot be prior-advice witnesses.\n`
			: "";
	return `${shown}${omitted}`;
}

const FEEDBACK_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iu;

function deliveredBy(deliveredAt: unknown, capturedAt: string | null): boolean {
	if (typeof deliveredAt !== "string" || capturedAt === null) {
		return false;
	}
	const delivered = Date.parse(deliveredAt);
	const captured = Date.parse(capturedAt);
	return Number.isFinite(delivered) && Number.isFinite(captured) && delivered <= captured;
}

/**
 * What was already said on this very piece of work, from the feedback history: only the review on the work, never
 * what reached the developer's own pages or conversations, and never anything about other work. Nothing missing is
 * filled in: an entry without an id, a time or a body is context, never prior advice.
 */
export function priorPublicFeedback(
	history: unknown,
	thisWork: string | undefined,
	capturedAt: string | null,
	limits: { sourceChars: number; totalChars: number } = SAME_WORK_LIMITS,
	readAt: string | null = capturedAt,
): OwnPriorFeedbackView {
	const omissions: OwnHistoryOmissions = { oversizedEntries: 0, budgetEntries: 0 };
	if (thisWork === undefined || !isRecord(history) || !Array.isArray(history.feedback)) {
		return { feedback: [], omissions };
	}
	const projected = history.feedback.flatMap((entry: unknown): OwnPriorFeedback[] => {
		if (!isRecord(entry) || entry.destination !== "ON_WORK" || entry.publicEligible !== true) {
			return [];
		}
		const item = isRecord(entry.item) ? entry.item : {};
		if (workIdentity(item.kind, item.url) !== thisWork) {
			return [];
		}
		const { id, itemVersion, basedOn, deliveredAt, body, recordedClaimCurrentness, withdrawn } =
			entry;
		const witnessId = typeof id === "string" && FEEDBACK_ID.test(id) ? `feedback:${id}` : null;
		// Stale words still record what was said here, so they can show novelty; only current ones stand as advice.
		const recorded =
			witnessId !== null &&
			typeof body === "string" &&
			body.trim() !== "" &&
			(recordedClaimCurrentness === "CURRENT" || recordedClaimCurrentness === "STALE") &&
			withdrawn !== true;
		return [
			{
				witnessId,
				...(id === undefined ? {} : { id }),
				...(itemVersion === undefined ? {} : { itemVersion }),
				...(basedOn === undefined ? {} : { basedOn }),
				deliveredAt,
				body,
				recordedClaimCurrentness,
				withdrawn,
				...(entry.wordsUnavailable === "COPY_AUTHORITY_UNAVAILABLE"
					? { wordsUnavailable: "COPY_AUTHORITY_UNAVAILABLE" as const }
					: {}),
				eligibleForPriorAdvice:
					recorded &&
					recordedClaimCurrentness === "CURRENT" &&
					deliveredBy(deliveredAt, capturedAt),
				eligibleForAlreadySaid: recorded && deliveredBy(deliveredAt, readAt),
			},
		];
	});
	const feedback: OwnPriorFeedback[] = [];
	// Reserve the count-only omission notice before adding whole entries; omitted identities are never rendered.
	const reserved = { oversizedEntries: projected.length, budgetEntries: projected.length };
	for (const entry of projected) {
		if (JSON.stringify(entry, null, 1).length > limits.sourceChars) {
			omissions.oversizedEntries += 1;
		} else if (ownHistoryText([...feedback, entry], reserved).length > limits.totalChars) {
			omissions.budgetEntries += 1;
		} else {
			feedback.push(entry);
		}
	}
	return { feedback, omissions };
}

export function priorAdviceWitnesses(
	own: readonly OwnPriorFeedback[],
	captured: readonly CapturedPublicStatement[],
): Map<string, PriorAdviceWitness> {
	const witnesses = new Map<string, PriorAdviceWitness>();
	for (const statement of own) {
		if (statement.witnessId !== null) {
			witnesses.set(statement.witnessId, {
				eligibleForPriorAdvice: statement.eligibleForPriorAdvice,
				eligibleForAlreadySaid: statement.eligibleForAlreadySaid,
			});
		}
	}
	for (const statement of captured) {
		// The captured discussion has one cutoff: what it holds was said by the time the work was captured.
		witnesses.set(statement.witnessId, {
			eligibleForPriorAdvice: statement.eligibleForPriorAdvice,
			eligibleForAlreadySaid: statement.eligibleForPriorAdvice,
		});
	}
	return witnesses;
}

/** What report_review tells the model it does. The rules are applied by readReview, with every reason at once. */
export const REVIEW_TOOL_DESCRIPTION =
	"Store the final review on this piece of work. First decide each NOT_MET result: RAISE it, or withhold it " +
	"with your reason and, for advice this work already received, the statement that gave it. Then write the summary " +
	"comment and any notes placed on lines of the change: they speak about every raised result, itself or through " +
	"its problems, and no withheld one. Each body is published whole as written, with provider safety formatting and a fixed disclosure; nothing is " +
	"assembled from fragments. An accepted call is final and ends the composition. A missing or contradicted decision, " +
	"invalid support, eligibility or location, or a witness that cannot stand as the advice refuses the whole " +
	"review, with every reason, so it can be corrected and sent again. " +
	`Aim for at most ${REVIEW_TARGETS.summaryChars} characters and ${REVIEW_TARGETS.summaryPoints} points in the summary, ` +
	`${REVIEW_TARGETS.inlineChars} characters and one local point per line note. Each point uses at most two short sentences: what, then why or next action. ` +
	"No headings, internal labels or citation coordinates in the published text; refer to evidence in natural words or a short exact quote. " +
	`A summary over ${REVIEW_LIMITS.summaryChars} characters or a note over ${REVIEW_LIMITS.inlineChars} is refused for correction, never truncated. ` +
	'Input example: {"decisions":[{"resultId":"<admitted NOT_MET resultId>","disposition":"RAISE"}],"summary":{"basedOn":["<same resultId>"],"body":"Explain the supported concern and its remedy."},"inline":[]}. ' +
	"For a quiet review, use summary:null, inline:[] and a supported withholding decision for each result. Unused inline and witnessIds lists may be null or omitted.";

/**
 * How the review is written. It ends the opening turn, after the criteria, so reference material is followed by the
 * writing task; the composer prompt points here.
 */
export const WRITE_CONTRACT =
	"## Writing the review\n" +
	"Write from the results and problems each text rests on, within their qualifications. A declared affordance supports its bounded " +
	"benefit, not an unobserved runtime or test outcome. Keep each remedy on its recorded gap and leave unrelated " +
	"behavior as it is; make another change a prerequisite only when the evidence establishes that dependency. " +
	"You are not told whether the work is ready, so do not approve it, call it ready or blocked, or set conditions " +
	"for merging it. Timing the evidence itself warrants is fine: a committed secret is removed and rotated before " +
	"anyone relies on the history.\n\n" +
	`Aim for at most ${REVIEW_TARGETS.summaryChars} characters in the whole summary, with at most ${REVIEW_TARGETS.summaryPoints} points, ` +
	`and ${REVIEW_TARGETS.inlineChars} characters per line note. Each point uses at most two short sentences: what is wrong, ` +
	"then why it matters or what to do next. A small point needs a short explanation. Keep the evidence and its qualifications; " +
	"cut repetition and withhold lesser concerns rather than dropping a material limit to fit. " +
	"Write plain prose or a short list without headings or internal labels such as Situation:, Capability: or Evidence:. " +
	"Say ‘the description’s closing line’ or use a short exact quote, not ‘description L2’, ‘description L16’ or citation indices. " +
	"Use basedOn and anchors to identify the exact evidence; leave its coordinates in the source evidence.\n\n" +
	"Make each point fully once in this review:\n" +
	"- A line note is one self-contained point about the code at its anchor: that local concern with its action and " +
	"evidence. It is read alone, so it says its point completely. Several practices " +
	"may support it when they describe that one event; it does not collect the review's other points.\n" +
	"- The summary orients the reader: the guidance its raised NOT_MET results warrant, most important first. It " +
	"carries every ask that is not " +
	"about one place in the code, such as the description, a reply to a reviewer or a work-wide change, unless it " +
	"forms one point with the code at a line. When a point has no useful line note, the summary carries that point " +
	"completely. It may name or locate a line note's topic, and then names that note's " +
	"results or problems in basedOn, but it does not restate the note's diagnosis or action.\n" +
	"- The review speaks only about NOT_MET results and their problems. It never acknowledges or praises a MET " +
	"result, and it does not use praise to cushion a concern. When no NOT_MET result is raised, the review says " +
	"nothing: the summary is null and there are no line notes.\n\n" +
	"One result or problem may support notes at several places, and also the summary's overview of them.";

function publicNotMetPractices(reviewable: readonly Record<string, unknown>[]): string[] {
	return [
		...new Set(
			reviewable
				.filter((result) => result.publicEligible === true && result.outcome === "NOT_MET")
				.map((result) => String(result.practiceSlug)),
		),
	];
}

export const STANDARD_REFERENCE =
	"Reference, whole as staged: each standard explains what its practice's recorded results were assessed " +
	"against and the responses it accepts, and qualifies whether and how those results and their problems may " +
	"be communicated. It is not a request to assess the work again and raises no concern of its own.";

/** Each concern's complete grounds, limits, standard and candidate communication references, grouped by practice. */
export function notMetReference(
	reviewable: readonly Record<string, unknown>[],
	stagedCriteria: (practiceSlug: string) => string | null,
	practices: readonly ReviewPractice[] = [],
	history: readonly OwnPriorFeedback[] = [],
	itemVersion: string | null = null,
): string {
	const concerns = publicNotMetPractices(reviewable).map((slug) => {
		const results = reviewable.filter(
			(entry) =>
				entry.publicEligible === true && entry.outcome === "NOT_MET" && entry.practiceSlug === slug,
		);
		return `${practiceStandard(slug, stagedCriteria).text}${practiceReference(slug, results, practices, history, itemVersion)}`;
	});
	return concerns.length === 0
		? "No public NOT_MET result was admitted.\n"
		: `${STANDARD_REFERENCE}\n${concerns.join("\n")}`;
}

/** Association by captured practice metadata only: these references decide neither a match nor novelty. */
function practiceReference(
	slug: string,
	results: readonly Record<string, unknown>[],
	practices: readonly ReviewPractice[],
	history: readonly OwnPriorFeedback[],
	itemVersion: string | null,
): string {
	const practice = practices.find((entry) => entry.slug === slug);
	const candidatePriorWitnesses = history.flatMap((entry) => {
		if (
			entry.witnessId === null ||
			(!entry.eligibleForAlreadySaid && !entry.eligibleForPriorAdvice) ||
			!Array.isArray(entry.basedOn) ||
			!entry.basedOn.some((support: unknown) => isRecord(support) && support.practiceSlug === slug)
		) {
			return [];
		}
		return [
			{
				witnessId: entry.witnessId,
				itemVersion: typeof entry.itemVersion === "string" ? entry.itemVersion : null,
				basedOn: entry.basedOn.filter(
					(support: unknown) => isRecord(support) && support.practiceSlug === slug,
				),
				eligibleForAlreadySaid: entry.eligibleForAlreadySaid,
				eligibleForPriorAdvice: entry.eligibleForPriorAdvice,
			},
		];
	});
	return `\`\`\`json\n${JSON.stringify(
		{
			itemVersion,
			...(practice === undefined
				? {}
				: {
						practice: {
							slug: practice.slug,
							name: practice.name,
							...(practice.versionId === undefined ? {} : { versionId: practice.versionId }),
							knownLimitations: practice.knownLimitations,
						},
					}),
			results,
			candidatePriorWitnesses,
		},
		null,
		1,
	)}\n\`\`\`\n`;
}

/**
 * One practice's staged criteria read once, and whether they are shown whole. A read that is missing, fails or holds
 * no text never counts as the standard; a blank one is still shown as staged.
 */
export function practiceStandard(
	slug: string,
	stagedCriteria: (practiceSlug: string) => string | null,
): { text: string; whole: boolean } {
	let criteria: string | null;
	try {
		criteria = stagedCriteria(slug);
	} catch {
		return {
			text: `### Criteria of \`${slug}\` — could not be read; nothing is known about them here\n`,
			whole: false,
		};
	}
	return {
		text: criteriaBlock(slug, criteria),
		whole: criteria !== null && criteria.trim() !== "",
	};
}

/** One practice's staged criteria, whole and fenced so their own code blocks cannot close it; never cut or rewritten. */
function criteriaBlock(slug: string, criteria: string | null): string {
	if (criteria === null) {
		return `### Criteria of \`${slug}\` — not available for this review; nothing is known about them here\n`;
	}
	if (criteria.length === 0) {
		return `### Criteria of \`${slug}\` — staged empty; no standard is available here\n`;
	}
	const longestRun = Math.max(0, ...[...criteria.matchAll(/`+/gu)].map((run) => run[0].length));
	const fence = "`".repeat(Math.max(3, longestRun + 1));
	return `### Criteria of \`${slug}\`\n${fence}markdown\n${criteria}\n${fence}\n`;
}

/** Offered before the body to orient generation; field order is not enforced. */
const SUPPORT_FIRST =
	"Choose these before writing the body: the id of every NOT_MET result or problem behind a concern this body " +
	"states, and no other: a result's resultId, or a problem's problemId for one located problem. " +
	"One named by another body does not support this one. Then write the body from exactly these.";

/** An id list the schema can offer: the ids themselves when there are any, since an empty enum is invalid. */
function idList(ids: readonly string[], description: string) {
	return {
		type: "array",
		minItems: 1,
		items: ids.length > 0 ? { type: "string", enum: [...ids] } : { type: "string" },
		description,
	};
}

export function reviewToolParameters(
	results: ReadonlyMap<string, ReviewedResult>,
	lineNotes: boolean,
	eligibleWitnesses: readonly string[],
) {
	const concerns = [...results].filter(([, result]) => result.outcome === "NOT_MET");
	if (concerns.length === 0) {
		throw new Error("report_review needs at least one NOT_MET result to rest on");
	}
	const notMet = concerns.map(([id]) => id);
	const restable = concerns.flatMap(([id, result]) => [
		id,
		...result.problems.map((problem) => problem.problemId),
	]);
	const anchorable = lineNotes
		? concerns.flatMap(([, result]) =>
				result.problems
					.filter((problem) => problem.citations.some((citation) => citation.anchorable === true))
					.map((problem) => problem.problemId),
			)
		: [];
	return {
		type: "object",
		additionalProperties: false,
		required: ["decisions", "summary"],
		properties: {
			decisions: {
				type: "array",
				minItems: notMet.length,
				maxItems: notMet.length,
				description:
					"Decided first: one decision for each NOT_MET result. RAISE means the " +
					"texts below speak about it or its problems; any other disposition withholds it, and no text " +
					"speaks about it or its problems.",
				items: {
					type: "object",
					additionalProperties: false,
					required: ["resultId", "disposition"],
					properties: {
						resultId: { type: "string", enum: notMet },
						disposition: { type: "string", enum: [...DISPOSITIONS] },
						witnessIds: {
							type: "array",
							...(eligibleWitnesses.length > 0
								? { items: { type: "string", enum: [...eligibleWitnesses] } }
								: { maxItems: 0, items: { type: "string" } }),
							description:
								"For ALREADY_SAID or NO_MATERIAL_CHANGE: the witnessId of each statement under what was " +
								"already said on this work that gave this advice. ALREADY_SAID takes a statement marked " +
								"eligibleForAlreadySaid; NO_MATERIAL_CHANGE one marked eligibleForPriorAdvice. Checked, " +
								"never stored.",
						},
					},
				},
			},
			summary: {
				type: ["object", "null"],
				additionalProperties: false,
				required: ["basedOn", "body"],
				description:
					"The one overview comment on the work: the guidance its raised NOT_MET results warrant, and " +
					"points not carried by line notes. It never acknowledges a MET result. Send null when nothing " +
					"on this work earns a comment of its own.",
				properties: {
					basedOn: idList(
						restable,
						`${SUPPORT_FIRST} If any of them may not go out, the whole comment stays unsaid.`,
					),
					body: {
						type: "string",
						minLength: 1,
						maxLength: REVIEW_LIMITS.summaryChars,
						description:
							`The whole comment in Markdown, from the results and problems in basedOn. Aim for at most ${REVIEW_TARGETS.summaryChars} characters ` +
							`and ${REVIEW_TARGETS.summaryPoints} points; at most two short sentences per point: what, then why or next action. No headings, internal labels or citation coordinates.`,
					},
				},
			},
			inline: {
				type: "array",
				maxItems: anchorable.length > 0 ? REVIEW_LIMITS.inlineNotes : 0,
				description:
					"Notes intended for cited lines of the change, each one self-contained point about the code at " +
					"its line. Each location can fail independently.",
				items: {
					type: "object",
					additionalProperties: false,
					required: ["basedOn", "body", "anchor"],
					properties: {
						basedOn: idList(
							restable,
							`${SUPPORT_FIRST} It includes the anchor's problem or that problem's result.`,
						),
						body: {
							type: "string",
							minLength: 1,
							maxLength: REVIEW_LIMITS.inlineChars,
							description:
								`The whole note in Markdown, from the results and problems in basedOn. Aim for at most ${REVIEW_TARGETS.inlineChars} characters, ` +
								"one point about this line, at most two short sentences: what, then why or next action. No headings, internal labels or citation coordinates.",
						},
						anchor: {
							type: "object",
							additionalProperties: false,
							required: ["problemId", "citationIndex"],
							description:
								"The line, as one citation marked anchorable of a problem that basedOn names, itself or " +
								"through its result. A result's own citations take no note.",
							properties: {
								problemId:
									anchorable.length > 0 ? { type: "string", enum: anchorable } : { type: "string" },
								citationIndex: { type: "integer", minimum: 0 },
							},
						},
					},
				},
			},
		},
	};
}

export interface ReviewPractice {
	slug: string;
	name: string;
	whyItMatters?: string;
	knownLimitations: readonly string[];
	versionId?: string;
}

/** Everything the review on the work is composed from; nothing else reaches its session. */
export interface ReviewTurnInput {
	sameWork: string;
	results: readonly Record<string, unknown>[];
	/** The limits recorded by the abstentions of this work, from publicQualifications: context, never support. */
	qualifications: readonly Record<string, unknown>[];
	/** What Hephaestus already said on this same work, from priorPublicFeedback. */
	alreadySaid: readonly OwnPriorFeedback[];
	ownHistoryOmissions?: OwnHistoryOmissions;
	/** When the delivered feedback above was read, just before this composition; absent when it was not read again. */
	ownHistoryReadAt?: string;
	/** The bounded captured public discussion of this same work, from buildPublicReviewHistory. */
	captured: PublicReviewHistory;
	practices: readonly ReviewPractice[];
	/** Practices this review did not reach at all. */
	notReached: readonly string[];
	/** Whether this work has lines a note can be placed on. */
	lineNotes: boolean;
	/** One practice's staged criteria, whole; null when none were staged. */
	stagedCriteria: (practiceSlug: string) => string | null;
}

export function buildReviewTurn(input: ReviewTurnInput): string {
	const own = ownHistoryText(input.alreadySaid, input.ownHistoryOmissions, input.ownHistoryReadAt);
	const others =
		input.captured.sources.length === 0 && input.captured.omitted === undefined
			? "What people and tools said on this work was not part of this capture, so it is unknown.\n"
			: `Captured public discussion on this work; omitted sources remain unknown:\n\`\`\`json\n${JSON.stringify(input.captured, null, 1)}\n\`\`\`\n`;
	const said = `${own}${others}An \`ALREADY_SAID\` decision names the \`witnessId\` of a statement marked \`eligibleForAlreadySaid\`; a \`NO_MATERIAL_CHANGE\` decision one marked \`eligibleForPriorAdvice\`; any other statement is context only. \`eligibleForAlreadySaid\` alone establishes communication by the read time, not that the captured work received or responded to it.\n`;

	const qualifications =
		input.qualifications.length === 0
			? ""
			: `### Recorded limits of this review
Practices this review looked at and did not decide, or found not applicable, each with the limit it recorded and the lines it cited. They bound what the review may claim. They are not concerns: no text rests on them, and nothing is decided about them here.
\`\`\`json
${JSON.stringify({ results: input.qualifications }, null, 1)}
\`\`\`
`;
	const location = input.lineNotes
		? "- Line notes: a note's `anchor` names one citation marked `anchorable` of a problem, by the " +
			"problem's `problemId` and the citation's `citationIndex`; a result's own citations take no note. " +
			"On GitHub it is a review comment on that line; on GitLab it is its own comment " +
			"headed by a link to the line. Each body stands on its own, and each location can fail independently.\n"
		: "- This work has no lines a note can sit on; everything goes in the summary.\n";
	const concerns = notMetReference(
		input.results,
		input.stagedCriteria,
		input.practices,
		input.alreadySaid,
		input.captured.itemVersion ?? null,
	);
	return `## The review to write
The measurement of this work is finished. The captured work establishes its purpose and evidence. Earlier public communication records what was said, not current verification. Each concern below carries its recorded grounds and standard.

### The reviewed work, as captured
${input.sameWork}
### Already said on this work
${said}
### Concerns to decide
Candidate prior witness references associate captured practice metadata only. Read their complete statements above to judge whether the advice matches, remains warranted, or is worth repeating; a missing association does not establish novelty.
${concerns}
${qualifications}
### Where the words go
- The summary: one comment on the work.
${location}
${notReachedNote(input.notReached)}Store the whole review with one report_review call. It decides first: each NOT_MET result is RAISE, and a text speaks about it or its problems, or withheld with its reason, and no text speaks about it or its problems. Writing nothing for the work is a decision too: it is still one final report_review call.

${WRITE_CONTRACT}`;
}
