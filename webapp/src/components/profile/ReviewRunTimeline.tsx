import type { PracticeGroupReviewObservation, PracticeGroupReviewRun } from "@/api/types.gen";
import { ReviewRunCard } from "./ReviewRunCard";
import type { FeedbackUsefulness, ObservationDetailState } from "./review-runs";

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
	if (runs.length === 0) {
		return (
			<div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
				No reviewed work matches these filters.
			</div>
		);
	}

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
