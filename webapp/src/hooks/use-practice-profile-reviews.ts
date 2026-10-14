import { skipToken, useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";

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
	/** The open review level's review id, if any. */
	reviewId?: string;
	itemKind?: string;
	since?: ReviewTimeframe;
}

/** The open review, with its results, the reader's feedback from it and what they may see of its trace. */
export type ProfileReviewOpenState = PanelState<{ detail: ProfileReviewDetail }>;

/** A review that may still record something, so its level asks again until it stops. */
function isActive(review: ReviewRef | undefined): boolean {
	return review?.status === "QUEUED" || review?.status === "RUNNING";
}

/**
 * The two review levels' data: the reviews that recorded something about the reader's own work,
 * and the one open over it, which carries the reader's own trace of its item.
 */
export function usePracticeProfileReviews({
	workspaceSlug,
	listOpen,
	reviewId,
	itemKind,
	since,
}: ProfileReviewsRequest) {
	const now = useNow();
	const reviewsQuery = useInfiniteQuery({
		...listOwnReviewsInfiniteOptions({
			path: { workspaceSlug },
			query: {
				size: PROFILE_REVIEWS_PAGE_SIZE,
				itemKind,
				since: since === undefined ? undefined : reviewRangeStart(now, since),
			},
		}),
		...pagedModelParams,
		enabled: listOpen,
		// A review is listed once it records something about the reader, which no ask here announces.
		refetchInterval: PRACTICE_PROFILE_POLL_MS,
	});
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
	// The list polls only every minute, so it can predate the open review's final results; the
	// review stopping is what asks it once more.
	const queryClient = useQueryClient();
	const lastSeen = useRef({ reviewId, active });
	useEffect(() => {
		const before = lastSeen.current;
		lastSeen.current = { reviewId, active };
		if (before.reviewId === reviewId && before.active && !active) {
			void queryClient.invalidateQueries({
				queryKey: listOwnReviewsInfiniteQueryKey({ path: { workspaceSlug } }),
			});
		}
	});

	// The open review's head explains a refusal about its own item; a row anywhere else gets a toast.
	const openItemId = reviewQuery.data?.review.item.id;
	const request = useRequestPracticeReview(workspaceSlug, {
		showsInline: (itemId) => reviewId !== undefined && itemId === openItemId,
	});
	// A refusal is said on the review it was asked from and on no other; leaving the level forgets
	// it, so it never comes back on a later visit.
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
		// Counted only over every review there is; a page or a filter would count short.
		positions:
			list.status === "ready" && !list.hasMore && !hasText(itemKind) && since === undefined
				? reviewPositionsOnItem(list.reviews.map(({ review }) => review))
				: undefined,
		open,
		onReviewNow: request.ask,
		/** The item an ask is in flight about. */
		requesting: request.asking,
		refusal: request.refusal?.outcome,
	};
}
