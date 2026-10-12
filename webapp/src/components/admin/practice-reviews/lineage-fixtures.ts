import type {
	ReviewLineage,
	ReviewLineageFeedback,
	ReviewLineageResult,
	ReviewFeedbackDetail,
	ReviewResultDetail,
} from "@/api/types.gen";
import type { FeedbackLocation } from "@/components/practice-vocabulary/feedback-location-defs";

import { feedbackDetail, resultDetail, reviewArtifact, reviewResultDetail } from "./fixtures";

/** The chain the fixture's records sit in, answered from the same records their levels read. */
export function reviewLineage(kind: string, id: string): ReviewLineage {
	if (kind === "FEEDBACK") {
		return lineageOfFeedback(feedbackDetail(id));
	}
	return lineageOfResult(kind === "RESULT" ? resultDetail(id) : reviewResultDetail);
}

/** The chain a piece of feedback's level reads, from the record the level shows. */
export function lineageOfFeedback(feedback: ReviewFeedbackDetail): ReviewLineage {
	return {
		work: feedback.reviewedWork,
		review: run(feedback.agentJobId, feedback.createdAt),
		results: feedback.practiceResults.map((result) => ({
			id: result.practiceResultId,
			agentJobId: feedback.agentJobId,
			summary: result.summary,
			practiceSlug: result.practiceSlug,
			practiceName: result.practiceName,
			outcome: result.outcome,
			severity: result.severity,
			problemCount: 0,
			observedAt: result.observedAt,
			focus: true,
			invalidated: false,
			feedbackCount: 1,
			ownFeedbackCount: 1,
		})),
		moreResults: 0,
		reviewTrigger: "READY",
		feedback: [
			{
				id: feedback.id,
				agentJobId: feedback.agentJobId,
				channel: feedback.channel,
				deliveryState: feedback.deliveryState,
				suppressionReason: feedback.suppressionReason,
				createdAt: feedback.createdAt,
				deliveredAt: feedback.deliveredAt,
				placementCount: feedback.placements.length,
				practiceResultIds: feedback.practiceResults.map((result) => result.practiceResultId),
				replacesId: feedback.replacesId,
				focus: true,
				location: feedback.location,
			},
		],
		moreFeedback: 0,
		workMoments: [],
	};
}

/** The chain a result's level reads, from the record the level shows. */
export function lineageOfResult(result: ReviewResultDetail): ReviewLineage {
	return {
		work: result.reviewedWork,
		review: run(result.agentJobId, result.observedAt),
		results: [
			{
				id: result.id,
				agentJobId: result.agentJobId,
				summary: result.summary,
				practiceSlug: result.practiceSlug,
				practiceName: result.practiceName,
				outcome: result.outcome,
				severity: result.severity,
				problemCount: result.observations.length,
				observedAt: result.observedAt,
				focus: true,
				invalidated: result.invalidations.some((entry) => entry.restoredAt === undefined),
				feedbackCount: result.feedback.length,
				ownFeedbackCount: result.feedback.filter(
					(feedback) => feedback.agentJobId === result.agentJobId,
				).length,
			},
		],
		moreResults: 2,
		reviewTrigger: "OPENED",
		feedback: result.feedback.map((feedback): ReviewLineageFeedback => ({
			id: feedback.feedbackId,
			agentJobId: feedback.agentJobId,
			channel: feedback.channel,
			deliveryState: feedback.deliveryState,
			suppressionReason: feedback.suppressionReason,
			createdAt: feedback.createdAt,
			deliveredAt: feedback.deliveredAt,
			placementCount: 1,
			practiceResultIds: [result.id],
			replacesId: feedbackDetail(feedback.feedbackId).replacesId,
			focus: true,
			location: feedbackDetail(feedback.feedbackId).location,
		})),
		moreFeedback: 0,
		workMoments: [],
	};
}

function run(id: string, createdAt: Date): NonNullable<ReviewLineage["review"]> {
	return {
		id,
		status: "COMPLETED",
		phase: "COMPLETED",
		createdAt,
		startedAt: createdAt,
		model: "claude-sonnet-4-5",
	};
}

