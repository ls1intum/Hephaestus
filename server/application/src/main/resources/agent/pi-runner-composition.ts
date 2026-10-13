import {
	type CapturedPublicStatement,
	type PublicReviewHistory,
	SAME_WORK_LIMITS,
} from "./pi-review-brief.ts";

export const CHANNELS = ["IN_CONTEXT", "IN_APP", "IN_CHAT"] as const;
export type Channel = (typeof CHANNELS)[number];

/** The developer's practice pages and the mentor conversation; the reviewed work takes a review instead. */
export const PRIVATE_CHANNELS = ["IN_APP", "IN_CHAT"] as const;
export type PrivateChannel = (typeof PRIVATE_CHANNELS)[number];

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
export const REVIEW_CONTRACT_VERSION = 2;

/** ComposedReview's bounds; a review over them is refused so the composer can correct it, never cut. */
export const REVIEW_LIMITS = { summaryChars: 8000, inlineChars: 2000, inlineNotes: 30 } as const;

/** Partial view for coherence checks; pi-runner.ts owns the complete tool schema. */
export interface ComposedFeedbackUnit {
	action?: FeedbackAction;
	channel?: Channel;
	practiceSlug?: string;
	supersedesThreadKey?: string;
	[key: string]: unknown;
}

export interface PreparedFeedbackTarget {
	threadKey: string;
	channel: Channel;
	practiceSlug: string;
}

/**
 * What a text or a unit rests on, as the server records it: one admitted result, or one observation of it. The
 * composer names either id; the reference always carries the result the id belongs to.
 */
export interface EvidenceRef {
	practiceResultId: string;
	observationId?: string;
}

/** A line note's place: one citation of one observation. A result's own citations never anchor a note. */
export interface ReviewAnchor {
	observationId: string;
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
	units?: ComposedFeedbackUnit[];
	review?: ComposedReview | null;
}

/** The fields of an admitted observation a line note is checked against: the citations it may sit on. */
export interface ReviewedObservation {
	observationId: string;
	citations: readonly Record<string, unknown>[];
}

/** The fields of an admitted result a review is checked against, with its observations when it is NOT_MET. */
export interface ReviewedResult {
	summaryOnly?: boolean;
	practiceSlug: string;
	outcome: unknown;
	observations: readonly ReviewedObservation[];
}

/** The admitted result an id names, either as its own practiceResultId or as one of its observations' observationId. */
export interface NamedEvidence<R> {
	result: R;
	ref: EvidenceRef;
}

/**
 * Every id composition may name, keyed by itself: each result's practiceResultId and each of its observations'
 * observationId, with the result it belongs to.
 */
export function namedEvidence<R extends { observations: readonly { observationId: string }[] }>(
	results: ReadonlyMap<string, R>,
): Map<string, NamedEvidence<R>> {
	const named = new Map<string, NamedEvidence<R>>();
	for (const [practiceResultId, result] of results) {
		named.set(practiceResultId, { result, ref: { practiceResultId } });
		for (const { observationId } of result.observations) {
			named.set(observationId, { result, ref: { practiceResultId, observationId } });
		}
	}
	return named;
}

/**
 * The references the ids name, each once, in the order first named. Only checked ids reach this point, so an id that
 * names nothing admitted is a defect here, never something to drop.
 */
