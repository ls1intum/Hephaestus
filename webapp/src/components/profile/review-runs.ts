import type {
	FeedbackResponseRequest,
	ObservationDetail,
	PracticeGroupReviewObservation,
} from "@/api/types.gen";

export interface ObservationDetailState {
	isLoading: boolean;
	detail?: ObservationDetail;
	error?: unknown;
}

/** Re-exported from the registry that owns the enum, so the two halves of a response agree. */
export type { FeedbackUsefulness } from "@/components/practice-vocabulary/feedback-usefulness-defs";

/**
 * A developer's complete answer to one piece of feedback. The endpoint **replaces** rather than
 * patches, so the whole answer travels: sending one changed half would erase the others. An answer
 * with no field set is how a response is withdrawn.
 */
export type FeedbackResponse = FeedbackResponseRequest;

/** True when nothing is left to record, which the caller sends as a deletion rather than an update. */
export function isEmptyFeedbackResponse(response: FeedbackResponse): boolean {
	return (
		response.usefulness === undefined &&
		response.resolution === undefined &&
		(response.comment === undefined || response.comment.trim() === "")
	);
}

/** The response an observation already carries, as the form's starting point. */
export function feedbackResponseOf(observation: PracticeGroupReviewObservation): FeedbackResponse {
	return {
		usefulness: observation.feedbackUsefulness,
		resolution: observation.feedbackResolution,
		comment: observation.feedbackResponseComment,
	};
}
