import type { ObservationDetail, PracticeGroupReviewObservation } from "@/api/types.gen";

export interface ObservationDetailState {
	isLoading: boolean;
	detail?: ObservationDetail;
	error?: unknown;
}

export type FeedbackUsefulness = NonNullable<PracticeGroupReviewObservation["feedbackUsefulness"]>;
