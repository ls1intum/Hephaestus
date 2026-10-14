import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { defineTool, type AgentToolResult } from "@earendil-works/pi-coding-agent";
import { type LeanResult, type AdmittedResult, resultsById } from "./pi-admitted-results.ts";
import {
	prepareFeedback,
	type FeedbackComposition,
	type FeedbackPreparationReceipt,
} from "./pi-feedback-preparation.ts";
import {
	REVIEW_TOOL_DESCRIPTION,
	readReview,
	prepareReviewArguments,
	reviewToolParameters,
	type priorAdviceWitnesses,
	type ReviewedResult,
	ACTIONS,
	PRIVATE_DESTINATIONS,
	PRIOR_ADVICE_REASONS,
	WITHHOLD_REASONS,
	evidenceRefs,
	namedEvidence,
	undeliverableFeedback,
	validateFeedbackEvidence,
	type ComposedFeedbackEnvelope,
	type ComposedFeedbackPiece,
	type ComposedReview,
	type PreparedFeedbackTarget,
	type EvidenceRef,
	type Destination,
	type PrivateDestination,
	type FeedbackAction,
} from "./pi-runner-composition.ts";
import { hasText, isBlank } from "./pi-text.ts";
import {
	compositionRequestSchema,
	type Outcome,
} from "./work/practice-scripts/lib/core/contract.ts";
import { optionalString, isJsonObject as isRecord } from "./work/practice-scripts/lib/core/json.ts";
import { z } from "./work/practice-scripts/lib/core/practice-script.ts";

export type CompositionRequest = z.infer<typeof compositionRequestSchema>;

/** Missing requests disable composition; malformed requests never grant a destination. */
export function loadCompositionRequest(path: string): CompositionRequest | null {
	if (!existsSync(path)) {
		return null;
	}
	const request = compositionRequestSchema.parse(JSON.parse(readFileSync(path, "utf8")));
	return Object.keys(request.destinations).length === 0 ? null : request;
}

