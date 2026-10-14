import type {
	ItemRef,
	ProblemSummary,
	ReviewBoundFeedback,
	ReviewBoundResult,
	ReviewDetail,
	ReviewLineage,
	ReviewPractice,
	ReviewRef,
} from "@/api/types.gen";

import {
	gitlabMergeRequest,
	outlineDocument,
	practiceNameOf,
	pullRequestReviewItem,
	reviewAccounts,
	reviewDetailCompleted,
	reviewDetailScanManyJobs,
	reviewDevelopers,
	reviewItem,
	scannedFiles,
	scannedRepository,
	slackConversation,
} from "./fixtures";
import {
	at,
	dayAt,
	EARLIER_REVIEW_ID,
	LINEAGE_REVIEW_ID,
	type LineageFeedbackSpec,
	type LineageResultSpec,
	storyLineage,
} from "./lineage-fixtures";

/**
 * Every state the graph can meet, one small graph per state, grouped the way the state matrix
 * stories show them. Each graph is the same pull request, reviewed from 13:35, so only the state in
 * question differs between two of them.
 */
export interface LineageStateCase {
	key: string;
	/** What the case shows, as its heading in the matrix. */
	title: string;
	lineage: ReviewLineage;
	/**
	 * The review's own detail, where the case needs what only it says: a stop reason, a withheld
	 * practice, the runtime jobs or their absence.
	 */
	review?: ReviewDetail;
}

export type LineageStateGroup =
	| "review"
	| "origin"
	| "item"
	| "results"
	| "destination"
	| "delivery"
	| "volume";

const id = (group: number, index: number) =>
	`5e5e${String(group).padStart(4, "0")}-0000-4000-8000-${String(index).padStart(12, "0")}`;

const RESULT_A = id(1, 1);
const RESULT_B = id(1, 2);
const RESULT_C = id(1, 3);
const FEEDBACK_A = id(2, 1);

/** A result of the graph's review, about Ada's pull request unless the case says otherwise. */
function stateResult(
	resultId: string,
	overrides: Partial<ReviewBoundResult> = {},
	problems: ProblemSummary[] = [],
): LineageResultSpec {
	const practiceSlug = overrides.practiceSlug ?? "errors-carry-context";
	return {
		result: {
			id: resultId,
			reviewId: LINEAGE_REVIEW_ID,
			item: reviewItem,
			developer: reviewDevelopers.ada,
			practiceSlug,
			practiceName: practiceNameOf(practiceSlug),
			outcome: "NOT_MET",
			severity: "MAJOR",
			summary: "A cache miss and a permission failure come back as the same 404",
			recordedAt: at("13:39:00"),
			currentness: "CURRENT",
			...overrides,
		},
		problems,
	};
}

const problemOf = (resultId: string, position: number, severity: ProblemSummary["severity"]) => ({
	id: `${resultId.slice(0, -2)}${String(position).padStart(2, "0")}`,
	severity,
	summary: `Problem ${position + 1}`,
});

/** Three results: one not met behind the feedback, one met, one that does not apply. */
const threeResults = (overrides: Partial<ReviewBoundResult> = {}): LineageResultSpec[] => [
	stateResult(RESULT_A, overrides, [
		problemOf(RESULT_A, 0, "MAJOR"),
		problemOf(RESULT_A, 1, "MINOR"),
	]),
	stateResult(RESULT_B, {
		practiceSlug: "thin-controllers",
		outcome: "MET",
		severity: null,
		summary: "The controller delegates before it does anything else",
		recordedAt: at("13:40:00"),
	}),
	stateResult(RESULT_C, {
		practiceSlug: "the-change-explains-itself",
		outcome: "DOES_NOT_APPLY",
		severity: null,
		summary: "The change is a dependency bump with nothing to explain",
		recordedAt: at("13:40:20"),
	}),
];

function stateFeedback(
	feedbackId: string,
	basis: string[],
	overrides: Partial<ReviewBoundFeedback> = {},
): LineageFeedbackSpec {
	return {
		feedback: {
			id: feedbackId,
			destination: "ON_WORK",
			state: "DELIVERED",
			deliveredAt: at("13:41:30"),
			withdrawn: false,
			...overrides,
		},
		basis,
	};
}