// ------------------------------------------------------------------------------------------------
// One review of a pull request, told several ways, for the chain's stories. Fixed instants, so a gap
// between two steps reads the same in every run.
// ------------------------------------------------------------------------------------------------

export const LINEAGE_REVIEW_ID = "11111111-1111-1111-1111-111111111111";
export const EARLIER_REVIEW_ID = "11111111-1111-1111-1111-000000000000";

export const at = (time: string): Date => new Date(`2026-07-28T${time}Z`);
export const dayAt = (day: string, time: string): Date => new Date(`2026-${day}T${time}Z`);

export const LINEAGE_OBS = {
	cache404: "66666666-6666-6666-6666-666666666666",
	testNames: "77777777-7777-7777-7777-777777777777",
	description: "88888888-8888-8888-8888-888888888888",
	thinController: "55555555-5555-5555-5555-555555555555",
	scoped: "5a5a5a5a-5555-5555-5555-555555555555",
} as const;

export const LINEAGE_FB = {
	onGitHub: "44444444-4444-4444-4444-444444444444",
	afterMerge: "bbbbbbbb-4444-4444-4444-444444444444",
	praise: "33333333-3333-3333-3333-333333333333",
	deduped: "aaaaaaaa-3333-3333-3333-333333333333",
	card: "cccccccc-4444-4444-4444-444444444444",
	earlier: "99999999-3333-3333-3333-333333333333",
	invalidated: "dddddddd-4444-4444-4444-444444444444",
} as const;

const OBS = LINEAGE_OBS;
const FB = LINEAGE_FB;

/**
 * A result of the chain's review. Its own feedback count follows the total unless the
 * override says a later review wrote some of it.
 */
export const lineageResult = (
	id: string,
	overrides: Partial<ReviewLineageResult>,
): ReviewLineageResult => {
	const base: ReviewLineageResult = {
		id,
		agentJobId: LINEAGE_REVIEW_ID,
		summary: "",
		practiceSlug: "errors-carry-context",
		practiceName: "Errors carry their context",
		outcome: "NOT_MET",
		problemCount: 1,
		observedAt: at("13:39:00"),
		focus: false,
		invalidated: false,
		feedbackCount: 0,
		ownFeedbackCount: 0,
		...overrides,
	};
	return { ...base, ownFeedbackCount: overrides.ownFeedbackCount ?? base.feedbackCount };
};

/** Where a piece on each channel lands for the fixture's pull request, which is on GitHub. */
const LOCATION_BY_CHANNEL: Record<ReviewLineageFeedback["channel"], FeedbackLocation> = {
	IN_CONTEXT: "GITHUB",
	IN_CHAT: "HEPH",
	IN_APP: "PRACTICE_PAGES",
};

export const lineageFeedback = (
	id: string,
	practiceResultIds: string[],
	overrides: Partial<ReviewLineageFeedback>,
): ReviewLineageFeedback => {
	const base: ReviewLineageFeedback = {
		id,
		agentJobId: LINEAGE_REVIEW_ID,
		channel: "IN_CONTEXT",
		deliveryState: "DELIVERED",
		createdAt: at("13:41:00"),
		deliveredAt: at("13:41:30"),
		placementCount: 1,
		practiceResultIds,
		focus: false,
		location: "GITHUB",
		...overrides,
	};
	return { ...base, location: overrides.location ?? LOCATION_BY_CHANNEL[base.channel] };
};

