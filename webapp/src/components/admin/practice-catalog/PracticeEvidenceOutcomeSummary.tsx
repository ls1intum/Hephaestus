import type { PracticeEvidenceOutcome, PracticeEvidenceSourceOption } from "@/api/types.gen";
import { evidenceSourceLabel } from "@/components/admin/practice-catalog/evidence-presentation";

export interface PracticeEvidenceOutcomeSummaryProps {
	outcome: PracticeEvidenceOutcome;
	sources: readonly PracticeEvidenceSourceOption[];
}

/**
 * What these requirements have actually done to recent reviews.
 *
 * An author sets requirements against an idea of what the sources usually hold, and until now nothing
 * told them whether that idea was right — a requirement that skips four reviews in five looked exactly
 * like one that never skips. Every run already recorded the decision; this reads it back.
 */
export function PracticeEvidenceOutcomeSummary({
	outcome,
	sources,
}: PracticeEvidenceOutcomeSummaryProps) {
	if (outcome.consideredReviews === 0) {
		return null;
	}
	const skipped = outcome.consideredReviews - outcome.reviewedCount;
	return (
		<div className="rounded-lg border border-dashed p-4">
			<p className="font-medium">On recent reviews</p>
			{skipped === 0 ? (
				<p className="mt-1 text-sm text-muted-foreground">
					These requirements were met every time, across the last {outcome.consideredReviews}{" "}
					{outcome.consideredReviews === 1 ? "review" : "reviews"} that reached this practice.
				</p>
			) : (
				<>
					<p className="mt-1 text-sm text-muted-foreground">
						Hephaestus skipped this practice in {skipped} of the last {outcome.consideredReviews}{" "}
						{outcome.consideredReviews === 1 ? "review" : "reviews"} that reached it, because the
						evidence did not meet what you require here.
					</p>
					<ul className="mt-2 space-y-1 text-sm text-muted-foreground">
						{outcome.skippedBecause.map((block) => (
							<li key={`${block.sourceKind}:${block.reasonCode}`}>
								{evidenceSourceLabel(block.sourceKind, sources)} —{" "}
								{readinessReasonLabel(block.reasonCode)} ({block.reviews}{" "}
								{block.reviews === 1 ? "review" : "reviews"})
							</li>
						))}
					</ul>
				</>
			)}
		</div>
	);
}

/** Reads a readiness reason back as the thing the author would have to change. */
function readinessReasonLabel(reasonCode: string): string {
	switch (reasonCode) {
		case "SOURCE_NOT_AVAILABLE":
			return "was not available";
		case "SOURCE_INCOMPLETE":
			return "was not fully captured";
		case "SOURCE_NOT_CURRENT":
			return "was not taken from the commit under review";
		case "SOURCE_EMPTY":
			return "was empty";
		default:
			return reasonCode.toLowerCase().replaceAll("_", " ");
	}
}
