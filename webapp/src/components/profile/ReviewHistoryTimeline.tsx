import { OBSERVATION_OUTCOME_PRESENTATION, observationOutcome } from "./observation-outcome";
import { ReviewHistoryMoment } from "./ReviewHistoryMoment";
import type {
	FindingChange,
	ObservationDetailState,
	ReviewedArtifact,
	ReviewFinding,
	ReviewHistoryEntry,
	ReviewRun,
} from "./review-history";

export type {
	ArtifactKind,
	ObservationDetailState,
	ReviewedArtifact,
	ReviewFinding,
	ReviewRun,
} from "./review-history";

function runsNewestFirst(artifact: ReviewedArtifact) {
	return [...artifact.runs].sort((left, right) => right.reviewedAt.localeCompare(left.reviewedAt));
}

/** Compare only the same recurrence locus on an earlier run of the same artifact. */
function changeSincePreviousRun(
	finding: ReviewFinding,
	earlierRuns: ReviewRun[],
): FindingChange | undefined {
	if (!finding.recurrenceKey) return undefined;
	const currentPolarity =
		OBSERVATION_OUTCOME_PRESENTATION[observationOutcome(finding)].trendPolarity;
	if (currentPolarity === null) return undefined;

	for (const run of earlierRuns) {
		const previous = run.findings.find(
			(candidate) => candidate.recurrenceKey === finding.recurrenceKey,
		);
		if (!previous) continue;
		const previousPolarity =
			OBSERVATION_OUTCOME_PRESENTATION[observationOutcome(previous)].trendPolarity;
		if (previousPolarity === null) continue;
		if (previousPolarity === currentPolarity) return undefined;
		return {
			direction: currentPolarity > previousPolarity ? "IMPROVED" : "REGRESSED",
			previousAt: run.reviewedAt,
		};
	}
	return undefined;
}

export interface ReviewHistoryTimelineProps {
	artifacts: ReviewedArtifact[];
	/** Mirrors the practice filter owned by the left-hand column in the real detail page. */
	selectedPracticeSlug?: string;
	openObservationId?: string;
	observationDetail?: ObservationDetailState;
	onToggleObservation?: (observationId: string) => void;
	onRateFeedback?: (feedbackId: string, helpful?: boolean) => void;
	pendingFeedbackId?: string;
}

/** Strict reverse-chronological review moments composed from real or Storybook data. */
export function ReviewHistoryTimeline({
	artifacts,
	selectedPracticeSlug,
	openObservationId,
	observationDetail,
	onToggleObservation,
	onRateFeedback,
	pendingFeedbackId,
}: ReviewHistoryTimelineProps) {
	const entries = artifacts
		.flatMap((artifact) => {
			const runs = runsNewestFirst(artifact);
			return runs.map((run, index): ReviewHistoryEntry | undefined => {
				const findings = selectedPracticeSlug
					? run.findings.filter((finding) => finding.practiceSlug === selectedPracticeSlug)
					: run.findings;
				if (findings.length === 0) return undefined;
				return { artifact, run, findings, earlierRuns: runs.slice(index + 1) };
			});
		})
		.filter((entry): entry is ReviewHistoryEntry => entry !== undefined)
		.sort((left, right) => right.run.reviewedAt.localeCompare(left.run.reviewedAt));

	if (entries.length === 0) {
		return (
			<div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
				No feedback has been recorded for this practice yet.
			</div>
		);
	}

	return (
		<ol className="flex min-w-0 flex-col" aria-label="Feedback over time">
			{entries.map((entry) => {
				const changes = Object.fromEntries(
					entry.findings.map((finding) => [
						finding.observationId,
						changeSincePreviousRun(finding, entry.earlierRuns),
					]),
				);
				return (
					<ReviewHistoryMoment
						key={entry.run.reviewId}
						artifact={entry.artifact}
						run={entry.run}
						findings={entry.findings}
						changes={changes}
						openObservationId={openObservationId}
						observationDetail={observationDetail}
						showPracticeNames={!selectedPracticeSlug}
						onToggleObservation={onToggleObservation}
						onRateFeedback={onRateFeedback}
						pendingFeedbackId={pendingFeedbackId}
					/>
				);
			})}
		</ol>
	);
}