/** The review's five results: three not met, two met. */
function lineageResults(
	focus: readonly string[],
	overrides: Partial<Record<string, Partial<ReviewLineageResult>>> = {},
): ReviewLineageResult[] {
	const base: ReviewLineageResult[] = [
		lineageResult(OBS.cache404, {
			summary: "A cache miss and a permission failure come back as the same 404",
			severity: "MAJOR",
			observedAt: at("13:39:00"),
			feedbackCount: 2,
		}),
		lineageResult(OBS.testNames, {
			summary: "Three of the new tests are named after the method they call",
			practiceSlug: "tests-name-the-behaviour",
			practiceName: "Tests name the behaviour",
			severity: "MINOR",
			observedAt: at("13:38:00"),
			feedbackCount: 1,
		}),
		lineageResult(OBS.description, {
			summary: "The description lists the files touched and stops there",
			practiceSlug: "the-change-explains-itself",
			practiceName: "The change explains itself",
			severity: "INFO",
			observedAt: at("13:37:00"),
			feedbackCount: 1,
		}),
		lineageResult(OBS.thinController, {
			summary: "The controller delegates before it does anything else",
			practiceSlug: "thin-controllers",
			practiceName: "Thin controllers",
			outcome: "MET",
			observedAt: at("13:40:00"),
			feedbackCount: 1,
		}),
		lineageResult(OBS.scoped, {
			summary: "Every new query names the workspace it reads",
			practiceSlug: "workspace-scoped-queries",
			practiceName: "Queries are scoped to the workspace",
			outcome: "MET",
			observedAt: at("13:40:00"),
		}),
	];
	return base.map((entry) => {
		const override = overrides[entry.id] ?? {};
		return lineageResult(entry.id, {
			...entry,
			...override,
			ownFeedbackCount:
				override.ownFeedbackCount ?? override.feedbackCount ?? entry.ownFeedbackCount,
			focus: focus.includes(entry.id),
		});
	});
}

export const storyLineage = (overrides: Partial<ReviewLineage>): ReviewLineage => ({
	work: reviewArtifact.reviewedWork,
	review: {
		id: LINEAGE_REVIEW_ID,
		status: "COMPLETED",
		phase: "COMPLETED",
		createdAt: at("13:35:00"),
		startedAt: at("13:35:00"),
		model: "claude-sonnet-4-5",
	},
	reviewTrigger: "OPENED",
	results: lineageResults([OBS.cache404]),
	moreResults: 0,
	feedback: [],
	moreFeedback: 0,
	workMoments: [],
	...overrides,
});

/** The rest of the review's feedback, which the traced path does not reach. */
const untraced = [
	lineageFeedback(FB.praise, [OBS.thinController], {
		createdAt: at("13:42:00"),
		deliveredAt: at("13:43:00"),
	}),
	lineageFeedback(FB.deduped, [OBS.description], {
		deliveryState: "SUPPRESSED",
		suppressionReason: "COMPOSER_DEDUPED",
		deliveredAt: undefined,
		placementCount: 0,
	}),
];

const onGitHub = lineageFeedback(FB.onGitHub, [OBS.cache404, OBS.testNames], {
	focus: true,
	placementCount: 2,
	response: { disputed: false, respondedAt: at("15:20:00") },
});

/** The result in front led to one comment on the pull request, which the developer found helpful. */
export const lineageResultFocus: ReviewLineage = storyLineage({
	feedback: [onGitHub, ...untraced],
});

/** A card on the developer's practice pages that rests on three of the review's results. */
export const lineageFeedbackFocus: ReviewLineage = storyLineage({
	results: lineageResults([OBS.cache404, OBS.testNames, OBS.description]),
	feedback: [
		lineageFeedback(FB.card, [OBS.cache404, OBS.testNames, OBS.description], {
			focus: true,
			channel: "IN_APP",
			createdAt: at("13:42:00"),
			deliveredAt: dayAt("07-29", "08:14:00"),
			placementCount: 0,
		}),
		...untraced,
	],
});

/**
 * The review started when the work was marked ready. The work was merged more than an hour later,
 * and a piece of feedback composed after that was withheld: the review finished too late.
 */
export const lineageMergedBeforeFinished: ReviewLineage = storyLineage({
	reviewTrigger: "READY",
	workMergedAt: at("14:51:00"),
	feedback: [
		onGitHub,
		lineageFeedback(FB.afterMerge, [OBS.cache404], {
			focus: true,
			createdAt: at("14:58:00"),
			deliveryState: "SUPPRESSED",
			suppressionReason: "ARTIFACT_MERGED",
			deliveredAt: undefined,
			placementCount: 0,
		}),
		...untraced,
	],
});

