import type {
	AgentJob,
	ReviewLineage,
	ReviewLineageFeedback,
	ReviewLineageObservation,
} from "@/api/types.gen";
import type { FeedbackLocation } from "@/components/practice-vocabulary/feedback-location-defs";
import type { ReviewTrigger } from "@/components/practice-vocabulary/review-trigger-defs";

import { gitlabMergeRequest, mockJobCompleted } from "./fixtures";
import {
	at,
	dayAt,
	EARLIER_REVIEW_ID,
	LINEAGE_REVIEW_ID,
	lineageFeedback,
	lineageObservation,
	storyLineage,
} from "./lineage-fixtures";

/**
 * Every state the story can meet, one small chain per state, grouped the way the state matrix
 * stories show them. Each chain is the same pull request, reviewed from 13:35, so only the state in
 * question differs between two of them.
 */
export interface LineageStateCase {
	key: string;
	/** What the case shows, as its heading in the matrix. */
	title: string;
	lineage: ReviewLineage;
	/** Why a completed review assessed nothing; the review’s page reads it from the job. */
	reviewOutcome?: AgentJob["reviewOutcome"];
	/** The review's own record, where the case needs what only it says: an error, a first read. */
	job?: AgentJob;
}

export type LineageStateGroup =
	| "review"
	| "trigger"
	| "merge"
	| "observations"
	| "location"
	| "delivery"
	| "withholding"
	| "decision"
	| "response"
	| "volume";

const id = (group: number, index: number) =>
	`5e5e${String(group).padStart(4, "0")}-0000-4000-8000-${String(index).padStart(12, "0")}`;

const OBS_A = id(1, 1);
const OBS_B = id(1, 2);
const OBS_C = id(1, 3);

/** Three observations: one not met behind the feedback, one met, one not applicable. */
const threeObservations = (overrides: Partial<ReviewLineageObservation> = {}) => [
	lineageObservation(OBS_A, {
		summary: "A cache miss and a permission failure come back as the same 404",
		severity: "MAJOR",
		observedAt: at("13:39:00"),
		feedbackCount: 1,
		...overrides,
	}),
	lineageObservation(OBS_B, {
		summary: "The controller delegates before it does anything else",
		practiceSlug: "thin-controllers",
		practiceName: "Thin controllers",
		outcome: "MET",
		observedAt: at("13:40:00"),
	}),
	lineageObservation(OBS_C, {
		summary: "The work changes no public interface, so there is nothing to document",
		practiceSlug: "public-interfaces-are-documented",
		practiceName: "Public interfaces are documented",
		outcome: "NOT_APPLICABLE",
		observedAt: at("13:40:20"),
	}),
];

const FB_A = id(2, 1);

/** One comment on the pull request from the not-met observation, delivered 30 s later. */
const oneComment = (overrides: Partial<ReviewLineageFeedback> = {}) =>
	lineageFeedback(FB_A, [OBS_A], { createdAt: at("13:41:00"), ...overrides });

const finished = at("13:44:00");

const chain = (overrides: Partial<ReviewLineage> = {}): ReviewLineage =>
	storyLineage({
		review: {
			id: LINEAGE_REVIEW_ID,
			status: "COMPLETED",
			phase: "COMPLETED",
			createdAt: at("13:35:00"),
			startedAt: at("13:35:10"),
			finishedAt: finished,
			model: "claude-sonnet-4-5",
		},
		reviewTrigger: "READY",
		workMoments: [
			{ kind: "READY", occurredAt: at("13:34:50"), startedReview: true, joinedReview: false },
		],
		observations: threeObservations(),
		feedback: [oneComment()],
		...overrides,
	});

const { review } = chain();
if (review === undefined) {
	throw new Error("The state fixtures need a review");
}
const baseReview = review;

/** The review's own record for a chain, with what the case needs on top. */
function jobOf(lineage: ReviewLineage, overrides: Partial<AgentJob> = {}): AgentJob {
	const run = lineage.review ?? baseReview;
	return {
		...mockJobCompleted,
		id: run.id,
		status: run.phase,
		createdAt: run.createdAt,
		availableAt: run.createdAt,
		startedAt: run.startedAt,
		completedAt: run.finishedAt,
		model: run.model,
		...overrides,
	};
}

const failedChain = chain({
	review: { ...baseReview, status: "FAILED", phase: "FAILED", finishedAt: at("13:43:00") },
	observations: [],
	feedback: [],
});

