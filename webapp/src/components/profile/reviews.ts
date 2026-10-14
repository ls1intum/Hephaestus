import type {
	FeedbackResponse,
	FeedbackResponseRequest,
	ProfileReview,
	ResultList,
} from "@/api/types.gen";
import type { PanelState } from "@/components/common/panel-state";
import { hasText } from "@/lib/text";
import {
	type InfiniteQueryLike,
	infiniteListState,
	type MorePages,
} from "@/runtime/tanstack-query/infinite-list";

/**
 * A feed of the reader's own reviews, newest first, with its paging while earlier ones exist: a
 * practice level's reviews on one group, the reviews of one item, or every review of the reader's
 * work.
 */
export type ReviewFeedState = PanelState<{ reviews: ProfileReview[] } & MorePages>;

/**
 * A level with no reviews of its own: ready, empty, and with nothing more to load — `hasMore:
 * false` keeps the paging button off screen, so the callback is never reached.
 */
export const EMPTY_REVIEW_FEED = {
	status: "ready",
	reviews: [],
	hasMore: false,
	isLoadingMore: false,
	onLoadMore: () => undefined,
} satisfies ReviewFeedState;

/** One infinite query as the feed's state: failed, loading, or the loaded pages as one list. */
export function reviewFeedState(
	query: InfiniteQueryLike<{ content?: ProfileReview[] }>,
): ReviewFeedState {
	return infiniteListState(query, (pages) => ({
		reviews: pages.flatMap((page) => page.content ?? []),
	}));
}

/**
 * The reader's response to each piece of their feedback, as a route wires it: one object handed
 * down the feed to every piece. A result can be the basis of several pieces of feedback, so every
 * write names the feedback, never the result.
 */
export interface FeedbackResponseControls {
	/**
	 * Applies `change` over the response that stands — the one being written, else `recorded` — and
	 * writes the complete result, so a new resolution keeps the usefulness and a new usefulness
	 * keeps the resolution. A key present with no value removes that dimension. A response with nothing
	 * left in it is cleared.
	 */
	write: (
		feedbackId: string,
		recorded: FeedbackResponse | null,
		change: FeedbackResponseRequest,
	) => void;
	/** "Clear response": back to no response at all. */
	clear: (feedbackId: string) => void;
	/** The response that stands: the one being written, else the recorded one. */
	responseOf: (feedbackId: string, recorded: FeedbackResponse | null) => FeedbackResponseRequest;
	/**
	 * The response being written to each piece of feedback, from whichever surface; a clear is the
	 * empty response. A surface shows it over the recorded one, and its buttons wait until the write
	 * lands.
	 */
	pendingResponses: ReadonlyMap<string, FeedbackResponseRequest>;
}

export function isEmptyFeedbackResponse(response: FeedbackResponseRequest): boolean {
	return (
		response.usefulness === undefined &&
		response.resolution === undefined &&
		!hasText(response.comment?.trim())
	);
}

/** A recorded response as the request that would write it again; a cleared one is empty. */
export function responseRequestOf(
	response: FeedbackResponse | null | undefined,
): FeedbackResponseRequest {
	return {
		usefulness: response?.usefulness ?? undefined,
		resolution: response?.resolution ?? undefined,
		comment: response?.comment ?? undefined,
	};
}

/**
 * What a result's head line says: its summary, or the practice's name when it has none, as a met
 * result may not.
 */
export function resultSummary({
	summary,
	practiceName,
}: Pick<ResultList, "summary" | "practiceName">) {
	const trimmed = summary?.trim();
	return hasText(trimmed) ? trimmed : practiceName;
}