/** One comment on the pull request from the not met result. */
const oneComment = (overrides: Partial<ReviewBoundFeedback> = {}) =>
	stateFeedback(FEEDBACK_A, [RESULT_A], overrides);

const baseReview: ReviewRef = {
	id: LINEAGE_REVIEW_ID,
	kind: "ITEM",
	item: reviewItem,
	itemVersion: "27f4e88c9f5a",
	origin: "EVENT",
	status: "COMPLETE",
	createdAt: at("13:35:00"),
	completedAt: at("13:44:00"),
};

interface ChainSpec {
	item?: ItemRef;
	/** The review's ref; `null` when its record is gone. */
	review?: Partial<ReviewRef> | null;
	results?: LineageResultSpec[];
	feedback?: LineageFeedbackSpec[];
}

/** A graph asked for by its review, or by its item once the review's record is gone. */
function chain({
	item = reviewItem,
	review = {},
	results,
	feedback,
}: ChainSpec = {}): ReviewLineage {
	const ref = review === null ? null : { ...baseReview, item, ...review };
	const resultsOf = (results ?? threeResults()).map((entry) => ({
		...entry,
		result: {
			...entry.result,
			item: entry.result.item.id === reviewItem.id ? item : entry.result.item,
		},
	}));
	return storyLineage({
		root: ref === null ? { kind: "ITEM", id: item.id } : { kind: "REVIEW", id: ref.id },
		item,
		reviews: ref === null ? [] : [ref],
		results: resultsOf,
		feedback: feedback ?? [oneComment()],
	});
}

/** The review's detail for a case, built on the completed review with what the case needs on top. */
function detailOf(lineage: ReviewLineage, overrides: Partial<ReviewDetail> = {}): ReviewDetail {
	const ref = lineage.nodes.find((entry) => entry.review !== null)?.review ?? baseReview;
	return {
		...reviewDetailCompleted,
		id: ref.id,
		item: ref.item,
		origin: ref.origin,
		status: ref.status,
		createdAt: ref.createdAt,
		orderedAt: ref.createdAt,
		completedAt: ref.completedAt,
		...overrides,
	};
}

const withCase = (
	key: string,
	title: string,
	lineage: ReviewLineage,
	review?: (lineage: ReviewLineage) => ReviewDetail,
): LineageStateCase => ({ key, title, lineage, review: review?.(lineage) });

// ------------------------------------------------------------------------------------------------
// The review: where it is or how it ended
// ------------------------------------------------------------------------------------------------

const withheldPractice: ReviewPractice = {
	practiceSlug: "product-language",
	practiceName: practiceNameOf("product-language"),
	practiceVersionId: "201",
	number: 1,
	status: "WITHHELD",
	reason: "The practice reads the review threads, and the provider did not return them.",
	itemCounts: { listed: 1, reviewed: 0, reused: 0, withheld: 1, notReviewed: 0 },
	results: { met: 0, notMet: 0, doesNotApply: 0, couldNotDetermine: 0 },
	script: null,
};