const slowQueueChain = chain({
	review: { ...baseReview, startedAt: at("15:45:00"), finishedAt: at("15:52:00") },
	observations: threeObservations().map((entry) => ({ ...entry, observedAt: at("15:50:00") })),
	feedback: [oneComment({ createdAt: at("15:51:00"), deliveredAt: at("15:51:20") })],
});

const cancelledChain = chain({
	review: {
		...baseReview,
		status: "FAILED",
		phase: "CANCELLED",
		startedAt: undefined,
		finishedAt: at("13:35:04"),
	},
	observations: [],
	feedback: [],
});

// ------------------------------------------------------------------------------------------------
// The review: where it is or how it ended
// ------------------------------------------------------------------------------------------------

const reviewCases: LineageStateCase[] = [
	{
		key: "queued",
		title: "Queued",
		lineage: chain({
			review: {
				...baseReview,
				status: "IN_PROGRESS",
				phase: "QUEUED",
				startedAt: undefined,
				finishedAt: undefined,
			},
			observations: [],
			feedback: [],
		}),
	},
	{
		key: "on-hold",
		title: "Held on the AI budget",
		lineage: chain({
			review: {
				...baseReview,
				status: "IN_PROGRESS",
				phase: "QUEUED",
				holdReason: "BUDGET",
				startedAt: undefined,
				finishedAt: undefined,
			},
			observations: [],
			feedback: [],
		}),
	},
	{
		key: "running",
		title: "Running, two observations so far",
		lineage: chain({
			review: { ...baseReview, status: "IN_PROGRESS", phase: "RUNNING", finishedAt: undefined },
			observations: threeObservations().slice(0, 2),
			feedback: [],
		}),
	},
	{ key: "completed", title: "Completed", lineage: chain() },
	{
		key: "failed",
		title: "Failed after recording one observation",
		lineage: chain({
			review: { ...baseReview, status: "FAILED", phase: "FAILED" },
			observations: threeObservations().slice(0, 1),
			feedback: [],
		}),
	},
	{
		key: "timed-out",
		title: "Timed out with nothing recorded",
		lineage: chain({
			review: { ...baseReview, status: "FAILED", phase: "TIMED_OUT", finishedAt: at("13:55:10") },
			observations: [],
			feedback: [],
		}),
	},
	{
		key: "cancelled",
		title: "Canceled",
		lineage: chain({
			review: { ...baseReview, status: "FAILED", phase: "CANCELLED", finishedAt: at("13:36:00") },
			observations: [],
			feedback: [],
		}),
	},
	{
		key: "insufficient-evidence",
		title: "Completed, nothing assessed for lack of evidence",
		reviewOutcome: "INSUFFICIENT_EVIDENCE",
		lineage: chain({ observations: [], feedback: [] }),
	},
	{
		key: "coalesced",
		title: "Answered by an earlier review on the same code",
		reviewOutcome: "COALESCED",
		lineage: chain({
			observations: threeObservations().map((entry) => ({
				...entry,
				agentJobId: EARLIER_REVIEW_ID,
				observedAt: at("11:02:00"),
			})),
			feedback: [],
		}),
	},
	{
		key: "folded",
		title: "A later push folded into the review",
		lineage: chain({
			workMoments: [
				{ kind: "READY", occurredAt: at("13:34:50"), startedReview: true, joinedReview: false },
				{ kind: "PUSHED", occurredAt: at("13:36:30"), startedReview: false, joinedReview: true },
			],
		}),
	},
	{
		key: "waited",
		title: "Waited 19 minutes in the queue",
		lineage: chain({
			review: { ...baseReview, startedAt: at("13:54:00"), finishedAt: at("14:03:00") },
			observations: threeObservations().map((entry) => ({ ...entry, observedAt: at("13:58:00") })),
			feedback: [oneComment({ createdAt: at("14:00:00"), deliveredAt: at("14:00:20") })],
		}),
	},
	{
		key: "failed-with-error",
		title: "Failed with its error, after a retry",
		lineage: failedChain,
		job: jobOf(failedChain, {
			retryCount: 1,
			errorMessage:
				"The model output did not match the review schema twice: observations[2].evidence[0].quote was not found in the diff.",
		}),
	},
	{
		key: "slow-queue",
		title: "Waited 2 hours in the queue",
		lineage: slowQueueChain,
		job: jobOf(slowQueueChain, { firstFeedbackReadAt: at("16:30:00") }),
	},
	{
		key: "cancelled-declined",
		title: "Canceled: a developer declined AI review",
		lineage: cancelledChain,
		job: jobOf(cancelledChain, { cancellationReason: "MEMBER_AI_DECLINED" }),
	},
	{
		key: "record-gone",
		title: "The record of the review is gone",
		lineage: chain({ review: undefined }),
	},
];