/** The merge started the review, so feedback withheld as already merged is by design. */
export const lineageStartedByMerge: ReviewLineage = storyLineage({
	reviewTrigger: "MERGED",
	workMergedAt: at("13:20:00"),
	feedback: [
		lineageFeedback(FB.afterMerge, [OBS.cache404], {
			focus: true,
			createdAt: at("13:41:00"),
			deliveryState: "SUPPRESSED",
			suppressionReason: "ARTIFACT_MERGED",
			deliveredAt: undefined,
			placementCount: 0,
		}),
	],
});

/**
 * The developer disputed the comment on the pull request. The card on their practice pages they
 * found helpful, and some days later their work resolved it.
 */
export const lineageDisputedAndResolved: ReviewLineage = storyLineage({
	feedback: [
		{
			...onGitHub,
			response: {
				disputed: true,
				disputeExplanation: "The two cases share a cause, so one status is right here.",
				respondedAt: at("16:05:00"),
			},
		},
		lineageFeedback(FB.card, [OBS.cache404], {
			focus: true,
			channel: "IN_APP",
			createdAt: at("13:42:00"),
			deliveredAt: dayAt("07-29", "08:14:00"),
			placementCount: 0,
			response: {
				disputed: false,
				respondedAt: dayAt("07-29", "08:20:00"),
				resolvedByWorkAt: dayAt("08-06", "11:02:00"),
			},
		}),
		...untraced,
	],
});

// ------------------------------------------------------------------------------------------------
// The same review whole, as the review's page reads it: eight results, the feedback written
// from them, one piece an earlier review wrote and this review's feedback replaced, and the
// moments on the work. Every loss the chain can show happens once.
// ------------------------------------------------------------------------------------------------

export const WHOLE_OBS = {
	...OBS,
	swallowed: "6a6a6a6a-6666-6666-6666-666666666666",
	migration: "5b5b5b5b-5555-5555-5555-555555555555",
	readme: "5c5c5c5c-5555-5555-5555-555555555555",
	/** Recorded by the earlier review, so this chain does not list it. */
	earlier: "66666666-6666-6666-6666-000000000000",
} as const;

const wholeResults = (): ReviewLineageResult[] => [
	...lineageResults([], {
		[OBS.cache404]: { feedbackCount: 3 },
		[OBS.testNames]: { feedbackCount: 2 },
		[OBS.description]: { invalidated: true, markedIncorrectAt: at("13:58:00") },
		[OBS.thinController]: { feedbackCount: 1 },
	}),
	lineageResult(WHOLE_OBS.swallowed, {
		summary: "The retry wrapper drops the exception it caught and throws a new one",
		severity: "MINOR",
		observedAt: at("13:39:30"),
		feedbackCount: 1,
	}),
	lineageResult(WHOLE_OBS.migration, {
		summary: "Cannot tell from the diff whether the migration runs before the new query",
		practiceSlug: "workspace-scoped-queries",
		practiceName: "Queries are scoped to the workspace",
		outcome: "UNDETERMINED",
		observedAt: at("13:40:30"),
	}),
	lineageResult(WHOLE_OBS.readme, {
		summary: "The work changes no public interface, so there is nothing to document",
		practiceSlug: "public-interfaces-are-documented",
		practiceName: "Public interfaces are documented",
		outcome: "NOT_APPLICABLE",
		observedAt: at("13:40:40"),
	}),
];