export function evidenceRefs(
	ids: readonly string[],
	named: ReadonlyMap<string, { ref: EvidenceRef }>,
): EvidenceRef[] {
	const refs = new Map<string, EvidenceRef>();
	for (const id of ids) {
		const ref = named.get(id)?.ref;
		if (ref === undefined) {
			throw new Error(`${id} names no admitted result or observation`);
		}
		refs.set(`${ref.practiceResultId}\u0000${ref.observationId ?? ""}`, ref);
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

/**
 * Private feedback rests on a problem of its own practice: its NOT_MET result, named directly or through one of its
 * observations. History alone authorizes nothing.
 */
export function validateFeedbackEvidence(
	primaryPractice: string,
	basedOn: readonly string[],
	results: ReadonlyMap<
		string,
		{ practiceSlug: string; outcome: unknown; observations: readonly { observationId: string }[] }
	>,
): string | null {
	const named = namedEvidence(results);
	const unknown = basedOn.find((id) => !named.has(id));
	if (unknown !== undefined) {
		// The ids of this practice's own result and observations are named, so the correction is one edit away:
		// a session that wrote a digest or a citation here is looking at the wrong field.
		const own = [...results]
			.filter(([, result]) => result.practiceSlug === primaryPractice)
			.map(
				([id, result]) =>
					`${id}${result.observations.length > 0 ? ` (its observations: ${result.observations.map((observation) => observation.observationId).join(", ")})` : ""}`,
			);
		const hint =
			own.length > 0
				? `the admitted result of ${primaryPractice} is ${own.join(", ")}`
				: `no admitted result belongs to ${primaryPractice}`;
		return `Evidence '${unknown}' does not name an admitted result or observation from this run (basedOn takes a result's \`practiceResultId\` or an observation's \`observationId\`; ${hint}); skipped.`;
	}
	if (
		!basedOn.some((id) => {
			const result = named.get(id)?.result;
			return result?.practiceSlug === primaryPractice && result.outcome === "NOT_MET";
		})
	) {
		return `At least one basedOn id must name the NOT_MET result of the primary practice '${primaryPractice}' or one of its observations; skipped.`;
	}
	return null;
}

export function undeliverableUnits(
	envelope?: ComposedFeedbackEnvelope | null,
): ComposedFeedbackUnit[] {
	const prepared = new Set(
		envelope?.preparedTargets?.map(
			(target) => `${target.threadKey}\u0000${target.channel}\u0000${target.practiceSlug}`,
		),
	);
	return (envelope?.units ?? []).filter((unit) => {
		if (unit.action !== "SUPERSEDE") {
			return false;
		}
		const target = unit.supersedesThreadKey;
		return (
			target === undefined ||
			unit.channel === undefined ||
			unit.practiceSlug === undefined ||
			!prepared.has(`${target}\u0000${unit.channel}\u0000${unit.practiceSlug}`)
		);
	});
}

/** One occurrence an IN_APP card may cite, as the server selected it after admission: one NOT_MET result. */
export interface InAppSupportOccurrence {
	practiceResultId: string;
	artifactKind: string;
	artifactId: number;
	outcome: "NOT_MET";
	origin: "LIVE" | "MANUAL" | "BACKFILL";
	summary: string;
	evidenceRationale: string | null;
	evidence: unknown;
	practiceRevisionId: number | null;
	observedAt: string;
}

/** InAppRoutingDecision as the server decides it from the evidence, before any card is written. */
export const IN_APP_DECISIONS = [
	"ADMIT",
	"NO_EVIDENCE",
	"UNCORROBORATED",
	"PRACTICE_REQUIRES_APPROVAL",
	"BACKFILL_HELD",
	"RECENTLY_SURFACED",
	"UNATTRIBUTABLE_SUBJECT",
] as const;

/** ConversationRoutingDecision, per admitted NOT_MET result. */
export const IN_CHAT_DECISIONS = [
	"ADMIT",
	"MENTOR_UNAVAILABLE",
	"NOT_DELIVERABLE",
	"HAS_INLINE_ANCHOR",
	"ALREADY_DELIVERED_IN_CONTEXT",
	"REVIEWER_DEFERRED",
	"PRACTICE_REQUIRES_APPROVAL",
	"BACKFILL_QUIET",
] as const;

export interface InAppPracticeSupport {
	practiceSlug: string;
	decision: (typeof IN_APP_DECISIONS)[number];
	occurrences: InAppSupportOccurrence[];
}

/** The server's dated IN_APP support read; null until admission, and null when it arrived malformed. */
export type InAppSupport =
	| { state: "COMPLETE"; readAt: string; practices: InAppPracticeSupport[] }
	| { state: "UNAVAILABLE" | "REFUSED"; readAt: string; refusal: string | null };

/** The server's dated IN_CHAT routing read; null until admission, and null when it arrived malformed. */
export type InChatEligibility =
	| {
			state: "COMPLETE";
			readAt: string;
			results: { practiceResultId: string; decision: (typeof IN_CHAT_DECISIONS)[number] }[];
	  }
	| { state: "UNAVAILABLE" | "REFUSED"; readAt: string };

/** The admitted fields the routing reads are checked against. */
interface RoutedResult {
	practiceResultId: string;
	practiceSlug: string;
	outcome: unknown;
}

function readSupportOccurrence(value: unknown): InAppSupportOccurrence | null {
	if (
		!isObject(value) ||
		typeof value.practiceResultId !== "string" ||
		value.practiceResultId === "" ||
		typeof value.artifactKind !== "string" ||
		value.artifactKind === "" ||
		typeof value.artifactId !== "number" ||
		!Number.isSafeInteger(value.artifactId) ||
		value.artifactId <= 0 ||
		value.outcome !== "NOT_MET" ||
		(value.origin !== "LIVE" && value.origin !== "MANUAL" && value.origin !== "BACKFILL") ||
		typeof value.summary !== "string" ||
		(value.evidenceRationale !== undefined &&
			value.evidenceRationale !== null &&
			typeof value.evidenceRationale !== "string") ||
		(value.practiceRevisionId !== undefined &&
			value.practiceRevisionId !== null &&
			(typeof value.practiceRevisionId !== "number" ||
				!Number.isSafeInteger(value.practiceRevisionId) ||
				value.practiceRevisionId <= 0)) ||
		typeof value.observedAt !== "string" ||
		!Number.isFinite(Date.parse(value.observedAt))
	) {
		return null;
	}
	return {
		practiceResultId: value.practiceResultId,
		artifactKind: value.artifactKind,
		artifactId: value.artifactId,
		outcome: value.outcome,
		origin: value.origin,
		summary: value.summary,
		evidenceRationale: value.evidenceRationale ?? null,
		evidence: value.evidence ?? null,
		practiceRevisionId: value.practiceRevisionId ?? null,
		observedAt: value.observedAt,
	};
}

/** Validates the support read beside the admission; anything else is no support context at all. */
export function readInAppSupport(
	value: unknown,
	current: readonly RoutedResult[],
): InAppSupport | null {
	if (
		!isObject(value) ||
		typeof value.readAt !== "string" ||
		!Number.isFinite(Date.parse(value.readAt))
	) {
		return null;
	}
	if (value.state === "UNAVAILABLE" || value.state === "REFUSED") {
		if (
			value.refusal !== undefined &&
			value.refusal !== null &&
			typeof value.refusal !== "string"
		) {
			return null;
		}
		return { state: value.state, readAt: value.readAt, refusal: value.refusal ?? null };
	}
	if (value.state !== "COMPLETE" || !Array.isArray(value.practices)) {
		return null;
	}
	const sent: unknown[] = value.practices;
	const practices: InAppPracticeSupport[] = [];
	const expected = new Set(
		current.filter((result) => result.outcome === "NOT_MET").map((result) => result.practiceSlug),
	);
	for (const practice of sent) {
		const decision = isObject(practice)
			? IN_APP_DECISIONS.find((known) => known === practice.decision)
			: undefined;
		if (
			!isObject(practice) ||
			typeof practice.practiceSlug !== "string" ||
			!expected.delete(practice.practiceSlug) ||
			decision === undefined ||
			!Array.isArray(practice.occurrences) ||
			// A practice the page refuses carries no support.
			(decision !== "ADMIT" && practice.occurrences.length > 0)
		) {
			return null;
		}
		const listed: unknown[] = practice.occurrences;
		const occurrences = listed.map(readSupportOccurrence);
		if (!occurrences.every((occurrence) => occurrence !== null)) {
			return null;
		}
		practices.push({ practiceSlug: practice.practiceSlug, decision, occurrences });
	}
	if (expected.size > 0) {
		return null;
	}
	return { state: "COMPLETE", readAt: value.readAt, practices };
}

/** Validates the IN_CHAT routing beside the admission: one decision for each admitted NOT_MET result, or none. */
export function readInChatEligibility(
	value: unknown,
	current: readonly RoutedResult[],
): InChatEligibility | null {
	if (
		!isObject(value) ||
		typeof value.readAt !== "string" ||
		!Number.isFinite(Date.parse(value.readAt)) ||
		!Array.isArray(value.results)
	) {
		return null;
	}
	const sent: unknown[] = value.results;
	if (value.state === "UNAVAILABLE" || value.state === "REFUSED") {
		return sent.length === 0 ? { state: value.state, readAt: value.readAt } : null;
	}
	if (value.state !== "COMPLETE") {
		return null;
	}
	const expected = new Set(
		current
			.filter((result) => result.outcome === "NOT_MET")
			.map((result) => result.practiceResultId),
	);
	const results: { practiceResultId: string; decision: (typeof IN_CHAT_DECISIONS)[number] }[] = [];
	for (const route of sent) {
		const decision = isObject(route)
			? IN_CHAT_DECISIONS.find((known) => known === route.decision)
			: undefined;
		if (
			!isObject(route) ||
			typeof route.practiceResultId !== "string" ||
			!expected.delete(route.practiceResultId) ||
			decision === undefined
		) {
			return null;
		}
		results.push({ practiceResultId: route.practiceResultId, decision });
	}
	return expected.size === 0 ? { state: "COMPLETE", readAt: value.readAt, results } : null;
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

const isObject = (value: unknown): value is Record<string, unknown> =>
	typeof value === "object" && value !== null && !Array.isArray(value);

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

/**
 * The ids as sent, or null when basedOn is not an array of non-empty strings. Nothing is dropped from it: a text
 * whose support names something that is not an id says something no result backs, so it is refused whole.
 */
function idsOf(value: unknown): string[] | null {
	if (!Array.isArray(value)) {
		return null;
	}
	const ids: string[] = [];
	for (const id of value) {
		if (typeof id !== "string" || id.trim() === "") {
			return null;
		}
		ids.push(id.trim());
	}
	return ids;
}

const NOT_AN_ID_LIST =
	"basedOn must be an array of result and observation id strings; a text resting on anything else is refused whole";

type Named = ReadonlyMap<string, NamedEvidence<ReviewedResult>>;

function integerOf(value: unknown): number | undefined {
	if (typeof value === "number" && Number.isInteger(value)) {
		return value;
	}
	return typeof value === "string" && /^\d+$/u.test(value.trim()) ? Number(value) : undefined;
}

/** The problems with a text that is published as written: missing, too long, or carrying a reserved artifact. */
function textProblem(value: unknown, limit: number): string | null {
	if (typeof value !== "string" || value.trim() === "") {
		return "body is required: the complete text, as the developer will read it";
	}
	if (value.length > limit) {
		return `body must be at most ${limit} characters; this one is ${value.length}. Shorten it`;
	}
	if (value.includes(RESERVED_MARKER)) {
		return "body may not contain an HTML comment (`<!--`); the server writes its own markers there";
	}
	if (ENVELOPE_TAIL.test(value)) {
		return "body ends in quote and bracket characters left over from a JSON envelope; end it on its last sentence";
	}
	return null;
}

/**
 * The problems with what a part rests on: every id an admitted NOT_MET result, or an observation of one. The review on
 * the work never acknowledges a MET result, so a text that rests on one is refused.
 */
function supportProblem(basedOn: readonly string[] | null, named: Named): string | null {
	if (basedOn === null) {
		return NOT_AN_ID_LIST;
	}
	if (basedOn.length === 0) {
		return "basedOn is required: the id of every admitted result or observation this text speaks about";
	}
	const unknown = basedOn.filter((id) => !named.has(id));
	if (unknown.length > 0) {
		return `basedOn names ${unknown.join(", ")}, which is not one of the NOT_MET results or observations this review may rest on (basedOn takes their \`practiceResultId\` or \`observationId\`)`;
	}
	const notConcerns = basedOn.filter((id) => named.get(id)?.result.outcome !== "NOT_MET");
	return notConcerns.length > 0
		? `basedOn names ${notConcerns.join(", ")}, which is not a NOT_MET result or one of its observations. The review on the work speaks only about NOT_MET results and their observations; it never acknowledges a MET result`
		: null;
}

/**
 * The problem with a note's anchor: it names an observation that the note rests on, itself or through its result, and
 * one of that observation's citations that is a line of this change and carries no other note.
 */
function anchorProblem(
	anchor: ReviewAnchor,
	basedOn: readonly string[],
	named: Named,
	anchors: ReadonlySet<string>,
): string | null {
	const { observationId, citationIndex } = anchor;
	const entry = named.get(observationId);
	if (entry === undefined) {
		return `anchor names ${observationId}, which is not an observation this review may rest on`;
	}
	const observation = entry.result.observations.find(
		(candidate) => candidate.observationId === observationId,
	);
	if (observation === undefined) {
		return `anchor names ${observationId}, which is a result: a note sits on a citation of one of its observations, named by its observationId`;
	}
	if (!basedOn.includes(observationId) && !basedOn.includes(entry.ref.practiceResultId)) {
		return `anchor names ${observationId}, which this note's basedOn does not, itself or through its result`;
	}
	const citation = observation.citations[citationIndex];
	if (citation === undefined) {
		return `${observationId} has no citation ${citationIndex}`;
	}
	if (citation.anchorable !== true) {
		return `citation ${citationIndex} of ${observationId} is not a line of this change, so no note can sit on it`;
	}
	return anchors.has(`${observationId}#${citationIndex}`)
		? `another note already sits on citation ${citationIndex} of ${observationId}`
		: null;
}

/** What the review decides for one NOT_MET result: raise it, or withhold it for one of the reasons. */
export const DISPOSITIONS = ["RAISE", ...WITHHOLD_REASONS] as const;
export type Disposition = (typeof DISPOSITIONS)[number];

function dispositionOf(value: unknown): Disposition | undefined {
	const word = typeof value === "string" ? value.trim().toUpperCase().replaceAll("-", "_") : "";
	return DISPOSITIONS.find((candidate) => candidate === word);
}

/**
 * The results a sent text speaks about, itself or through an observation it names in basedOn, read from what was sent
 * so a refused part still counts as spoken.
 */
function spokenOf(value: Record<string, unknown>, named: Named): Set<string> {
	const summary = isObject(value.summary) ? (idsOf(value.summary.basedOn) ?? []) : [];
	const notes = (listOf(value.inline) ?? []).flatMap((note) =>
		isObject(note) ? (idsOf(note.basedOn) ?? []) : [],
	);
	return new Set([...summary, ...notes].flatMap((id) => named.get(id)?.ref.practiceResultId ?? []));
}

/**
 * Every NOT_MET result takes one decision, and the text keeps to it: a raised one is spoken about, itself or through
 * its observations, and a withheld one is not. A result whose decision was sent but refused is answered by that
 * refusal, not as undecided.
 */
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
				`${id} is decided RAISE, and no text speaks about it or its observations; speak about it, or decide again`,
			);
		} else if (disposition !== "RAISE" && spoken.has(id)) {
			errors.push(
				`${id} is withheld as ${disposition}, and a text speaks about it or its observations; a result is raised or withheld, never both`,
			);
		}
	}
	return errors;
}