// ------------------------------------------------------------------------------------------------
// What started it
// ------------------------------------------------------------------------------------------------

const TRIGGERS: readonly ReviewTrigger[] = [
	"OPENED",
	"READY",
	"PUSHED",
	"EDITED",
	"REVIEWED",
	"MERGED",
	"CLOSED",
	"PUBLISHED",
	"SETTLED",
	"REQUESTED",
	"BACKFILL",
	"SWEEP",
];

const triggerCases: LineageStateCase[] = [
	...TRIGGERS.map((trigger) => ({
		key: `trigger-${trigger.toLowerCase()}`,
		title: trigger.charAt(0) + trigger.slice(1).toLowerCase(),
		lineage: chain({
			reviewTrigger: trigger,
			workMergedAt: trigger === "MERGED" ? at("13:34:50") : undefined,
			workMoments: [
				{ kind: trigger, occurredAt: at("13:34:50"), startedReview: true, joinedReview: false },
			],
		}),
	})),
	{
		key: "trigger-unknown",
		title: "No moment on the work names the review",
		lineage: chain({ reviewTrigger: undefined, workMoments: [] }),
	},
];

// ------------------------------------------------------------------------------------------------
// The merge against the review
// ------------------------------------------------------------------------------------------------

const withheldAsMerged = (createdAt: Date) =>
	oneComment({
		createdAt,
		deliveryState: "SUPPRESSED",
		suppressionReason: "ARTIFACT_MERGED",
		deliveredAt: undefined,
		placementCount: 0,
	});

const mergeCases: LineageStateCase[] = [
	{
		key: "merged-before",
		title: "Merged before the review was asked for",
		lineage: chain({
			workMergedAt: at("12:10:00"),
			feedback: [withheldAsMerged(at("13:41:00"))],
		}),
	},
	{
		key: "merged-in-queue",
		title: "Merged while the review waited in the queue",
		lineage: chain({
			review: { ...baseReview, startedAt: at("13:50:00"), finishedAt: at("13:58:00") },
			workMergedAt: at("13:42:00"),
			observations: threeObservations().map((entry) => ({ ...entry, observedAt: at("13:54:00") })),
			feedback: [withheldAsMerged(at("13:56:00"))],
		}),
	},
	{
		key: "merged-during",
		title: "Merged while the review ran: its comment was withheld",
		lineage: chain({
			review: { ...baseReview, finishedAt: at("15:02:00") },
			workMergedAt: at("14:51:00"),
			workMoments: [
				{ kind: "READY", occurredAt: at("13:34:50"), startedReview: true, joinedReview: false },
				{ kind: "MERGED", occurredAt: at("14:51:00"), startedReview: false, joinedReview: false },
			],
			feedback: [
				lineageFeedback(id(2, 9), [OBS_B], { createdAt: at("13:41:00") }),
				withheldAsMerged(at("14:58:00")),
			],
		}),
	},
	{
		key: "merged-after",
		title: "Merged a day after the review",
		lineage: chain({ workMergedAt: dayAt("07-29", "09:00:00") }),
	},
	{ key: "never-merged", title: "Not merged", lineage: chain() },
	{
		key: "started-by-merge",
		title: "The merge started the review",
		lineage: chain({
			reviewTrigger: "MERGED",
			workMergedAt: at("13:34:50"),
			workMoments: [
				{ kind: "MERGED", occurredAt: at("13:34:50"), startedReview: true, joinedReview: false },
			],
			feedback: [withheldAsMerged(at("13:41:00"))],
		}),
	},
];

// ------------------------------------------------------------------------------------------------
// Observations: verdict by severity, corrections, an earlier review’s answers, none, many
// ------------------------------------------------------------------------------------------------

