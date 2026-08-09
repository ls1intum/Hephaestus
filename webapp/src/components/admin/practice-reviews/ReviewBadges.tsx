import { CircleHelp, ClockAlert } from "lucide-react";
import type {
	ReviewFeedback,
	ReviewFeedbackCounts,
	ReviewFeedbackDisposition,
	ReviewFinding,
	ReviewFindingCounts,
} from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import {
	DELIVERY_STATE_LABELS,
	deliveryStateBadgeVariant,
	REVIEW_RESULT_LABELS,
	SEVERITY_LABELS,
	severityBadgeVariant,
} from "./review-format";

type NonCurrentClaimCurrentness = Exclude<ReviewFinding["claimCurrentness"], "CURRENT">;

const CLAIM_CURRENTNESS_CONFIG = {
	STALE: {
		badge: "Uses older review rules",
		badgeVariant: "warning",
		Icon: ClockAlert,
		title: "This result uses older review rules",
		description: "This result was produced using an older practice definition.",
	},
	UNVERIFIABLE: {
		badge: "Currentness unknown",
		badgeVariant: "outline",
		Icon: CircleHelp,
		title: "Currentness is unknown",
		description:
			"We can’t determine whether this result uses the current review rules because comparison provenance is unavailable.",
	},
} as const satisfies Record<
	NonCurrentClaimCurrentness,
	{
		badge: string;
		badgeVariant: "warning" | "outline";
		Icon: typeof ClockAlert;
		title: string;
		description: string;
	}
>;

export function FeedbackStateBadge({ state }: { state: ReviewFeedback["deliveryState"] }) {
	return <Badge variant={deliveryStateBadgeVariant(state)}>{DELIVERY_STATE_LABELS[state]}</Badge>;
}

type FindingResult = Pick<ReviewFinding, "presence" | "assessment" | "severity">;

export function FindingResultBadge({ finding }: { finding: FindingResult }) {
	if (finding.presence === "NOT_APPLICABLE") {
		return <Badge variant="outline">Not applicable</Badge>;
	}
	// Distinct from "Not applicable": the practice did apply and the evidence was read, it just did
	// not settle the question. Collapsing the two would claim nothing here was worth looking at.
	if (finding.presence === "INDETERMINATE") {
		return <Badge variant="outline">Could not be determined</Badge>;
	}
	if (!finding.assessment) {
		return <Badge variant="secondary">No result</Badge>;
	}
	return (
		<span className="flex flex-wrap items-center gap-1.5">
			<Badge variant={finding.assessment === "GOOD" ? "success" : "destructive"}>
				{REVIEW_RESULT_LABELS[finding.assessment]}
			</Badge>
			{finding.assessment === "BAD" && finding.severity && (
				<Badge variant={severityBadgeVariant(finding.severity)}>
					{SEVERITY_LABELS[finding.severity]}
				</Badge>
			)}
		</span>
	);
}

/**
 * What occasioned a measurement, shown only when it was not the ordinary case.
 *
 * <p>LIVE renders nothing: badging the overwhelming majority of rows says nothing and buries the two
 * that matter. A campaign's finding and one somebody asked for by hand are both self-selected rather
 * than a random draw from the work, so reading them as though they were live is the mistake this
 * exists to prevent.
 */
export function ObservationOriginBadge({ origin }: { origin: ReviewFinding["origin"] }) {
	if (origin === "LIVE") return null;
	return (
		<Badge variant="outline">
			{origin === "BACKFILL" ? "From a review of past work" : "Requested by hand"}
		</Badge>
	);
}

export function ClaimCurrentnessBadge({
	currentness,
}: {
	currentness: ReviewFinding["claimCurrentness"];
}) {
	if (currentness === "CURRENT") return null;
	const config = CLAIM_CURRENTNESS_CONFIG[currentness];
	return <Badge variant={config.badgeVariant}>{config.badge}</Badge>;
}

export function ClaimCurrentnessAlert({
	currentness,
}: {
	currentness: ReviewFinding["claimCurrentness"];
}) {
	if (currentness === "CURRENT") return null;
	const { Icon, title, description } = CLAIM_CURRENTNESS_CONFIG[currentness];
	return (
		<Alert variant="warning">
			<Icon />
			<AlertTitle>{title}</AlertTitle>
			<AlertDescription>{description}</AlertDescription>
		</Alert>
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
			? `${counts.problems} ${counts.problems === 1 ? "improvement" : "improvements"}`
			: undefined,
		counts.notApplicable > 0 ? `${counts.notApplicable} not applicable` : undefined,
		counts.indeterminate > 0 ? `${counts.indeterminate} undetermined` : undefined,
	].filter(Boolean);
	return (
		<p className="text-sm text-muted-foreground">
			{parts.length > 0 ? parts.join(" · ") : "No findings"}
		</p>
	);
}