function inlineSupportProblem(basedOn: string[] | null, named: Named): string | null {
	return basedOn !== null && basedOn.some((id) => named.get(id)?.result.summaryOnly === true)
		? "this note rests on a practice that requires summary placement"
		: null;
}

/** What a review is read against besides the results it may rest on. */
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
function readSummary(
	value: unknown,
	named: Named,
): { summary: ReviewSummary | null; errors: string[] } {
	if (value === undefined || value === null) {
		return { summary: null, errors: [] };
	}
	if (!isObject(value)) {
		return { summary: null, errors: ["summary: is one object with body and basedOn"] };
	}
	const basedOn = idsOf(value.basedOn);
	const problems = [
		textProblem(value.body, REVIEW_LIMITS.summaryChars),
		supportProblem(basedOn, named),
	].filter((problem): problem is string => problem !== null);
	if (problems.length > 0 || typeof value.body !== "string" || basedOn === null) {
		return { summary: null, errors: problems.map((problem) => `summary: ${problem}`) };
	}
	return { summary: { body: value.body, basedOn: evidenceRefs(basedOn, named) }, errors: [] };
}

/**
 * The decisions as sent, one per NOT_MET result: each valid one by result, every result a decision names, and every
 * reason one is refused. A prior-advice reason names the statement that gave the advice.
 */
