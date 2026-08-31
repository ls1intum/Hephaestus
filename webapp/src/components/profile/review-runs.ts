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
 * A developer's complete answer to one piece of delivered feedback.
 *
 * The wire type is what travels, because the endpoint **replaces** the response rather than patching
 * it: sending only the half a control just changed would erase the other half. Carrying the whole
 * answer through the props makes that impossible to get wrong — an earlier version passed usefulness
 * alone, and the route had to read the remaining fields back off the observation to avoid clearing
 * them.
 *
 * Every field is optional and an answer with none of them set is how a response is withdrawn.
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