interface FeedbackToolRuntime {
	feedback: ComposedFeedback;
	admitted: () => boolean;
	routes: (slug: string) => readonly PrivateDestination[] | undefined;
	destinationRefusal: (destination: PrivateDestination, slug: string) => string;
	negativeItems: (slug: string) => number;
	documentedShape: (schema: unknown) => unknown;
	listSchema: (items: unknown, what: string) => unknown;
	submittedList: (value: unknown) => { items: unknown[] } | { error: string };
	refusal: (id: string, text: string) => Promise<AgentToolResult<ReportFeedbackDetails>>;
	persist: () => void;
	paid: () => boolean;
	onStored: (count: number) => void;
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

export type CompositionPhase = "PUBLIC_REVIEW" | "PRIVATE_FEEDBACK";

export type CompositionFailureReason =
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

export interface ComposedFeedback extends ComposedFeedbackEnvelope {
	compositionFailures: CompositionFailure[];
	contractVersion: number;
	admissionDigest: string | null;
	results: LeanResult[];
	preparedTargets: PreparedFeedbackTarget[];
	feedback: FeedbackPiece[];
	review: ComposedReview | null;
	preparation: FeedbackPreparationReceipt | null;
}

interface ReportFeedbackDetails {
	stored: number;
	destination?: Destination;
	total?: number;
}

function skipped(text: string): { stored: boolean; text: string } {
	return { stored: false, text };
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

export function notMetPractices(
	results: readonly { practiceSlug: string; outcome: Outcome }[],
): string[] {
	return [
		...new Set(
			results.filter((result) => result.outcome === "NOT_MET").map((result) => result.practiceSlug),
		),
	].toSorted();
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

export function persistComposedFeedback(composedFeedback: ComposedFeedback, output: string): void {
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
	writeFileSync(output, JSON.stringify({ ...composedFeedback, feedback }, null, 2));
}

export function buildFeedbackTool(
	practiceSlugs: string[],
	request: CompositionRequest,
	results: readonly AdmittedResult[],
	preparedTargets: PreparedFeedbackTarget[],
	deliveredPractices: ReadonlySet<string>,
	runtime: FeedbackToolRuntime,
) {
	runtime.feedback.preparedTargets = preparedTargets;
	const enabledDestinations = PRIVATE_DESTINATIONS.filter(
		(destination) => request.destinations[destination] !== undefined,
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
		if (bounds === undefined) {
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
		if (!(runtime.routes(piece.practiceSlug) ?? []).includes(piece.destination)) {
			return skipped(
				`${piece.destination} cannot carry feedback about ${piece.practiceSlug} this run ` +
					`(${runtime.destinationRefusal(piece.destination, piece.practiceSlug)}); write no piece for it there, not even a WITHHOLD. Skipped.`,
			);
		}
		if (piece.destination === "PRACTICE_PAGE" && delivers) {
			const pieces = runtime.negativeItems(piece.practiceSlug);
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
		runtime.feedback.feedback.push({
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
			"Persist feedback for the developer's practice pages and the mentor conversation, one per " +
			"destination and practice. Send every piece you have ready in one call; each is stored or skipped on " +
			"its own, with the reason, and a skipped piece can be sent again corrected without re-sending the " +
			"stored ones. This is an intervention, not a measurement: it takes no outcome, severity or " +
			"confidence, and no citation you typed yourself.",
		parameters: {
			type: "object",
			required: ["feedback"],
			properties: {
				feedback: runtime.documentedShape(
					runtime.listSchema(
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
			if (!runtime.admitted()) {
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
			const submitted = runtime.submittedList(isRecord(params) ? params.feedback : null);
			if ("error" in submitted) {
				return runtime.refusal(toolCallId, `feedback refused — ${submitted.error}`);
			}
			if (submitted.items.length === 0) {
				return runtime.refusal(
					toolCallId,
					"feedback refused — the list is empty; send every piece you have ready in it",
				);
			}
			const outcomes = submitted.items.map(store);
			const stored = outcomes.filter((outcome) => outcome.stored).length;
			runtime.onStored(stored);
			if (stored > 0) {
				runtime.persist();
			}
			const text = outcomes.map((outcome, index) => `#${index + 1}: ${outcome.text}`).join("\n");
			if (stored === 0) {
				return runtime.refusal(toolCallId, text);
			}
			return {
				content: [{ type: "text", text }],
				details: { stored, total: runtime.feedback.feedback.length },
				terminate: runtime.paid(),
			};
		},
	});
}

export async function prepareComposedFeedback(options: {
	feedback: ComposedFeedback;
	admitted: boolean;
	expired: boolean;
	url: string;
	token: string;
	task: { reviewId: string; jobId: string; attempt: number };
	timeoutMs: number;
	persist: () => void;
	recordFailure: (phase: CompositionPhase, reason: CompositionFailureReason) => void;
}): Promise<void> {
	const { feedback: composedFeedback } = options;
	if (!options.admitted || options.expired) {
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
	const publicComposition = review !== null;
	if (composition.length === 0 && !publicComposition) {
		return;
	}
	const result = await prepareFeedback({
		url: options.url,
		token: options.token,
		payload: {
			reviewId: options.task.reviewId,
			jobId: options.task.jobId,
			attempt: options.task.attempt,
			basisResultIds: [...new Set(composition.flatMap((piece) => piece.resultIds))],
			composition,
			publicComposition,
		},
		timeoutMs: options.timeoutMs,
	});
	if (result.state === "REFUSED") {
		// The server made a final decision: returning this package at terminal must not bypass it.
		composedFeedback.review = null;
		composedFeedback.feedback = [];
		if (composition.some((piece) => piece.destination === "ON_WORK")) {
			options.recordFailure("PUBLIC_REVIEW", "SERVER_REFUSED");
		}
		if (composition.some((piece) => piece.destination !== "ON_WORK")) {
			options.recordFailure("PRIVATE_FEEDBACK", "SERVER_REFUSED");
		}
		options.persist();
	}
	if (result.state === "PREPARED") {
		composedFeedback.preparation = result.receipt;
		options.persist();
	}
	// An acknowledgement can be lost after preparation commits; reconcile the unchanged immutable package.
	console.error(`[pi-runner] feedback preparation: ${result.state}`);
}

interface ReportReviewDetails {
	stored: number;
}

// A completed response can contain more calls; final composition must reject all subsequent writes.
export interface PublicReviewState {
	final: boolean;
	reviewContext: "LOST" | "RESTORING" | "HELD";
}

const REVIEW_FINAL = "The review on this work is final; this composition accepts nothing more.";

type ReviewToolRuntime = Pick<
	FeedbackToolRuntime,
	"admitted" | "feedback" | "persist" | "onStored"
> & {
	refusal: (id: string, text: string) => Promise<AgentToolResult<ReportReviewDetails>>;
};

export function buildReviewTool(
	lineNotes: boolean,
	restable: ReadonlyMap<string, ReviewedResult>,
	witnesses: ReturnType<typeof priorAdviceWitnesses>,
	state: PublicReviewState,
	restore: () => string,
	runtime: ReviewToolRuntime,
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
		execute: createReviewRecorder(lineNotes, restable, witnesses, state, restore, runtime),
	});
}

export function createReviewRecorder(
	lineNotes: boolean,
	restable: ReadonlyMap<string, ReviewedResult>,
	witnesses: ReturnType<typeof priorAdviceWitnesses>,
	state: PublicReviewState,
	restore: () => string,
	runtime: ReviewToolRuntime,
) {
	return async (
		toolCallId: string,
		params: unknown,
	): Promise<AgentToolResult<ReportReviewDetails>> => {
		if (!runtime.admitted()) {
			return runtime.refusal(
				toolCallId,
				"Feedback composition opens only after Java admits the completed results.",
			);
		}
		if (state.final) {
			return runtime.refusal(toolCallId, REVIEW_FINAL);
		}
		if (state.reviewContext !== "HELD") {
			const restored = state.reviewContext === "LOST" ? `\n${restore()}\n` : "";
			state.reviewContext = "RESTORING";
			return runtime.refusal(
				toolCallId,
				`review refused, nothing was stored: the session's context was compacted. Use the restored review reference in the next model turn before storing the review.${restored}\nSend the whole review again.`,
			);
		}
		const read = readReview(params, restable, lineNotes, { witnesses });
		if ("errors" in read) {
			return runtime.refusal(
				toolCallId,
				`review refused, nothing was stored:\n${read.errors.map((error) => `- ${error}`).join("\n")}`,
			);
		}
		// Final only once persisted: a failed write leaves the review unaccepted, so it is never delivered.
		const previous = runtime.feedback.review;
		runtime.feedback.review = read.review;
		try {
			runtime.persist();
		} catch (error) {
			runtime.feedback.review = previous;
			throw error;
		}
		state.final = true;
		runtime.onStored(1);
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
	};
}
