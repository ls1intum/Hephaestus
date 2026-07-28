import type {
	ReviewFeedback,
	ReviewFeedbackCounts,
	ReviewFeedbackDisposition,
	ReviewFinding,
	ReviewFindingCounts,
} from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import {
	ASSESSMENT_LABELS,
	DELIVERY_STATE_LABELS,
	deliveryStateBadgeVariant,
	SEVERITY_LABELS,
	severityBadgeVariant,
} from "./review-format";

export function FeedbackStateBadge({ state }: { state: ReviewFeedback["deliveryState"] }) {
	return <Badge variant={deliveryStateBadgeVariant(state)}>{DELIVERY_STATE_LABELS[state]}</Badge>;
}

type FindingAssessment = Pick<ReviewFinding, "presence" | "assessment" | "severity">;

export function FindingAssessmentBadge({ finding }: { finding: FindingAssessment }) {
	if (finding.presence === "NOT_APPLICABLE") {
		return <Badge variant="outline">Not applicable</Badge>;
	}
	if (!finding.assessment) {
		return <Badge variant="secondary">Unassessed</Badge>;
	}
	return (
		<span className="flex flex-wrap items-center gap-1.5">
			<Badge variant={finding.assessment === "GOOD" ? "success" : "destructive"}>
				{ASSESSMENT_LABELS[finding.assessment]}
			</Badge>
			{finding.assessment === "BAD" && finding.severity && (
				<Badge variant={severityBadgeVariant(finding.severity)}>
					{SEVERITY_LABELS[finding.severity]}
				</Badge>
			)}
		</span>
	);
}

export function FindingFeedbackSummary({
	disposition,
}: {
	disposition: ReviewFinding["feedbackDisposition"];
}) {
	const summary = feedbackCountsLabel(disposition);
	return (
		<p className="text-sm text-muted-foreground">
			{summary ? `Feedback: ${summary.toLowerCase()}` : "No feedback composed"}
		</p>
	);
}

type FeedbackCounts = ReviewFeedbackCounts | ReviewFeedbackDisposition;

function feedbackCountsLabel(counts: FeedbackCounts): string | undefined {
	const parts = [
		counts.delivered > 0 ? `${counts.delivered} delivered` : undefined,
		counts.superseded > 0 ? `${counts.superseded} replaced` : undefined,
		counts.prepared > 0 ? `${counts.prepared} awaiting conversation` : undefined,
		counts.suppressed > 0 ? `${counts.suppressed} not delivered` : undefined,
		counts.failed > 0 ? `${counts.failed} failed` : undefined,
	].filter(Boolean);
	return parts.length > 0 ? parts.join(" · ") : undefined;
}

export function FeedbackCountsSummary({ counts }: { counts: FeedbackCounts }) {
	return (
		<p className="text-sm text-muted-foreground">
			{feedbackCountsLabel(counts) ?? "No feedback composed"}
		</p>
	);
}

export function FindingCountsSummary({ counts }: { counts: ReviewFindingCounts }) {
	const parts = [
		counts.strengths > 0
			? `${counts.strengths} ${counts.strengths === 1 ? "strength" : "strengths"}`
			: undefined,
		counts.problems > 0
			? `${counts.problems} ${counts.problems === 1 ? "area" : "areas"} to improve`
			: undefined,
		counts.notApplicable > 0 ? `${counts.notApplicable} not applicable` : undefined,
	].filter(Boolean);
	return (
		<p className="text-sm text-muted-foreground">
			{parts.length > 0 ? parts.join(" · ") : "No findings"}
		</p>
	);
}