const wholeFeedback = (): ReviewLineageFeedback[] => [
	lineageFeedback(FB.onGitHub, [OBS.cache404, OBS.testNames], {
		placementCount: 2,
		replacesId: FB.earlier,
		response: {
			disputed: true,
			disputeExplanation: "The two cases share a cause, so one status is right here.",
			respondedAt: at("16:05:00"),
		},
	}),
	lineageFeedback(FB.card, [OBS.cache404, WHOLE_OBS.swallowed, OBS.testNames], {
		channel: "IN_APP",
		createdAt: at("13:42:00"),
		deliveredAt: dayAt("07-29", "08:14:00"),
		placementCount: 0,
		response: {
			disputed: false,
			respondedAt: dayAt("07-29", "08:20:00"),
			resolvedByWorkAt: dayAt("08-06", "11:02:00"),
		},
	}),
	lineageFeedback(FB.afterMerge, [OBS.cache404], {
		createdAt: at("14:58:00"),
		deliveryState: "SUPPRESSED",
		suppressionReason: "ARTIFACT_MERGED",
		deliveredAt: undefined,
		placementCount: 0,
	}),
	lineageFeedback(FB.deduped, [OBS.description], {
		createdAt: at("13:41:10"),
		deliveryState: "SUPPRESSED",
		suppressionReason: "COMPOSER_DEDUPED",
		deliveredAt: undefined,
		placementCount: 0,
	}),
	lineageFeedback(FB.praise, [OBS.thinController], {
		createdAt: at("13:42:00"),
		deliveredAt: at("13:43:00"),
	}),
	lineageFeedback(FB.earlier, [WHOLE_OBS.earlier], {
		agentJobId: EARLIER_REVIEW_ID,
		createdAt: at("09:12:00"),
		deliveredAt: at("09:12:20"),
		deliveryState: "SUPERSEDED",
		replacedById: FB.onGitHub,
	}),
];

/**
 * The review started when the work was marked ready, and the work merged while it still ran: the
 * note it wrote after the merge was withheld. That is the case the page has to make obvious.
 */
export const wholeReview: ReviewLineage = storyLineage({
	review: {
		id: LINEAGE_REVIEW_ID,
		status: "COMPLETED",
		phase: "COMPLETED",
		createdAt: at("13:35:00"),
		startedAt: at("13:35:20"),
		finishedAt: at("15:02:00"),
		model: "claude-sonnet-4-5",
	},
	reviewTrigger: "READY",
	workMergedAt: at("14:51:00"),
	workMoments: [
		{ kind: "OPENED", occurredAt: at("13:20:00"), startedReview: false, joinedReview: false },
		{ kind: "READY", occurredAt: at("13:34:40"), startedReview: true, joinedReview: false },
		{ kind: "PUSHED", occurredAt: at("14:20:00"), startedReview: false, joinedReview: true },
		{ kind: "MERGED", occurredAt: at("14:51:00"), startedReview: false, joinedReview: false },
	],
	results: wholeResults(),
	feedback: wholeFeedback(),
});

/** The merge started the review, so the note withheld as already merged is by design. */
export const wholeReviewStartedByMerge: ReviewLineage = {
	...wholeReview,
	review: {
		id: LINEAGE_REVIEW_ID,
		status: "COMPLETED",
		phase: "COMPLETED",
		createdAt: at("14:51:20"),
		startedAt: at("14:51:25"),
		finishedAt: at("14:58:30"),
		model: "claude-sonnet-4-5",
	},
	reviewTrigger: "MERGED",
	workMoments: [
		{ kind: "OPENED", occurredAt: at("13:20:00"), startedReview: false, joinedReview: false },
		{ kind: "MERGED", occurredAt: at("14:51:00"), startedReview: true, joinedReview: false },
	],
	feedback: wholeFeedback().filter((entry) => entry.id !== FB.earlier),
};

/** Twelve pieces of feedback from the same eight results, for the lines to cross. */
export const wholeReviewManyFeedback: ReviewLineage = {
	...wholeReview,
	feedback: [
		...wholeFeedback(),
		...[
			[OBS.thinController, OBS.scoped],
			[WHOLE_OBS.readme],
			[OBS.description, OBS.cache404],
			[WHOLE_OBS.migration, OBS.scoped],
			[OBS.testNames],
			[WHOLE_OBS.swallowed, WHOLE_OBS.readme],
		].map((practiceResultIds, index) =>
			lineageFeedback(`eeeeeeee-0000-4000-8000-00000000000${index}`, practiceResultIds, {
				channel: index % 2 === 0 ? "IN_APP" : "IN_CONTEXT",
				createdAt: at(`13:4${index}:30`),
				deliveredAt: at(`13:4${index}:50`),
			}),
		),
	],
};
