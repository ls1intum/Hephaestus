import { type UseQueryResult, useQuery, useQueryClient } from "@tanstack/react-query";
import { subDays } from "date-fns";
import { useEffect, useRef } from "react";

import {
	countPracticeReviewFeedbackByLocationOptions,
	countPracticeReviewFeedbackByReasonOptions,
	getPracticeReviewOverviewOptions,
	getPracticeReviewQueueOptions,
	getPracticeReviewTimingOptions,
	listPracticeReviewsOptions,
} from "@/api/@tanstack/react-query.gen";
import type { PracticeReviewOverview } from "@/api/types.gen";
import { DELIVERED_STATES } from "@/components/admin/practice-reviews/delivery-breakdown";
import type {
	DeliveredByChannelState,
	WithheldByReasonState,
} from "@/components/admin/practice-reviews/DeliveryBreakdownCard";
import {
	type OutcomeScope,
	scopeSearch,
} from "@/components/admin/practice-reviews/review-outcomes";
import { QUEUE_POLL_MS, queueActive } from "@/components/admin/practice-reviews/review-queue";
import {
	REVIEW_RANGE_DEFS,
	type ReviewRange,
} from "@/components/admin/practice-reviews/review-range";
import {
	ACTIVE_REVIEW_POLL_MS,
	dateWindowQuery,
	type RunsSearch,
	runsQuery,
} from "@/components/admin/practice-reviews/review-search";
import {
	type PracticeReviewOverviewState,
	type PreviousReviewPeriodState,
	type ReviewCountState,
	toSectionState,
} from "@/components/admin/practice-reviews/review-states";
import type {
	AttentionGroup,
	ReviewAttentionProps,
} from "@/components/admin/practice-reviews/ReviewAttention";
import type { ReviewQueueState } from "@/components/admin/practice-reviews/ReviewQueueNow";
import type { ReviewTimingState } from "@/components/admin/practice-reviews/ReviewTimeCard";
import { panelState } from "@/components/common/panel-state";
import { awaitingApprovalOptions } from "@/hooks/use-feedback-awaiting-approval";
import { browserTimeZone } from "@/lib/dates";
import { invalidateWorkspaceReads } from "@/runtime/tanstack-query/invalidate-workspace-reads";
import { keepSameSubject } from "@/runtime/tanstack-query/keep-same-subject";

/**
 * What the reviews did from `from` until now, in the reader's time zone, or since the workspace's
 * first review where `from` is absent, for all time; re-read while a review in it is still queued
 * or running. While another range loads, the previous range's figures stand in, marked stale.
 */
export function usePracticeReviewOverview(
	workspaceSlug: string,
	from: Date | undefined,
): PracticeReviewOverviewState {
	const query = useQuery({
		...getPracticeReviewOverviewOptions({
			path: { workspaceSlug },
			query: { ...rangeQuery(from), zone: browserTimeZone() },
		}),
		placeholderData: keepSameSubject({ workspaceSlug }),
		refetchInterval: ({ state: { data } }) =>
			data !== undefined && reviewsActive(data) ? ACTIVE_REVIEW_POLL_MS : false,
	});
	return panelState(query, (overview) => ({
		status: "ready" as const,
		overview,
		stale: query.isPlaceholderData,
	}));
}

/** A range from `from` on, or all time where it is absent: the server then starts at the first review. */
function rangeQuery(from: Date | undefined): { from: Date } | { allTime: true } {
	return from === undefined ? { allTime: true } : { from };
}

/**
 * How long the reviews from `from` until now took, or over all time where it is absent, re-read with the overview while a review
 * in the range is queued or running, since a review still waiting is one of its counts.
 */
export function usePracticeReviewTiming(
	workspaceSlug: string,
	from: Date | undefined,
	overview: PracticeReviewOverviewState,
): ReviewTimingState {
	const query = useQuery({
		...getPracticeReviewTimingOptions({ path: { workspaceSlug }, query: rangeQuery(from) }),
		placeholderData: keepSameSubject({ workspaceSlug }),
		refetchInterval:
			overview.status === "ready" && reviewsActive(overview.overview)
				? ACTIVE_REVIEW_POLL_MS
				: false,
	});
	return panelState(query, (timing) => ({
		status: "ready" as const,
		timing,
		stale: query.isPlaceholderData,
	}));
}

/**
 * The queue as it stands now, re-read every ten seconds while something in it waits or runs, or
 * while the overview counts a review as queued or running, so a review requested while the queue
 * was empty shows up. Only while the overview is on screen, and, as every poll, not while the
 * browser tab is hidden: TanStack Query keeps `refetchIntervalInBackground` off.
 */
export function usePracticeReviewQueue(
	workspaceSlug: string,
	overview: PracticeReviewOverviewState,
): ReviewQueueState {
	const overviewActive = overview.status === "ready" && reviewsActive(overview.overview);
	const query = useQuery({
		...getPracticeReviewQueueOptions({ path: { workspaceSlug } }),
		refetchInterval: ({ state: { data } }) =>
			overviewActive || (data !== undefined && queueActive(data)) ? QUEUE_POLL_MS : false,
	});
	return panelState(query, (queue) => ({ status: "ready" as const, queue }));
}

/**
 * The period of the same length before `from`, for the overview's totals to be set against. A read
 * of its own, so the overview never waits for it, and nothing but the totals pays for it. It is not
 * re-read, since nothing in it moves. All time has no period before it, so nothing is read and no
 * total is set against anything.
 */
