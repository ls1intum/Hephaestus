import { CircleHelp, ClockAlert } from "lucide-react";
import type {
	ReviewFeedbackCounts,
	ReviewFeedbackDisposition,
	ReviewObservation,
	ReviewObservationCounts,
} from "@/api/types.gen";
import { DELIVERY_STATE_DEFS } from "@/components/practice-vocabulary/delivery-outcome-defs";
import {
	type ObservationResultFacts,
	observationResult,
} from "@/components/practice-vocabulary/observation-result";
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
		title: "This was judged against an older version of the practice",
		description:
			"The practice has been edited since. What it says may no longer be what the practice asks for.",
	},
	UNVERIFIABLE: {
		badge: "Rules version unknown",
		badgeVariant: "outline",
		Icon: CircleHelp,
		title: "We can't tell which version of the practice this was judged against",
		description:
			"The record of which practice text the review read was not kept, so there is no way to say whether the practice has changed since. Treat it as you would any observation you have not checked.",
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

/**
 * What one observation concluded, plus how much it costs when it is a shortfall.
 *
 * The rule for collapsing presence and assessment into one badge lives in `observationResult`, so
 * the badge here and the leading icon on the row cannot disagree about what a row is.
 */
export function ObservationResultBadge({
	observation,
}: {
	observation: ObservationResultFacts & Pick<ReviewObservation, "severity">;
}) {
	return (
		<span className="flex flex-wrap items-center gap-1.5">
			<StatusBadge def={observationResult(observation)} />
			{observation.assessment === "BAD" && observation.severity && (
				<StatusBadge def={SEVERITY_DEFS[observation.severity]} />
			)}
		</span>
	);
}

/**
 * What occasioned a measurement, shown only when it was not the ordinary case.
 *
 * <p>LIVE renders nothing: badging the overwhelming majority of rows says nothing and buries the two
 * that matter. A campaign's observation and one somebody asked for by hand are both self-selected
 * rather than a random draw from the work, so reading them as though they were live is the mistake
 * this exists to prevent.
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

type FeedbackCounts = ReviewFeedbackCounts | ReviewFeedbackDisposition;

/**
 * A tally of delivery outcomes reads as a sentence rather than a row of badges: badging five counts
 * on every row would colour the norm, which is what makes the one exceptional row invisible.
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

/**
 * What became of the feedback an observation or a review produced.
 *
 * <p>One component where there were two — `FeedbackCountsSummary` and `FindingFeedbackSummary` —
 * which differed only in that one prefixed the sentence with "Feedback: " and the other did not, so
 * the same tally read two ways on two screens. The prefix is now the caller's business, because on a
 * row under a heading that already says Feedback it is noise, and in a meta line of mixed facts it
 * is the only thing that says what the numbers count.
 */
export function FeedbackCountsSummary({
	counts,
	prefix,
}: {
	counts: FeedbackCounts;
	prefix?: string;
}) {
	const summary = feedbackCountsLabel(counts);
	if (!summary) return <span>No feedback composed</span>;
	return (
		<span>
			{prefix && `${prefix} `}
			{summary}
		</span>
	);
}

/**
 * What a review found, as a sentence.
 *
 * <p>Every noun here is the registry's own word for the value it counts. `inconclusive` used to read
 * "undetermined", which appears in no registry and on no badge — an operator who filtered by
 * "Could not be determined" got back rows summarised with a word they had never seen. The two
 * assessment counts are nominalised (`Strength` → strengths, `Needs improvement` → improvements)
 * because a count needs a noun, but they keep the stem so the filter and the tally are recognisably
 * about one thing.
 */
export function ObservationCountsSummary({ counts }: { counts: ReviewObservationCounts }) {
	const parts = [
		counts.strengths > 0 &&
			`${counts.strengths} ${counts.strengths === 1 ? "strength" : "strengths"}`,
		counts.problems > 0 &&
			`${counts.problems} ${counts.problems === 1 ? "improvement" : "improvements"}`,
		counts.notApplicable > 0 &&
			`${counts.notApplicable} ${PRESENCE_DEFS.NOT_APPLICABLE.label.toLowerCase()}`,
		counts.inconclusive > 0 &&
			`${counts.inconclusive} ${PRESENCE_DEFS.INCONCLUSIVE.label.toLowerCase()}`,
	].filter(Boolean);
	return <span>{parts.length > 0 ? parts.join(" · ") : "No observations"}</span>;
}