const verdictGrid: ReviewLineageObservation[] = [
	...(["CRITICAL", "MAJOR", "MINOR", "INFO"] as const).map((severity, index) =>
		lineageObservation(id(3, index + 1), {
			summary: `Not met, ${severity.toLowerCase()}`,
			severity,
			observedAt: at(`13:3${6 + (index % 3)}:00`),
		}),
	),
	lineageObservation(id(3, 5), {
		summary: "Met",
		outcome: "MET",
		practiceSlug: "thin-controllers",
		practiceName: "Thin controllers",
		observedAt: at("13:39:00"),
	}),
	lineageObservation(id(3, 6), {
		summary: "Not applicable",
		outcome: "NOT_APPLICABLE",
		practiceSlug: "public-interfaces-are-documented",
		practiceName: "Public interfaces are documented",
		observedAt: at("13:39:10"),
	}),
	lineageObservation(id(3, 7), {
		summary: "Undetermined: the diff does not say whether the migration runs first",
		outcome: "UNDETERMINED",
		practiceSlug: "workspace-scoped-queries",
		practiceName: "Queries are scoped to the workspace",
		observedAt: at("13:39:20"),
	}),
];

const manyObservations: ReviewLineageObservation[] = Array.from({ length: 24 }, (_, index) =>
	lineageObservation(id(4, index + 1), {
		summary: `Observation ${index + 1} of a large review`,
		outcome: (["NOT_MET", "MET", "MET", "NOT_APPLICABLE", "UNDETERMINED"] as const)[index % 5],
		severity: index % 5 === 0 ? "MINOR" : undefined,
		practiceSlug: `practice-${index % 8}`,
		practiceName: `Practice ${(index % 8) + 1}`,
		observedAt: at(
			`13:${String(36 + Math.floor(index / 6)).padStart(2, "0")}:${String((index % 6) * 10).padStart(2, "0")}`,
		),
		feedbackCount: index % 5 === 0 ? 1 : 0,
	}),
);

const observationCases: LineageStateCase[] = [
	{
		key: "verdicts",
		title: "Every verdict, and not met by severity",
		lineage: chain({ observations: verdictGrid, feedback: [] }),
	},
	{
		key: "marked-incorrect",
		title: "Marked incorrect, so its card was withheld",
		lineage: chain({
			observations: threeObservations({
				invalidated: true,
				markedIncorrectAt: at("13:58:00"),
			}),
			feedback: [
				oneComment({ deliveredAt: at("13:41:30") }),
				lineageFeedback(id(2, 2), [OBS_A], {
					channel: "IN_APP",
					createdAt: at("13:42:00"),
					deliveryState: "SUPPRESSED",
					suppressionReason: "OBSERVATION_INVALIDATED",
					deliveredAt: undefined,
					placementCount: 0,
				}),
			],
		}),
	},
	{
		key: "answered-earlier",
		title: "Two practices answered by an earlier review",
		lineage: chain({
			observations: [
				...threeObservations().slice(0, 1),
				...threeObservations()
					.slice(1)
					.map((entry) => ({
						...entry,
						agentJobId: EARLIER_REVIEW_ID,
						observedAt: at("11:02:00"),
					})),
			],
		}),
	},
	{
		key: "not-met-silent",
		title: "Not met, and no feedback written from it",
		lineage: chain({ observations: threeObservations({ feedbackCount: 0 }), feedback: [] }),
	},
	{
		key: "later-review-wrote",
		title: "Feedback a later review wrote counts for that review only",
		lineage: chain({
			observations: threeObservations({ feedbackCount: 1, ownFeedbackCount: 0 }),
			feedback: [],
		}),
	},
	{
		key: "empty",
		title: "Completed with no observation",
		lineage: chain({ observations: [], feedback: [] }),
	},
	{
		key: "observations-no-feedback",
		title: "Observations, but no feedback",
		lineage: chain({ observations: verdictGrid.slice(4), feedback: [] }),
	},
	{
		key: "many-observations",
		title: "24 observations",
		lineage: chain({
			observations: manyObservations,
			feedback: manyObservations
				.filter((entry) => entry.feedbackCount > 0)
				.map((entry, index) =>
					lineageFeedback(id(5, index + 1), [entry.id], {
						createdAt: at(`13:4${index}:30`),
						deliveredAt: at(`13:4${index}:50`),
					}),
				),
		}),
	},
];