function readDecisions(
	value: unknown,
	results: ReadonlyMap<string, ReviewedResult>,
	evidence: Named,
	witnesses: ReadonlyMap<string, PriorAdviceWitness>,
): { decided: Map<string, Disposition>; named: Set<string>; errors: string[] } {
	const decided = new Map<string, Disposition>();
	const named = new Set<string>();
	if (!Array.isArray(value)) {
		return {
			decided,
			named,
			errors: [
				"decisions is required: an array with one decision for each NOT_MET result, empty when there is none",
			],
		};
	}
	const sent: unknown[] = value;
	const errors: string[] = [];
	for (const [index, raw] of sent.entries()) {
		const label = `decisions #${index + 1}`;
		if (!isObject(raw)) {
			errors.push(
				`${label}: is one object with practiceResultId, disposition and, for a prior advice, witnessIds`,
			);
			continue;
		}
		const id = typeof raw.practiceResultId === "string" ? raw.practiceResultId.trim() : "";
		const disposition = dispositionOf(raw.disposition);
		const witnessIds =
			raw.witnessIds === undefined || raw.witnessIds === null ? [] : idsOf(raw.witnessIds);
		const problems: string[] = [];
		const unknown = Object.keys(raw).filter(
			(key) => key !== "practiceResultId" && key !== "disposition" && key !== "witnessIds",
		);
		if (unknown.length > 0) {
			problems.push(`unknown decision field(s): ${unknown.join(", ")}`);
		}
		const result = results.get(id);
		const parent = evidence.get(id)?.ref.practiceResultId;
		if (id === "") {
			problems.push("practiceResultId is required: the `practiceResultId` of one NOT_MET result");
		} else if (result === undefined) {
			problems.push(
				parent === undefined
					? `${id} is not one of the results this review may rest on`
					: `${id} is an observation of ${parent}; a decision names its result's practiceResultId`,
			);
		} else if (result.outcome !== "NOT_MET") {
			problems.push(`${id} is not a NOT_MET result; only a result with problems takes a decision`);
		} else if (named.has(id)) {
			problems.push(`${id} already has a decision; each NOT_MET result takes one`);
		}
		if (result?.outcome === "NOT_MET") {
			named.add(id);
		}
		if (disposition === undefined) {
			problems.push(`disposition must be one of ${DISPOSITIONS.join(", ")}`);
		} else if (disposition === "RAISE") {
			if (witnessIds === null || witnessIds.length > 0) {
				problems.push("a raised result names no witness");
			}
		} else {
			problems.push(...witnessProblems(disposition, witnessIds, witnesses));
		}
		errors.push(...problems.map((problem) => `${label}: ${problem}`));
		if (problems.length === 0 && disposition !== undefined) {
			decided.set(id, disposition);
		}
	}
	return { decided, named, errors };
}

