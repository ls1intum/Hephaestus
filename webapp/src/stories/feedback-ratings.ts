import { useState } from "react";

import type { FeedbackResponseRequest } from "@/api/types.gen";
import type {
	FeedbackBand,
	FeedbackRatingProps,
} from "@/components/practice-vocabulary/PracticeFeedbackCard";
import {
	type FeedbackResponseControls,
	isEmptyFeedbackResponse,
	responseRequestOf,
} from "@/components/profile/reviews";
import {
	nextRating,
	nextResolution,
	opensNoteBand,
	withComment,
	withDisagreement,
	withoutDisagreement,
} from "@/hooks/use-practice-page-feedback";

/** What the reader said about one piece of feedback, and the band still open under it. */
interface FeedbackRating {
	response: FeedbackResponseRequest;
	openBand?: FeedbackBand;
}

/**
 * The ratings a story holds in place of the server, keyed by feedback id: the same contract
 * `usePracticePageFeedback` fulfils on the wire, without a network, and on the hook's own `nextRating`,
 * `withComment` and `nextResolution` rules — a press on a rating opens the comment band, unless a
 * disagreement stands; pressing the chosen rating again withdraws it and closes the band; Send and
 * Skip close the band and keep the rating; Addressed and Not applicable replace the answer, and a
 * second press takes it back; Disagree opens the band that asks why, whose Send writes the
 * disagreement, and takes a standing one back. The
 * card's state is the fixture's, since closing it is the server's to decide.
 */
export function useFeedbackRatings() {
	const [ratings, setRatings] = useState<Record<string, FeedbackRating | undefined>>({});
	const update = (
		feedbackId: string,
		next: (current: FeedbackRating | undefined) => FeedbackRating | undefined,
	) => setRatings((current) => ({ ...current, [feedbackId]: next(current[feedbackId]) }));

	/** The card props that wire one piece of feedback to this state. */
	const ratingProps = (feedbackId: string): FeedbackRatingProps => ({
		usefulness: ratings[feedbackId]?.response.usefulness,
		resolution: ratings[feedbackId]?.response.resolution,
		openBand: ratings[feedbackId]?.openBand,
		onRate: (usefulness) =>
			update(feedbackId, (current) => {
				const response = nextRating(current?.response, usefulness);
				return { response, openBand: opensNoteBand(response) ? "comment" : undefined };
			}),
		onSendComment: (comment) =>
			update(
				feedbackId,
				(current) => current && { response: withComment(current.response, comment) },
			),
		onSkipComment: () => update(feedbackId, (current) => current && { response: current.response }),
		onDisagree: () =>
			update(feedbackId, (current) =>
				current?.response.resolution === "DISAGREE"
					? { response: withoutDisagreement(current.response) }
					: {
							response: current?.response ?? {},
							openBand: current?.openBand === "disagreement" ? undefined : "disagreement",
						},
			),
		onSendDisagreement: (comment) =>
			update(feedbackId, (current) => ({
				response: withDisagreement(current?.response, comment),
			})),
		onResolve: (answer) =>
			update(feedbackId, (current) => ({
				response: nextResolution(current?.response, answer),
				openBand: current?.openBand,
			})),
	});

	return { ratingProps };
}

/**
 * The responses a story holds in place of the server, keyed by feedback id: the contract
 * `useFeedbackResponseWrite` fulfils on the wire, without a network. A write applies its change over
 * the response that stands and a response with nothing left in it is cleared, as the hook does.
 * With `pending` a write never lands, so the story shows the response being written and every
 * control waiting.
 */
export function useStoryFeedbackResponses({ pending = false } = {}): FeedbackResponseControls {
	const [written, setWritten] = useState<ReadonlyMap<string, FeedbackResponseRequest>>(new Map());
	const responseOf: FeedbackResponseControls["responseOf"] = (feedbackId, recorded) =>
		written.get(feedbackId) ?? responseRequestOf(recorded);
	const store = (feedbackId: string, response: FeedbackResponseRequest) =>
		setWritten((current) => new Map(current).set(feedbackId, response));
	return {
		write: (feedbackId, recorded, change) => {
			const response = { ...responseOf(feedbackId, recorded), ...change };
			store(feedbackId, isEmptyFeedbackResponse(response) ? {} : response);
		},
		clear: (feedbackId) => store(feedbackId, {}),
		responseOf,
		pendingResponses: pending ? written : new Map(),
	};
}