// ------------------------------------------------------------------------------------------------
// Feedback: where it went, what became of it, and an admin’s hand
// ------------------------------------------------------------------------------------------------

const LOCATIONS: readonly {
	location: FeedbackLocation;
	channel: ReviewLineageFeedback["channel"];
}[] = [
	{ location: "GITHUB", channel: "IN_CONTEXT" },
	{ location: "GITLAB", channel: "IN_CONTEXT" },
	{ location: "OTHER_WORK", channel: "IN_CONTEXT" },
	{ location: "PRACTICE_PAGES", channel: "IN_APP" },
	{ location: "HEPH", channel: "IN_CHAT" },
	{ location: "SLACK", channel: "IN_CHAT" },
	{ location: "NEXT_CONVERSATION", channel: "IN_CHAT" },
];

const locationCases: LineageStateCase[] = [
	{
		key: "locations",
		title: "Every place feedback goes",
		lineage: chain({
			feedback: LOCATIONS.map(({ location, channel }, index) =>
				lineageFeedback(id(6, index + 1), [OBS_A], {
					channel,
					location,
					createdAt: at(`13:4${index}:00`),
					deliveryState: location === "NEXT_CONVERSATION" ? "PREPARED" : "DELIVERED",
					deliveredAt: location === "NEXT_CONVERSATION" ? undefined : at(`13:4${index}:20`),
				}),
			),
		}),
	},
	{
		key: "gitlab-work",
		title: "A merge request on GitLab",
		lineage: chain({
			work: gitlabMergeRequest.reviewedWork,
			feedback: [oneComment({ location: "GITLAB" })],
		}),
	},
];

/** The reason a stopped piece carries, where its state needs one. */
const STOPPED_BY: Partial<
	Record<ReviewLineageFeedback["deliveryState"], ReviewLineageFeedback["suppressionReason"]>
> = {
	SUPPRESSED: "VOLUME_CAPPED",
	PARTIALLY_FAILED: "ARTIFACT_CLOSED",
};

const DELIVERY_STATES: readonly ReviewLineageFeedback["deliveryState"][] = [
	"AWAITING_APPROVAL",
	"PREPARED",
	"PARTIALLY_DELIVERED",
	"PARTIALLY_FAILED",
	"DELIVERED",
	"SUPERSEDED",
	"SUPPRESSED",
	"FAILED",
	"DISCARDED",
	"UNCONFIRMED",
];

/** A card on the practice pages that no developer has opened, which only the review's record tells. */
function inAppUnopened(): LineageStateCase {
	const lineage = chain({
		feedback: [
			oneComment({
				channel: "IN_APP",
				location: "PRACTICE_PAGES",
				deliveryState: "DELIVERED",
				deliveredAt: at("13:41:10"),
			}),
		],
	});
	return {
		key: "in-app-unopened",
		title: "A card on their practice pages, not opened yet",
		lineage,
		job: jobOf(lineage),
	};
}

const deliveryCases: LineageStateCase[] = [
	{
		key: "delivery-states",
		title: "Every delivery state",
		lineage: chain({
			feedback: DELIVERY_STATES.map((deliveryState, index) =>
				lineageFeedback(id(7, index + 1), [OBS_A], {
					createdAt: at(`13:4${index}:00`),
					deliveryState,
					channel: deliveryState === "UNCONFIRMED" ? "IN_CHAT" : "IN_CONTEXT",
					suppressionReason: STOPPED_BY[deliveryState],
					deliveredAt: [
						"DELIVERED",
						"PARTIALLY_DELIVERED",
						"PARTIALLY_FAILED",
						"SUPERSEDED",
					].includes(deliveryState)
						? at(`13:4${index}:30`)
						: undefined,
					replacedAt: deliveryState === "SUPERSEDED" ? dayAt("07-29", "09:00:00") : undefined,
				}),
			),
		}),
	},
	{
		key: "replaced",
		title: "An earlier review’s comment, replaced by this one",
		lineage: chain({
			feedback: [
				oneComment({ replacesId: id(8, 1) }),
				lineageFeedback(id(8, 1), [id(8, 2)], {
					agentJobId: EARLIER_REVIEW_ID,
					createdAt: at("09:12:00"),
					deliveredAt: at("09:12:20"),
					deliveryState: "SUPERSEDED",
					replacedById: FB_A,
					replacedAt: at("13:41:00"),
				}),
			],
		}),
	},
	{
		key: "shown-then-withheld",
		title: "Shown on their practice pages, then withheld",
		lineage: chain({
			feedback: [
				oneComment({
					channel: "IN_APP",
					deliveryState: "SUPPRESSED",
					suppressionReason: "OUTSIDE_CURRENT_COVERAGE",
					deliveredAt: at("13:42:00"),
				}),
			],
		}),
	},
	{
		key: "partly-withheld",
		title: "Partly delivered, the rest withheld as a draft",
		lineage: chain({
			feedback: [
				oneComment({
					deliveryState: "PARTIALLY_DELIVERED",
					suppressionReason: "ARTIFACT_DRAFT",
					deliveredAt: at("13:41:20"),
				}),
			],
		}),
	},
	inAppUnopened(),
	{
		key: "in-chat-waiting",
		title: "A brief waiting for a conversation",
		lineage: chain({
			feedback: [
				oneComment({
					channel: "IN_CHAT",
					location: "NEXT_CONVERSATION",
					deliveryState: "PREPARED",
					deliveredAt: undefined,
				}),
			],
		}),
	},
];