/** Keep an explicit no-summary distinct from a primitive the SDK could coerce to null. */
export function prepareReviewArguments(value: unknown) {
	if (!isObject(value)) {
		throw new Error("the review is one object with decisions, summary and inline");
	}
	if (value.summary !== undefined && value.summary !== null && !isObject(value.summary)) {
		throw new Error(
			"summary is one object with body and basedOn, or null when there is no comment",
		);
	}
	return value;
}

/**
 * Reads one review as the composer sent it, or every reason it cannot be stored. A review is stored whole or not
 * at all: its decisions and texts were written together, so one wrong part sends the whole review back to be
 * corrected. The decisions are checked here and not stored as sent: each withheld one becomes the stored withholding
 * of its result and reason, and a decision's witnessIds are not kept. Every id a stored text names is stored as the
 * reference it resolves to.
 *
 * @param results the results this review may rest on, by practiceResultId
 * @param lineNotes whether this work has lines a note can be placed on
 */
export function readReview(
	value: unknown,
	results: ReadonlyMap<string, ReviewedResult>,
	lineNotes: boolean,
	context: ReviewContext,
): { review: ComposedReview } | { errors: string[] } {
	if (!isObject(value)) {
		return { errors: ["the review is one object with decisions, summary and inline"] };
	}
	const errors: string[] = [];
	const unknownFields = Object.keys(value).filter(
		(key) => key !== "decisions" && key !== "summary" && key !== "inline",
	);
	if (unknownFields.length > 0) {
		errors.push(
			`unknown review field(s): ${unknownFields.join(", ")} — a review takes decisions, summary and inline`,
		);
	}
	const named = namedEvidence(results);
	const decisions = readDecisions(value.decisions, results, named, context.witnesses);
	errors.push(...decisions.errors);

	const read = readSummary(value.summary, named);
	errors.push(...read.errors);
	const { summary } = read;

	const sentNotes = listOf(value.inline);
	if (sentNotes === null) {
		errors.push("inline must be an array of line notes");
	}
	const notes = sentNotes ?? [];
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
		if (!isObject(raw)) {
			errors.push(`${label}: is one object with body, basedOn and anchor`);
			continue;
		}
		const basedOn = idsOf(raw.basedOn);
		const problems = [
			textProblem(raw.body, REVIEW_LIMITS.inlineChars),
			supportProblem(basedOn, named),
		].filter((problem): problem is string => problem !== null);
		const placementProblem = inlineSupportProblem(basedOn, named);
		if (placementProblem !== null) {
			problems.push(placementProblem);
		}
		const anchor = isObject(raw.anchor) ? raw.anchor : {};
		const observationId =
			typeof anchor.observationId === "string" ? anchor.observationId.trim() : "";
		const citationIndex = integerOf(anchor.citationIndex);
		if (observationId === "" || citationIndex === undefined) {
			problems.push(
				"anchor needs observationId and citationIndex, the line comes from that citation of that observation",
			);
		} else if (basedOn !== null) {
			// A refused support is answered above; the anchor cannot be checked against it.
			const problem = anchorProblem({ observationId, citationIndex }, basedOn, named, anchors);
			if (problem !== null) {
				problems.push(problem);
			}
		}
		errors.push(...problems.map((problem) => `${label}: ${problem}`));
		if (
			problems.length === 0 &&
			typeof raw.body === "string" &&
			citationIndex !== undefined &&
			basedOn !== null
		) {
			anchors.add(`${observationId}#${citationIndex}`);
			inline.push({
				body: raw.body,
				// A note that rests on the anchor's result also rests on the observation it sits on.
				basedOn: evidenceRefs([...basedOn, observationId], named),
				anchor: { observationId, citationIndex },
			});
		}
	}

	errors.push(
		...coverageProblems(decisions.decided, decisions.named, spokenOf(value, named), results),
	);
	const withheld: ReviewWithheld[] = [...decisions.decided].flatMap(([id, disposition]) =>
		disposition === "RAISE" ? [] : [{ basedOn: [{ practiceResultId: id }], reason: disposition }],
	);
	return errors.length > 0 ? { errors } : { review: { summary, inline, withheld } };
}

