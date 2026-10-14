import { skipToken, useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useQueryReadRefusal } from "@/runtime/tanstack-query/use-query-read-refusal";

import {
	getOwnReviewOptions,
	listOwnReviewsInfiniteOptions,
	listOwnReviewsInfiniteQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { ProfileReviewDetail, ReviewRef } from "@/api/types.gen";
import { ACTIVE_REVIEW_POLL_MS } from "@/components/admin/practice-reviews/review-search";
import { type PanelState, panelState } from "@/components/common/panel-state";
import { useNow } from "@/components/common/use-now";
import {
	PROFILE_REVIEWS_PAGE_SIZE,
	reviewRangeStart,
	type ReviewTimeframe,
} from "@/components/practice-profile/practice-profile-search";
import { reviewPositionsOnItem } from "@/components/practice-trace/review-groups";
import {
	EMPTY_REVIEW_FEED,
	type ReviewFeedState,
	reviewFeedState,
} from "@/components/profile/reviews";
import { useInvalidateOnSettle } from "@/hooks/use-invalidate-on-settle";
import { PRACTICE_PROFILE_POLL_MS } from "@/hooks/use-practice-profile-overview";
import { useRequestPracticeReview } from "@/hooks/use-request-practice-review";
import { problemStatusOf } from "@/lib/problem-detail";
import { hasText } from "@/lib/text";
import { QUERY_RETRIES, sessionRetriesQueries } from "@/runtime/tanstack-query/query-defaults";
import { pagedModelParams } from "@/runtime/tanstack-query/spring-page";

interface ProfileReviewsRequest {
	workspaceSlug: string;
	/** True while a level that shows the list is open; a closed drawer reads nothing. */
	listOpen: boolean;
	reviewId?: string;
	itemKind?: string;
	since?: ReviewTimeframe;
}

/** The open review, with its results, the reader's feedback from it and what they may see of its trace. */
export type ProfileReviewOpenState = PanelState<{ detail: ProfileReviewDetail }>;

function isActive(review: ReviewRef | undefined): boolean {
	return review?.status === "QUEUED" || review?.status === "RUNNING";
}

export function usePracticeProfileReviews({
	workspaceSlug,
	listOpen,
	reviewId,
	itemKind,
	since,
}: ProfileReviewsRequest) {
	const now = useNow();
	const listOptions = listOwnReviewsInfiniteOptions({
		path: { workspaceSlug },
		query: {
			size: PROFILE_REVIEWS_PAGE_SIZE,
			itemKind,
			since: since === undefined ? undefined : reviewRangeStart(now, since),
		},
	});
	const reviewsQuery = useInfiniteQuery({
		...listOptions,
		...pagedModelParams,
		enabled: listOpen,
		refetchInterval: PRACTICE_PROFILE_POLL_MS,
	});
	useQueryReadRefusal(listOptions.queryKey, reviewsQuery.isError, reviewsQuery.error);
	const reviewOptions = getOwnReviewOptions({
		path: { workspaceSlug, reviewId: reviewId ?? "" },
	});
	const reviewQuery = useQuery({
		...reviewOptions,
		queryFn: reviewId === undefined ? skipToken : reviewOptions.queryFn,
		// A 404 is not the reader's review, or gone: asking again answers the same.
		retry: (failureCount, error) =>
			sessionRetriesQueries() && problemStatusOf(error) !== 404 && failureCount < QUERY_RETRIES,
		refetchInterval: (query) =>
			isActive(query.state.data?.review) ? ACTIVE_REVIEW_POLL_MS : false,
	});
	const active = isActive(reviewQuery.data?.review);

	const list: ReviewFeedState = listOpen ? reviewFeedState(reviewsQuery) : EMPTY_REVIEW_FEED;
	// Refresh the slower-polled list when an open review ends so its final results appear.
	useInvalidateOnSettle(reviewId, active, [
		listOwnReviewsInfiniteQueryKey({ path: { workspaceSlug } }),
	]);

	const openItemId = reviewQuery.data?.review.item.id;
	const request = useRequestPracticeReview(workspaceSlug, {
		showsInline: (itemId) => reviewId !== undefined && itemId === openItemId,
	});
	// Refusals belong to the active review level; clear them on leaving it.
	const [visited, setVisited] = useState(reviewId);
	if (visited !== reviewId) {
		setVisited(reviewId);
		request.forgetRefusal();
	}

	const open: ProfileReviewOpenState = panelState(reviewQuery, (detail) => ({
		status: "ready" as const,
		detail,
	}));

	return {
		list,
	// Exact positions require every review; filters or partial pages would undercount.
		positions:
			list.status === "ready" && !list.hasMore && !hasText(itemKind) && since === undefined
				? reviewPositionsOnItem(list.reviews.map(({ review }) => review))
				: undefined,
		open,
		onReviewNow: request.ask,
		requesting: request.asking,
		refusal: request.refusal?.outcome,
	};
}