export function usePreviousReviewPeriod(
	workspaceSlug: string,
	from: Date | undefined,
	range: ReviewRange,
): PreviousReviewPeriodState {
	const { days } = REVIEW_RANGE_DEFS[range];
	const query = useQuery({
		...getPracticeReviewOverviewOptions({
			path: { workspaceSlug },
			query: {
				from: from === undefined ? undefined : subDays(from, days),
				to: from,
				zone: browserTimeZone(),
			},
		}),
		enabled: from !== undefined,
	});
	if (from === undefined) {
		return { status: "none" };
	}
	return panelState(query, (overview) => ({
		status: "ready" as const,
		period: { overview },
	}));
}

/** What Needs you shows. */
export type ReviewAttentionReads = ReviewAttentionProps;

/** A count read as the total of a one-row page: the problems line needs how many, never which. */
const COUNT_ONLY = 1;

/** A one-row page's total, stale while another range's stands in for the range just chosen. */
function toCountState(
	query: UseQueryResult<{ page?: { totalElements?: number } }, unknown>,
): ReviewCountState {
	return panelState(query, (page) => ({
		status: "ready" as const,
		total: page.page?.totalElements ?? 0,
		stale: query.isPlaceholderData,
	}));
}

/**
 * What an admin owes the reviews: every piece of feedback awaiting approval, whenever it was
 * composed and oldest first. The approvals are the admin navigation's read too, one cache entry,
 * and are re-read on the overview's schedule, so the page and its navigation never show two
 * different answers to "how many await approval".
 */
export function useReviewAttention(
	workspaceSlug: string,
	overview: PracticeReviewOverviewState,
): ReviewAttentionReads {
	const approvalsQuery = useQuery({
		...awaitingApprovalOptions(workspaceSlug),
		placeholderData: keepSameSubject({ workspaceSlug }),
		refetchInterval:
			overview.status === "ready" && reviewsActive(overview.overview)
				? ACTIVE_REVIEW_POLL_MS
				: false,
	});
	return {
		approvals: {
			state: toSectionState(approvalsQuery),
		},
	};
}

/**
 * What the Delivery card splits: delivered feedback by channel, withheld feedback by reason, and
 * the reviews whose results could not be processed or delivered, out of those that completed.
 */
export interface DeliveryBreakdownReads {
	delivered: DeliveredByChannelState;
	withheld: WithheldByReasonState;
	unprocessedResults: AttentionGroup<ReviewCountState>;
}

/**
 * The feedback of `scope` split as the Delivery card shows it: delivered by channel and withheld by
 * reason, each read with the Feedback table's own filter, so a row and the table it opens hold the
 * same rows. Counts only: the server returns no text, rating or comment. Re-read with the overview
 * while a review in the range is queued or running.
 */
export function useDeliveryBreakdown(
	workspaceSlug: string,
	scope: OutcomeScope,
	overview: PracticeReviewOverviewState,
): DeliveryBreakdownReads {
	const path = { workspaceSlug };
	const days = dateWindowQuery(scope);
	const live = {
		placeholderData: keepSameSubject({ workspaceSlug }),
		refetchInterval:
			overview.status === "ready" && reviewsActive(overview.overview)
				? ACTIVE_REVIEW_POLL_MS
				: false,
	} as const;
	const deliveredQuery = useQuery({
		...countPracticeReviewFeedbackByLocationOptions({
			path,
			query: { ...days, deliveryState: [...DELIVERED_STATES] },
		}),
		...live,
	});
	const withheldQuery = useQuery({
		...countPracticeReviewFeedbackByReasonOptions({
			path,
			query: { ...days, deliveryState: ["SUPPRESSED"] },
		}),
		...live,
	});
	const unprocessed: Partial<RunsSearch> = {
		...scopeSearch(scope, "runs"),
		resultProcessing: ["FAILED"],
	};
	const unprocessedQuery = useQuery({
		...listPracticeReviewsOptions({ path, query: runsQuery(unprocessed, COUNT_ONLY) }),
		...live,
	});
	return {
		unprocessedResults: {
			state: toCountState(unprocessedQuery),
			list: { list: "runs", search: unprocessed },
			// Only a completed review has results to process: a failed or timed-out one is counted on
			// the Reviews tile instead, so the two figures never share a review.
			of: overview.status === "ready" ? overview.overview.reviews.completed : undefined,
		},
		delivered: panelState(deliveredQuery, (counts) => ({
			status: "ready" as const,
			counts,
			stale: deliveredQuery.isPlaceholderData,
		})),
		withheld: panelState(withheldQuery, (counts) => ({
			status: "ready" as const,
			counts,
			stale: withheldQuery.isPlaceholderData,
		})),
	};
}

/**
 * The overview, the review time, the attention counts and the queue each re-read while a review is
 * active, on the overview's word. When the overview says the last one finished, the others may hold
 * a read from just before: read them once more, so the page settles on one answer.
 */
export function useSettleWhenReviewsFinish(
	workspaceSlug: string,
	overview: PracticeReviewOverviewState,
) {
	const queryClient = useQueryClient();
	const active = overview.status === "ready" && reviewsActive(overview.overview);
	const wasActive = useRef(active);
	useEffect(() => {
		const before = wasActive.current;
		wasActive.current = active;
		if (before && !active) {
			void invalidateWorkspaceReads(queryClient, workspaceSlug, SETTLING_READS);
		}
	}, [active, queryClient, workspaceSlug]);
}

const SETTLING_READS: ReadonlySet<string> = new Set([
	"countPracticeReviewFeedbackByLocation",
	"countPracticeReviewFeedbackByReason",
	"getPracticeReviewTiming",
	"getPracticeReviewQueue",
	"listPracticeReviews",
	"listPracticeReviewFeedback",
]);

function reviewsActive({ reviews }: PracticeReviewOverview): boolean {
	return reviews.queued + reviews.running > 0;
}