/** Every result the review decided about, itself or through its observations: what it says and what it withholds. */
export function decidedByReview(review: ComposedReview | null | undefined): Set<string> {
	if (!review) {
		return new Set();
	}
	return new Set(
		[
			...(review.summary?.basedOn ?? []),
			...review.inline.flatMap((note) => note.basedOn),
			...review.withheld.flatMap((decision) => decision.basedOn),
		].map((ref) => ref.practiceResultId),
	);
}

/**
 * The admitted results the review on the work may rest on, whole, with their observations: the NOT_MET results that
 * admission marked publicEligible. A review on the work is owed only when there is at least one. A MET result is never
 * acknowledged on the work, so it is left out here, and a run with only MET results composes no review. The server
 * decides eligibility (PublicReviewEligibility) and admits a composed review by the same rule, so a result it did not
 * mark is left out entirely rather than shown with parts removed — its words may carry what it drew on. Nothing of a
 * shown result is shortened; only the verification digests of its citations, its example's and its observations' are
 * dropped, which say nothing a composer can use.
 */
export function publicResults(
	results: readonly Record<string, unknown>[],
): Record<string, unknown>[] {
	return results
		.filter((result) => result.publicEligible === true && result.outcome === "NOT_MET")
		.map(({ citations, example, observations, ...rest }) => ({
			...rest,
			...(example === undefined
				? {}
				: {
						example: isObject(example)
							? { ...example, citation: withoutDigest(example.citation) }
							: example,
					}),
			citations: withoutDigests(citations),
			observations: (Array.isArray(observations) ? observations : []).map((observation: unknown) =>
				isObject(observation)
					? { ...observation, citations: withoutDigests(observation.citations) }
					: observation,
			),
		}));
}

