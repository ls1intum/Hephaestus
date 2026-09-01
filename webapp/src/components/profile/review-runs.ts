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
export type { FeedbackUsefulness } from "@/components/practice-vocabulary/feedback-usefulness-defs";
/** Complete replacement payload for a feedback response. */
export type FeedbackResponse = FeedbackResponseRequest;
export function isEmptyFeedbackResponse(response: FeedbackResponse): boolean {
	return (
		response.usefulness === undefined &&
		response.resolution === undefined &&
		(response.comment === undefined || response.comment.trim() === "")
	);
}
export function feedbackResponseOf(observation: PracticeGroupReviewObservation): FeedbackResponse {
	return {
		usefulness: observation.feedbackUsefulness,
		resolution: observation.feedbackResolution,
		comment: observation.feedbackResponseComment,
	};
}
