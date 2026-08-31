import type { PracticeGroupReviewObservation, PracticeGroupReviewRun } from "@/api/types.gen";
import type { FeedbackUsefulness, ObservationDetailState } from "./review-runs";
import { ReviewRunCard } from "./ReviewRunCard";

export type { FeedbackUsefulness, ObservationDetailState } from "./review-runs";

export interface ReviewRunTimelineProps {
	runs: PracticeGroupReviewRun[];
	openObservationId?: string;
	observationDetail?: ObservationDetailState;
	onToggleObservation?: (observationId: string) => void;
	onChangeUsefulness?: (
		observation: PracticeGroupReviewObservation,
		usefulness?: FeedbackUsefulness,
	) => void;
	pendingFeedbackId?: string;
}

export function ReviewRunTimeline({
	runs,
	openObservationId,
	observationDetail,
	onToggleObservation,
	onChangeUsefulness,
	pendingFeedbackId,
}: ReviewRunTimelineProps) {
	// No empty state here: the caller decides what an empty feed means — no runs at all, or none
	// matching the filters — and renders that itself. A second one here could only ever be wrong.
	return (
		<ol className="flex min-w-0 flex-col" aria-label="Review runs">
			{runs.map((run) => (
				<ReviewRunCard
					key={run.reviewId}
					run={run}
					openObservationId={openObservationId}
					observationDetail={observationDetail}
					onToggleObservation={onToggleObservation}
					onChangeUsefulness={onChangeUsefulness}
					pendingFeedbackId={pendingFeedbackId}
				/>
			))}
		</ol>
	);
}