/** One citation as a public reference shows it: its quoted lines and coordinates, without its verification digests. */
function withoutDigest(citation: unknown): unknown {
	if (!isObject(citation)) {
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
	const branch = result.outcome === "UNDETERMINED" ? "undecidability" : "inapplicability";
	const evidence = isObject(result.evidence) ? result.evidence : {};
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
				(result.outcome === "NOT_APPLICABLE" || result.outcome === "UNDETERMINED"),
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
	reviewedRevision?: unknown;
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
	if (thisWork === undefined || !isObject(history) || !Array.isArray(history.feedback)) {
		return { feedback: [], omissions };
	}
	const projected = history.feedback.flatMap((entry: unknown): OwnPriorFeedback[] => {
		if (!isObject(entry) || entry.channel !== "IN_CONTEXT" || entry.publicEligible !== true) {
			return [];
		}
		const artifact = isObject(entry.artifact) ? entry.artifact : {};
		if (workIdentity(artifact.kind, artifact.url) !== thisWork) {
			return [];
		}
		const {
			id,
			reviewedRevision,
			basedOn,
			deliveredAt,
			body,
			recordedClaimCurrentness,
			withdrawn,
		} = entry;
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
				...(reviewedRevision === undefined ? {} : { reviewedRevision }),
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
	"its observations, and no withheld one. Each body is published whole as written, with provider safety formatting and a fixed disclosure; nothing is " +
	"assembled from fragments. An accepted call is final and ends the composition. A missing or contradicted decision, " +
	"invalid support, eligibility or placement, or a witness that cannot stand as the advice refuses the whole " +
	"review, with every reason, so it can be corrected and sent again.";

/**
 * How the review is written. It ends the opening turn, after the criteria, so reference material is followed by the
 * writing task; the composer prompt points here.
 */
export const WRITE_CONTRACT =
	"## Writing the review\n" +
	"Write from the results and observations each text rests on, within their qualifications. A declared affordance supports its bounded " +
	"benefit, not an unobserved runtime or test outcome. Keep each remedy on its recorded gap and leave unrelated " +
	"behavior as it is; make another change a prerequisite only when the evidence establishes that dependency. " +
	"You are not told whether the work is ready, so do not approve it, call it ready or blocked, or set conditions " +
	"for merging it. Timing the evidence itself warrants is fine: a committed secret is removed and rotated before " +
	"anyone relies on the history.\n\n" +
	"Make each point fully once in this review:\n" +
	"- A line note is one self-contained point about the code at its anchor: that local concern with its action and " +
	"evidence. It is read alone, so it says its point completely. Several practices " +
	"may support it when they describe that one event; it does not collect the review's other points.\n" +
	"- The summary orients the reader: the guidance its raised NOT_MET results warrant, most important first. It " +
	"carries every ask that is not " +
	"about one place in the code, such as the description, a reply to a reviewer or a work-wide change, unless it " +
	"forms one point with the code at a line. When a point has no useful line note, the summary carries that point " +
	"completely. It may name or locate a line note's topic, and then names that note's " +
	"results or observations in basedOn, but it does not restate the note's diagnosis or action.\n" +
	"- The review speaks only about NOT_MET results and their observations. It never acknowledges or praises a MET " +
	"result, and it does not use praise to cushion a concern. When no NOT_MET result is raised, the review says " +
	"nothing: the summary is null and there are no line notes.\n\n" +
	"One result or observation may support notes at several places, and also the summary's overview of them.";

/** The practices of the public NOT_MET results, each once: every one is decided against its whole standard. */
function notMetPractices(reviewable: readonly Record<string, unknown>[]): string[] {
	return [
		...new Set(
			reviewable
				.filter((result) => result.publicEligible === true && result.outcome === "NOT_MET")
				.map((result) => String(result.practiceSlug)),
		),
	];
}

/**
 * What a staged standard is for in the review, wherever one is shown: the opening or a restored reference. The
 * criteria were written to assess the work; here they only qualify how its recorded results are communicated.
 */
export const STANDARD_REFERENCE =
	"Reference, whole as staged: each standard explains what its practice's recorded results were assessed " +
	"against and the responses it accepts, and qualifies whether and how those results and their observations may " +
	"be communicated. It is not a request to assess the work again and raises no concern of its own.";

/** Each concern's complete grounds, limits, standard and candidate communication references, grouped by practice. */
export function notMetReference(
	reviewable: readonly Record<string, unknown>[],
	stagedCriteria: (practiceSlug: string) => string | null,
	practices: readonly ReviewPractice[] = [],
	history: readonly OwnPriorFeedback[] = [],
	reviewedRevision: string | null = null,
): string {
	const concerns = notMetPractices(reviewable).map((slug) => {
		const results = reviewable.filter(
			(entry) =>
				entry.publicEligible === true && entry.outcome === "NOT_MET" && entry.practiceSlug === slug,
		);
		return `${practiceStandard(slug, stagedCriteria).text}${practiceReference(slug, results, practices, history, reviewedRevision)}`;
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
	reviewedRevision: string | null,
): string {
	const practice = practices.find((entry) => entry.slug === slug);
	const candidatePriorWitnesses = history.flatMap((entry) => {
		if (
			entry.witnessId === null ||
			(!entry.eligibleForAlreadySaid && !entry.eligibleForPriorAdvice) ||
			!Array.isArray(entry.basedOn) ||
			!entry.basedOn.some((support: unknown) => isObject(support) && support.practiceSlug === slug)
		) {
			return [];
		}
		return [
			{
				witnessId: entry.witnessId,
				reviewedRevision:
					typeof entry.reviewedRevision === "string" ? entry.reviewedRevision : null,
				basedOn: entry.basedOn.filter(
					(support: unknown) => isObject(support) && support.practiceSlug === slug,
				),
				eligibleForAlreadySaid: entry.eligibleForAlreadySaid,
				eligibleForPriorAdvice: entry.eligibleForPriorAdvice,
			},
		];
	});
	return `\`\`\`json\n${JSON.stringify(
		{
			reviewedRevision,
			...(practice === undefined
				? {}
				: {
						practice: {
							slug: practice.slug,
							name: practice.name,
							...(practice.revisionId === undefined ? {} : { revisionId: practice.revisionId }),
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
	"Choose these before writing the body: the id of every NOT_MET result or observation behind a concern this body " +
	"states, and no other: a result's practiceResultId, or an observation's observationId for one located problem. " +
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

/**
 * The parameters of report_review for one run, built from the same results readReview checks against: one decision
 * for each NOT_MET result comes first, each text names only NOT_MET results of this run or their observations, and a
 * note sits only on an observation with a line of this change. A list with nothing it could name takes no items. The
 * required fields of each part are required here, so a missing one is answered by the provider's own validation; the
 * texts use null for no summary and an empty array for no notes. readReview stays the final check and answers each
 * part by name.
 *
 * @param results the results this review may rest on, by practiceResultId
 * @param lineNotes whether this work has lines a note can be placed on
 * @param eligibleWitnesses every witnessId a decision may name; readReview checks which reason each one supports
 */
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
		...result.observations.map((observation) => observation.observationId),
	]);
	const anchorable = lineNotes
		? concerns.flatMap(([, result]) =>
				result.observations
					.filter((observation) =>
						observation.citations.some((citation) => citation.anchorable === true),
					)
					.map((observation) => observation.observationId),
			)
		: [];
	return {
		type: "object",
		required: ["decisions", "summary"],
		properties: {
			decisions: {
				type: "array",
				minItems: notMet.length,
				maxItems: notMet.length,
				description:
					"Decided first: one decision for each NOT_MET result. RAISE means the " +
					"texts below speak about it or its observations; any other disposition withholds it, and no text " +
					"speaks about it or its observations.",
				items: {
					type: "object",
					required: ["practiceResultId", "disposition"],
					properties: {
						practiceResultId: { type: "string", enum: notMet },
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
							"The whole comment in Markdown, written from the results and observations in basedOn.",
					},
				},
			},
			inline: {
				type: "array",
				maxItems: anchorable.length > 0 ? REVIEW_LIMITS.inlineNotes : 0,
				description:
					"Notes intended for cited lines of the change, each one self-contained point about the code at " +
					"its line. Each placement can fail independently.",
				items: {
					type: "object",
					required: ["basedOn", "body", "anchor"],
					properties: {
						basedOn: idList(
							restable,
							`${SUPPORT_FIRST} It includes the anchor's observation or that observation's result.`,
						),
						body: {
							type: "string",
							minLength: 1,
							maxLength: REVIEW_LIMITS.inlineChars,
							description:
								"The whole note in Markdown, written from the results and observations in basedOn.",
						},
						anchor: {
							type: "object",
							required: ["observationId", "citationIndex"],
							description:
								"The line, as one citation marked anchorable of an observation that basedOn names, itself or " +
								"through its result. A result's own citations take no note.",
							properties: {
								observationId:
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

/** Explanatory context from the staged practice revision, not a new assessment. */
export interface ReviewPractice {
	slug: string;
	name: string;
	whyItMatters?: string;
	knownLimitations: readonly string[];
	/** Which revision of the practice was staged: it identifies the standard, not that the standard or the work changed. */
	revisionId?: number;
}

/** Everything the review on the work is composed from; nothing else reaches its session. */
export interface ReviewTurnInput {
	/** The captured record of this same work, from buildSameWorkContext: it orients; results own assessments. */
	sameWork: string;
	/** The NOT_MET results of this work the review may rest on, whole with their observations, from publicResults. */
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
	/** Context on the practices of the NOT_MET results. */
	practices: readonly ReviewPractice[];
	/** Practices this review did not reach at all. */
	notReached: readonly string[];
	/** Whether this work has lines a note can be placed on. */
	lineNotes: boolean;
	/** One practice's staged criteria, whole; null when none were staged. */
	stagedCriteria: (practiceSlug: string) => string | null;
}

/**
 * The opening reference: whole work and communication, full concerns, and the limits the abstentions recorded.
 */
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
	const placement = input.lineNotes
		? "- Line notes: a note's `anchor` names one citation marked `anchorable` of an observation, by the " +
			"observation's `observationId` and the citation's `citationIndex`; a result's own citations take no note. " +
			"On GitHub it is a review comment on that line; on GitLab it is its own comment " +
			"headed by a link to the line. Each body stands on its own, and each placement can fail independently.\n"
		: "- This work has no lines a note can sit on; everything goes in the summary.\n";
	const concerns = notMetReference(
		input.results,
		input.stagedCriteria,
		input.practices,
		input.alreadySaid,
		input.captured.reviewedRevision ?? null,
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
${placement}
${notReachedNote(input.notReached)}Store the whole review with one report_review call. It decides first: each NOT_MET result is RAISE, and a text speaks about it or its observations, or withheld with its reason, and no text speaks about it or its observations. Writing nothing for the work is a decision too: it is still one final report_review call.

${WRITE_CONTRACT}`;
}
