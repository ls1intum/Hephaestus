import type { PracticeGroupReviewObservation, PracticeGroupReviewRun } from "@/api/types.gen";
import type { FeedbackResponse, ObservationDetailState } from "./review-runs";
import { ReviewRunCard } from "./ReviewRunCard";

export interface ReviewRunTimelineProps {
	runs: PracticeGroupReviewRun[];
	openObservationId?: string;
	observationDetail?: ObservationDetailState;
	onToggleObservation?: (observationId: string) => void;
	/** The developer's complete answer to one piece of feedback; the endpoint replaces, not patches. */
	onRespond?: (observation: PracticeGroupReviewObservation, response: FeedbackResponse) => void;
	pendingFeedbackId?: string;
}

export function ReviewRunTimeline({
	runs,
	openObservationId,
	observationDetail,
	onToggleObservation,
	onRespond,
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
					onRespond={onRespond}
					pendingFeedbackId={pendingFeedbackId}
				/>
			))}
		</ol>
	);
}