const reviewCases: LineageStateCase[] = [
	withCase(
		"queued",
		"Queued",
		chain({ review: { status: "QUEUED", completedAt: null }, results: [], feedback: [] }),
	),
	withCase(
		"running",
		"Running, two results so far",
		chain({
			review: { status: "RUNNING", completedAt: null },
			results: threeResults().slice(0, 2),
			feedback: [],
		}),
	),
	withCase("complete", "Complete", chain()),
	withCase(
		"failed",
		"Failed after recording one result",
		chain({ review: { status: "FAILED" }, results: threeResults().slice(0, 1), feedback: [] }),
		(lineage) => detailOf(lineage, { stopReason: "EXECUTION_FAILED" }),
	),
	withCase(
		"timed-out",
		"Timed out with nothing recorded",
		chain({ review: { status: "FAILED", completedAt: at("13:55:10") }, results: [], feedback: [] }),
		(lineage) => detailOf(lineage, { stopReason: "TIMED_OUT" }),
	),
	withCase(
		"cancelled",
		"Canceled by an admin",
		chain({
			review: { status: "CANCELLED", completedAt: at("13:36:00") },
			results: [],
			feedback: [],
		}),
		(lineage) => detailOf(lineage, { stopReason: "CANCELLED_BY_ADMIN" }),
	),
	withCase(
		"withheld-practice",
		"A practice withheld, with no result",
		chain({ results: threeResults().slice(0, 2) }),
		(lineage) =>
			detailOf(lineage, { practices: [...reviewDetailCompleted.practices, withheldPractice] }),
	),
	withCase(
		"expired-runtime",
		"Execution details expired",
		chain({
			review: { completedAt: dayAt("05-02", "13:44:00"), createdAt: dayAt("05-02", "13:35:00") },
		}),
		(lineage) => detailOf(lineage, { runtime: null, stageTiming: [] }),
	),
	{
		key: "scan-many-jobs",
		title: "A scan carried by four jobs",
		lineage: chain({
			item: scannedRepository,
			review: {
				id: reviewDetailScanManyJobs.id,
				kind: "SCAN",
				status: "PARTIAL",
				origin: "REQUEST",
				itemVersion: null,
			},
			results: scannedFiles.map((file, index) =>
				stateResult(id(3, index + 1), {
					reviewId: reviewDetailScanManyJobs.id,
					item: file,
					developer: null,
					outcome: index === 0 ? "NOT_MET" : "MET",
					severity: index === 0 ? "MAJOR" : null,
					summary: index === 0 ? "The give-up line names no event" : null,
				}),
			),
			feedback: [],
		}),
		review: reviewDetailScanManyJobs,
	},
	withCase("record-gone", "The record of the review is gone", chain({ review: null })),
];

// ------------------------------------------------------------------------------------------------
// What started it
// ------------------------------------------------------------------------------------------------

const originCases: LineageStateCase[] = [
	withCase("origin-event", "Started by an event", chain()),
	withCase(
		"origin-request",
		"Requested by an admin",
		chain({ review: { origin: "REQUEST" } }),
		(lineage) => detailOf(lineage, { startedBy: null, requestedBy: reviewAccounts.grace }),
	),
	withCase(
		"origin-schedule",
		"Started by a schedule",
		chain({ review: { origin: "SCHEDULE" } }),
		(lineage) => detailOf(lineage, { startedBy: null }),
	),
	withCase(
		"origin-backfill",
		"One of a batch over past work",
		chain({
			review: { origin: "BACKFILL" },
			feedback: [oneComment({ state: "WITHHELD", deliveredAt: null })],
		}),
		(lineage) =>
			detailOf(lineage, { startedBy: null, batchId: "0b0b0b0b-1111-4111-8111-111111111111" }),
	),
];

// ------------------------------------------------------------------------------------------------
// The item reviewed
// ------------------------------------------------------------------------------------------------

const itemCases: LineageStateCase[] = [
	withCase("item-github", "A pull request on GitHub", chain()),
	withCase("item-gitlab", "A merge request on GitLab", chain({ item: gitlabMergeRequest })),
	withCase(
		"item-slack",
		"A Slack thread",
		chain({
			item: slackConversation,
			feedback: [oneComment({ destination: "CONVERSATION" })],
		}),
	),
	withCase("item-outline", "An Outline document", chain({ item: outlineDocument })),
	withCase(
		"item-pull-request-review",
		"A review of a pull request, under its pull request",
		chain({
			item: pullRequestReviewItem,
			results: [
				stateResult(RESULT_A, {
					practiceSlug: "review-comments-explain",
					outcome: "MET",
					severity: null,
					developer: reviewDevelopers.grace,
					summary: "Each comment says what to change and why",
				}),
			],
			feedback: [
				oneComment({ destination: "PRACTICE_PAGE", state: "WITHHELD", deliveredAt: null }),
			],
		}),
	),
];

// ------------------------------------------------------------------------------------------------
// Results: outcome by severity, currentness, an earlier review's answers, none, many
// ------------------------------------------------------------------------------------------------

