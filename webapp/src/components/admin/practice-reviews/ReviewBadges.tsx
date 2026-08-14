import { CircleHelp, ClockAlert } from "lucide-react";
import type {
	ReviewFeedbackCounts,
	ReviewFeedbackDisposition,
	ReviewObservation,
	ReviewObservationCounts,
} from "@/api/types.gen";
import { ASSESSMENT_DEFS } from "@/components/practice-vocabulary/assessment-defs";
import { DELIVERY_STATE_DEFS } from "@/components/practice-vocabulary/delivery-outcome-defs";
import { PRESENCE_DEFS } from "@/components/practice-vocabulary/presence-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";

type NonCurrentClaimCurrentness = Exclude<ReviewObservation["claimCurrentness"], "CURRENT">;

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

type FindingResult = Pick<ReviewObservation, "presence" | "assessment" | "severity">;

/**
 * What one observation concluded, as the smallest true set of badges.
 *
 * The two presence values that end the question answer it alone; only a practice that was in play
 * gets an assessment, and only a shortfall gets a severity beside it. Every badge is a registry
 * entry, so this decides *which* badges appear and never what any of them looks like.
 */
export function FindingResultBadge({ finding }: { finding: FindingResult }) {
	if (finding.presence === "NOT_APPLICABLE") {
		return <StatusBadge def={PRESENCE_DEFS.NOT_APPLICABLE} />;
	}
	if (finding.presence === "INCONCLUSIVE") {
		return <StatusBadge def={PRESENCE_DEFS.INCONCLUSIVE} />;
	}
	if (!finding.assessment) {
		return <Badge variant="secondary">No result</Badge>;
	}
	return (
		<span className="flex flex-wrap items-center gap-1.5">
			<StatusBadge def={ASSESSMENT_DEFS[finding.assessment]} />
			{finding.assessment === "BAD" && finding.severity && (
				<StatusBadge def={SEVERITY_DEFS[finding.severity]} />
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
export function ObservationOriginBadge({ origin }: { origin: ReviewObservation["origin"] }) {
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
	currentness: ReviewObservation["claimCurrentness"];
}) {
	if (currentness === "CURRENT") return null;
	const config = CLAIM_CURRENTNESS_CONFIG[currentness];
	return <Badge variant={config.badgeVariant}>{config.badge}</Badge>;
}

export function ClaimCurrentnessAlert({
	currentness,
}: {
	currentness: ReviewObservation["claimCurrentness"];
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
	disposition: ReviewObservation["feedbackDisposition"];
}) {
	const summary = feedbackCountsLabel(disposition);
	return (
		<p className="text-sm text-muted-foreground">
			{summary ? `Feedback: ${summary.toLowerCase()}` : "No feedback composed"}
		</p>
	);
}

type FeedbackCounts = ReviewFeedbackCounts | ReviewFeedbackDisposition;

/**
 * A tally of delivery outcomes reads as a sentence rather than a row of badges: badging five
 * counts on every row would colour the norm, which is what makes the one exceptional row invisible.
 *
 * The words still come from the registry — a count saying "not delivered" while the badge one column
 * over says "Withheld" is how the same enum ends up with two names.
 */
function feedbackCountsLabel(counts: FeedbackCounts): string | undefined {
	const parts = (
		[
			["DELIVERED", counts.delivered],
			["SUPERSEDED", counts.superseded],
			["PREPARED", counts.prepared],
			["SUPPRESSED", counts.suppressed],
			["FAILED", counts.failed],
		] as const
	)
		.filter(([, count]) => count > 0)
		.map(([state, count]) => `${count} ${DELIVERY_STATE_DEFS[state].label.toLowerCase()}`);
	return parts.length > 0 ? parts.join(" · ") : undefined;
}

export function FeedbackCountsSummary({ counts }: { counts: FeedbackCounts }) {
	return (
		<p className="text-sm text-muted-foreground">
			{feedbackCountsLabel(counts) ?? "No feedback composed"}
		</p>
	);
}

export function FindingCountsSummary({ counts }: { counts: ReviewObservationCounts }) {
	const parts = [
		counts.strengths > 0
			? `${counts.strengths} ${counts.strengths === 1 ? "strength" : "strengths"}`
			: undefined,
		counts.problems > 0
			? `${counts.problems} ${counts.problems === 1 ? "improvement" : "improvements"}`
			: undefined,
		counts.notApplicable > 0 ? `${counts.notApplicable} not applicable` : undefined,
		counts.inconclusive > 0 ? `${counts.inconclusive} undetermined` : undefined,
	].filter(Boolean);
	return (
		<p className="text-sm text-muted-foreground">
			{parts.length > 0 ? parts.join(" · ") : "No observations"}
		</p>
	);
}