/** Every reason feedback can be withheld for, as the API names them. */
const WITHHOLDING_REASONS: readonly NonNullable<ReviewLineageFeedback["suppressionReason"]>[] = [
	"VOLUME_CAPPED",
	"COMPOSER_DEDUPED",
	"COMPOSER_WITHHELD",
	"REPEATS_DELIVERED_NOTE",
	"REACTED_DISPUTED",
	"REACTED_NOT_APPLICABLE",
	"CONVERSATION_EXPIRED",
	"ARTIFACT_GONE",
	"ARTIFACT_CLOSED",
	"ISSUE_SNAPSHOT_CHANGED",
	"ARTIFACT_MERGED",
	"ARTIFACT_DRAFT",
	"RECIPIENT_OPTED_OUT",
	"EMPTY_AFTER_SANITIZE",
	"INSTANCE_SILENCED",
	"WORKSPACE_DISABLED",
	"WORKSPACE_DELIVERY_PAUSED",
	"STALE_ROLLOUT_REVISION",
	"OUTSIDE_CURRENT_COVERAGE",
	"APPROVAL_STALE",
	"APPROVAL_NO_LONGER_ELIGIBLE",
	"PRACTICE_REQUIRES_APPROVAL",
	"BACKFILL_QUIET",
	"OBSERVATION_INVALIDATED",
];

/** The reasons only a conversation brief can meet. */
const CHAT_ONLY: ReadonlySet<string> = new Set(["CONVERSATION_EXPIRED"]);

/** One reason from each family of reasons feedback is withheld for. */
const withholdingCases: LineageStateCase[] = [
	{
		key: "withholding-families",
		title: "Withheld, one reason of each kind",
		lineage: chain({
			feedback: (
				[
					"ARTIFACT_CLOSED",
					"VOLUME_CAPPED",
					"RECIPIENT_OPTED_OUT",
					"OBSERVATION_INVALIDATED",
					"COMPOSER_DEDUPED",
				] as const
			).map((suppressionReason, index) =>
				lineageFeedback(id(9, index + 1), [OBS_A], {
					createdAt: at(`13:4${index}:00`),
					deliveryState: "SUPPRESSED",
					suppressionReason,
					deliveredAt: undefined,
					placementCount: 0,
				}),
			),
		}),
	},
	...WITHHOLDING_REASONS.map((suppressionReason) => {
		const lineage = chain({
			workMergedAt: suppressionReason === "ARTIFACT_MERGED" ? at("13:39:30") : undefined,
			feedback: [
				oneComment({
					channel: CHAT_ONLY.has(suppressionReason) ? "IN_CHAT" : "IN_CONTEXT",
					deliveryState: "SUPPRESSED",
					suppressionReason,
					deliveredAt: undefined,
					placementCount: 0,
				}),
			],
		});
		return {
			key: `withheld-${suppressionReason.toLowerCase().replaceAll("_", "-")}`,
			title: `Withheld: ${suppressionReason}`,
			lineage,
			job: jobOf(lineage),
		};
	}),
];