const outcomeGrid: LineageResultSpec[] = [
	...(["CRITICAL", "MAJOR", "MINOR", "TRIVIAL"] as const).map((severity, index) =>
		stateResult(
			id(4, index + 1),
			{
				summary: `Not met, ${severity.toLowerCase()}`,
				severity,
				recordedAt: at(`13:3${6 + (index % 3)}:00`),
			},
			[problemOf(id(4, index + 1), 0, severity)],
		),
	),
	stateResult(id(4, 5), {
		summary: "Met",
		outcome: "MET",
		severity: null,
		practiceSlug: "thin-controllers",
		recordedAt: at("13:39:00"),
	}),
	stateResult(id(4, 6), {
		summary: null,
		outcome: "MET",
		severity: null,
		practiceSlug: "tests-name-the-behaviour",
		recordedAt: at("13:39:05"),
	}),
	stateResult(id(4, 7), {
		summary: "Does not apply",
		outcome: "DOES_NOT_APPLY",
		severity: null,
		practiceSlug: "the-change-explains-itself",
		recordedAt: at("13:39:10"),
	}),
	stateResult(id(4, 8), {
		summary: "Could not determine whether the migration runs first",
		outcome: "COULD_NOT_DETERMINE",
		severity: null,
		practiceSlug: "product-language",
		recordedAt: at("13:39:20"),
	}),
];

const OUTCOMES = ["NOT_MET", "MET", "MET", "DOES_NOT_APPLY", "COULD_NOT_DETERMINE"] as const;

const manyResults: LineageResultSpec[] = Array.from({ length: 24 }, (_, index) => {
	const outcome = OUTCOMES[index % OUTCOMES.length] ?? "MET";
	const resultId = id(5, index + 1);
	return stateResult(
		resultId,
		{
			summary: `Result ${index + 1} of a large review`,
			outcome,
			severity: outcome === "NOT_MET" ? "MINOR" : null,
			recordedAt: at(
				`13:${String(36 + Math.floor(index / 6)).padStart(2, "0")}:${String((index % 6) * 10).padStart(2, "0")}`,
			),
		},
		outcome === "NOT_MET" ? [problemOf(resultId, 0, "MINOR")] : [],
	);
});

const resultCases: LineageStateCase[] = [
	withCase(
		"outcomes",
		"Every outcome, and not met by severity",
		chain({ results: outcomeGrid, feedback: [] }),
	),
	withCase(
		"stale",
		"Stale: the work changed after the review",
		chain({ results: threeResults({ currentness: "STALE" }) }),
	),
	withCase(
		"unverifiable",
		"Unverifiable: whether the work changed cannot be told",
		chain({ results: threeResults({ currentness: "UNVERIFIABLE" }) }),
	),
	withCase(
		"answered-earlier",
		"Two practices answered by an earlier review",
		chain({
			results: [
				...threeResults().slice(0, 1),
				...threeResults()
					.slice(1)
					.map((entry) => ({
						...entry,
						result: { ...entry.result, reviewId: EARLIER_REVIEW_ID, recordedAt: at("11:02:00") },
					})),
			],
		}),
	),
	withCase(
		"evidence-hidden",
		"Not met, its evidence withheld from admins",
		chain({
			results: [
				stateResult(RESULT_A, {}, [
					{ id: problemOf(RESULT_A, 0, "MAJOR").id, severity: "MAJOR", summary: "Evidence hidden" },
				]),
			],
		}),
	),
	withCase("not-met-silent", "Not met, and no feedback written from it", chain({ feedback: [] })),
	withCase("empty", "Complete with no result", chain({ results: [], feedback: [] })),
	withCase(
		"many-results",
		"24 results",
		chain({
			results: manyResults,
			feedback: manyResults
				.filter((entry) => entry.result.outcome === "NOT_MET")
				.map((entry, index) =>
					stateFeedback(id(6, index + 1), [entry.result.id], {
						deliveredAt: at(`13:4${index}:50`),
					}),
				),
		}),
	),
];

// ------------------------------------------------------------------------------------------------
// Feedback: where it went, and what became of it
// ------------------------------------------------------------------------------------------------

const DESTINATIONS = ["ON_WORK", "PRACTICE_PAGE", "CONVERSATION"] as const;

