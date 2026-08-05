import type { PracticeEvidenceOutcome, PracticeEvidenceSourceOption } from "@/api/types.gen";
import {
	evidenceSourceLabel,
	readinessReasonLabel,
} from "@/components/admin/practice-catalog/evidence-presentation";

export interface PracticeEvidenceOutcomeSummaryProps {
	outcome: PracticeEvidenceOutcome;
	sources: readonly PracticeEvidenceSourceOption[];
}

/** How a practice's evidence requirements fared on the workspace's recent reviews. */
export function PracticeEvidenceOutcomeSummary({
	outcome,
	sources,
}: PracticeEvidenceOutcomeSummaryProps) {
	if (outcome.consideredReviews === 0) {
		return null;
	}
	const skipped = outcome.consideredReviews - outcome.reviewedCount;
	const reviews = (count: number) => `${count} ${count === 1 ? "review" : "reviews"}`;
	return (
		<div className="rounded-lg border p-4">
			<p className="font-medium">On recent reviews</p>
			{skipped === 0 ? (
				<p className="mt-1 text-sm text-muted-foreground">
					These requirements were met every time, across the last{" "}
					{reviews(outcome.consideredReviews)} that reached this practice.
				</p>
			) : (
				<>
					<p className="mt-1 text-sm text-muted-foreground">
						Hephaestus skipped this practice in {skipped} of the last{" "}
						{reviews(outcome.consideredReviews)} that reached it.
					</p>
					{outcome.skippedBecause.length > 0 && (
						<ul className="mt-2 space-y-1 text-sm text-muted-foreground">
							{outcome.skippedBecause.map((block) => (
								<li key={`${block.sourceKind ?? "practice"}:${block.reasonCode}`}>
									{block.sourceKind
										? `${evidenceSourceLabel(block.sourceKind, sources)} — ${readinessReasonLabel(block.reasonCode)}`
										: readinessReasonLabel(block.reasonCode)}{" "}
									({reviews(block.reviews)})
								</li>
							))}
						</ul>
					)}
				</>
			)}
		</div>
	);
}