const decisionCases: LineageStateCase[] = [
	{
		key: "decisions",
		title: "Awaiting approval, approved, rejected, withdrawn",
		lineage: chain({
			feedback: [
				lineageFeedback(id(10, 1), [OBS_A], {
					createdAt: at("13:41:00"),
					deliveryState: "AWAITING_APPROVAL",
					suppressionReason: "PRACTICE_REQUIRES_APPROVAL",
					deliveredAt: undefined,
				}),
				lineageFeedback(id(10, 2), [OBS_A], {
					createdAt: at("13:42:00"),
					decision: "APPROVED",
					decidedAt: at("14:30:00"),
					deliveredAt: at("14:30:20"),
				}),
				lineageFeedback(id(10, 3), [OBS_A], {
					createdAt: at("13:43:00"),
					deliveryState: "DISCARDED",
					decision: "REJECTED",
					decidedAt: at("14:31:00"),
					deliveredAt: undefined,
				}),
				lineageFeedback(id(10, 4), [OBS_A], {
					channel: "IN_APP",
					createdAt: at("13:44:00"),
					deliveredAt: at("13:50:00"),
					withdrawnAt: dayAt("07-29", "10:00:00"),
				}),
			],
		}),
	},
];

// ------------------------------------------------------------------------------------------------
// The developer’s answer
// ------------------------------------------------------------------------------------------------

const answered = (
	index: number,
	response: ReviewLineageFeedback["response"],
	overrides: Partial<ReviewLineageFeedback> = {},
) =>
	lineageFeedback(id(11, index), [OBS_A], {
		createdAt: at(`13:4${index}:00`),
		deliveredAt: at(`13:4${index}:30`),
		response,
		...overrides,
	});

const responseCases: LineageStateCase[] = [
	{
		key: "responses",
		title: "Every answer, and none",
		lineage: chain({
			feedback: [
				// What an admin may know of an answer: that it was given, or that it disputes the
				// feedback. Ratings and other decisions stay private to the developer.
				answered(1, { disputed: false, respondedAt: at("15:00:00") }),
				answered(2, { disputed: false, respondedAt: at("15:10:00") }),
				answered(3, {
					disputed: true,
					respondedAt: at("16:05:00"),
					disputeExplanation: "The two cases share a cause, so one status is right here.",
				}),
				answered(4, { disputed: false, respondedAt: dayAt("07-29", "08:00:00") }),
				answered(5, { disputed: false, respondedAt: at("15:30:00") }),
				answered(
					6,
					{ disputed: false, resolvedByWorkAt: dayAt("08-06", "11:02:00") },
					{ channel: "IN_APP" },
				),
				answered(7, undefined),
				answered(8, undefined, {
					deliveryState: "SUPPRESSED",
					suppressionReason: "COMPOSER_DEDUPED",
					deliveredAt: undefined,
				}),
			],
		}),
	},
];

// ------------------------------------------------------------------------------------------------
// Volume
// ------------------------------------------------------------------------------------------------

const volumeCases: LineageStateCase[] = [
	{
		key: "many-feedback",
		title: "Ten pieces of feedback from three observations",
		lineage: chain({
			feedback: Array.from({ length: 10 }, (_, index) =>
				lineageFeedback(
					id(12, index + 1),
					index % 3 === 0 ? [OBS_A, OBS_B] : [[OBS_A, OBS_B, OBS_C][index % 3] ?? OBS_A],
					{
						channel: index % 2 === 0 ? "IN_CONTEXT" : "IN_APP",
						createdAt: at(`13:4${index}:00`),
						deliveredAt: at(`13:4${index}:40`),
					},
				),
			),
		}),
	},
];

export const LINEAGE_STATE_CASES: Record<LineageStateGroup, LineageStateCase[]> = {
	review: reviewCases,
	trigger: triggerCases,
	merge: mergeCases,
	observations: observationCases,
	location: locationCases,
	delivery: deliveryCases,
	withholding: withholdingCases,
	decision: decisionCases,
	response: responseCases,
	volume: volumeCases,
};

export const LINEAGE_STATE_GROUP_TITLES: Record<LineageStateGroup, string> = {
	review: "How the review ran",
	trigger: "What started it",
	merge: "The merge against the review",
	observations: "Observations",
	location: "Where feedback went",
	delivery: "What became of feedback",
	withholding: "Why feedback was withheld",
	decision: "An admin’s decision",
	response: "The developer’s answer",
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

export const STATE_FEEDBACK_ID = FB_A;
export const STATE_OBSERVATION_ID = OBS_A;