const destinationCases: LineageStateCase[] = [
	withCase(
		"destinations",
		"Every destination",
		chain({
			feedback: DESTINATIONS.map((destination, index) =>
				stateFeedback(id(7, index + 1), [RESULT_A], {
					destination,
					deliveredAt: at(`13:4${index}:20`),
				}),
			),
		}),
	),
	...DESTINATIONS.map((destination) =>
		withCase(
			`destination-${destination.toLowerCase().replaceAll("_", "-")}`,
			`Only ${destination.toLowerCase().replaceAll("_", " ")}`,
			chain({ feedback: [oneComment({ destination })] }),
		),
	),
];

const STATES = [
	"AWAITING_APPROVAL",
	"READY",
	"DELIVERED",
	"UNCONFIRMED",
	"WITHHELD",
	"REJECTED",
	"REPLACED",
	"FAILED",
] as const satisfies readonly ReviewBoundFeedback["state"][];

const deliveryCases: LineageStateCase[] = [
	withCase(
		"delivery-states",
		"Every delivery state",
		chain({
			feedback: STATES.map((state, index) =>
				stateFeedback(id(8, index + 1), [RESULT_A], {
					state,
					destination: state === "UNCONFIRMED" ? "CONVERSATION" : "ON_WORK",
					deliveredAt: state === "DELIVERED" || state === "REPLACED" ? at(`13:4${index}:30`) : null,
				}),
			),
		}),
	),
	withCase(
		"withdrawn",
		"Shown on their practice pages, then withdrawn",
		chain({
			feedback: [oneComment({ destination: "PRACTICE_PAGE", withdrawn: true })],
		}),
	),
	withCase(
		"replaced",
		"An earlier comment, replaced by this one",
		chain({
			feedback: [
				oneComment(),
				stateFeedback(id(9, 1), [RESULT_A], {
					state: "REPLACED",
					deliveredAt: at("09:12:20"),
				}),
			],
		}),
	),
	...STATES.map((state) =>
		withCase(
			`state-${state.toLowerCase().replaceAll("_", "-")}`,
			`Only ${state.toLowerCase().replaceAll("_", " ")}`,
			chain({
				feedback: [
					oneComment({
						state,
						deliveredAt: state === "DELIVERED" || state === "REPLACED" ? at("13:41:30") : null,
					}),
				],
			}),
		),
	),
];

// ------------------------------------------------------------------------------------------------
// Volume
// ------------------------------------------------------------------------------------------------

const volumeCases: LineageStateCase[] = [
	withCase(
		"many-feedback",
		"Ten pieces of feedback from three results",
		chain({
			feedback: Array.from({ length: 10 }, (_, index) =>
				stateFeedback(
					id(10, index + 1),
					index % 3 === 0
						? [RESULT_A, RESULT_B]
						: [[RESULT_A, RESULT_B, RESULT_C][index % 3] ?? RESULT_A],
					{
						destination: index % 2 === 0 ? "ON_WORK" : "PRACTICE_PAGE",
						deliveredAt: at(`13:4${index}:40`),
					},
				),
			),
		}),
	),
];

export const LINEAGE_STATE_CASES: Record<LineageStateGroup, LineageStateCase[]> = {
	review: reviewCases,
	origin: originCases,
	item: itemCases,
	results: resultCases,
	destination: destinationCases,
	delivery: deliveryCases,
	volume: volumeCases,
};

export const LINEAGE_STATE_GROUP_TITLES: Record<LineageStateGroup, string> = {
	review: "How the review ran",
	origin: "What started it",
	item: "The work reviewed",
	results: "Results",
	destination: "Where feedback went",
	delivery: "What became of feedback",
	volume: "Many records",
};

/** One case by key, for the route tests. */
export function lineageStateCase(key: string): LineageStateCase {
	for (const cases of Object.values(LINEAGE_STATE_CASES)) {
		const found = cases.find((entry) => entry.key === key);
		if (found !== undefined) {
			return found;
		}
	}
	throw new Error(`No lineage state case ${key}`);
}

export const STATE_FEEDBACK_ID = FEEDBACK_A;
export const STATE_RESULT_ID = RESULT_A;
