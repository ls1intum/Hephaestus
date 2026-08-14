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
import type { StatusDef } from "@/components/practice-vocabulary/status-def";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

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
	const severity = observationSeverity(observation);
	return (
		<span className="flex flex-wrap items-center gap-1.5">
			<StatusBadge def={observationResult(observation)} />
			{severity && <StatusBadge def={severity} />}
		</span>
	);
}

/**
 * The severity entry a row shows, or nothing.
 *
 * <p>Severity is only ever read alongside a shortfall — a "Minor" beside a strength would be a cost
 * for something that cost nothing. The rule lives here because the list row and the detail header
 * both apply it, and the list row needs the entry on its own: it puts the result and the severity in
 * two fixed slots so the severity lands at one x down the list, which it cannot do while they are a
 * single badge pair of varying width.
 */
export function observationSeverity(
	observation: ObservationResultFacts & Pick<ReviewObservation, "severity">,
): StatusDef | undefined {
	return observation.assessment === "BAD" && observation.severity
		? SEVERITY_DEFS[observation.severity]
		: undefined;
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

/** One counted thing: the registry's word for it, and how many of it there were. */
export interface ReviewCountSlot {
	key: string;
	/** Lower-cased registry words. A tally saying "not delivered" beside a badge saying "Withheld" is
	 * how one enum ends up with two names. */
	label: string;
	count: number;
}

/**
 * The five delivery outcomes, in the order a reader meets them, whether or not any occurred.
 *
 * A tally of delivery outcomes stays words rather than becoming a row of badges: badging five counts
 * on every row would colour the norm, which is what makes the one exceptional row invisible.
 */
export function feedbackCountSlots(counts: FeedbackCounts): ReviewCountSlot[] {
	return (
		[
			["DELIVERED", counts.delivered],
			["SUPERSEDED", counts.superseded],
			["PREPARED", counts.prepared],
			["SUPPRESSED", counts.suppressed],
			["FAILED", counts.failed],
		] as const
	).map(([state, count]) => ({
		key: state,
		label: DELIVERY_STATE_DEFS[state].label.toLowerCase(),
		count,
	}));
}

/**
 * What a review concluded, as four counted things.
 *
 * <p>Every noun here is the registry's own word for the value it counts. `inconclusive` used to read
 * "undetermined", which appears in no registry and on no badge — an operator who filtered by
 * "Could not be determined" got back rows summarised with a word they had never seen. The two
 * assessment counts are nominalised (`Strength` → strengths, `Needs improvement` → improvements)
 * because a count needs a noun, but they keep the stem so the filter and the tally are recognisably
 * about one thing.
 */
export function observationCountSlots(counts: ReviewObservationCounts): ReviewCountSlot[] {
	return [
		{
			key: "strengths",
			label: counts.strengths === 1 ? "strength" : "strengths",
			count: counts.strengths,
		},
		{
			key: "problems",
			label: counts.problems === 1 ? "improvement" : "improvements",
			count: counts.problems,
		},
		{
			key: "notApplicable",
			label: PRESENCE_DEFS.NOT_APPLICABLE.label.toLowerCase(),
			count: counts.notApplicable,
		},
		{
			key: "inconclusive",
			label: PRESENCE_DEFS.INCONCLUSIVE.label.toLowerCase(),
			count: counts.inconclusive,
		},
	];
}

/**
 * A tally as a fixed grid: every slot drawn, every number at the same x on every row.
 *
 * <p>What a review produced is nine numbers, and nine numbers compared down a list of twenty is the
 * case a table exists for — *"two adjacent data points are easy to compare because … users don't
 * need to either move their eyes much or store information in their working memory"* (NN/g, "Data
 * Tables"). This list used to join them into a sentence with the zeroes dropped, so "3 improvements"
 * landed somewhere different on every row, an absent count was indistinguishable from a count that
 * is not rendered at all, and the sentence reflowed under the reader while the five-second poll
 * refreshed an active review. Drawing every slot, zero included, fixes all three at once: the width
 * of the strip no longer depends on the numbers in it.
 *
 * <p>The grid is two columns on a phone and five above `sm` — a fixed template either way, so the
 * columns line up down the list at every width, and the four-slot observation tally sits under the
 * five-slot feedback one in the same columns. Each number keeps its word beside it, so a screen
 * reader gets "0 improvements" rather than a bare nought.
 */
export function ReviewCountStrip({ slots, label }: { slots: ReviewCountSlot[]; label: string }) {
	return (
		<ul aria-label={label} className="grid grid-cols-2 gap-x-4 gap-y-0.5 sm:grid-cols-5">
			{slots.map((slot) => (
				<li key={slot.key} className="flex min-w-0 items-baseline gap-1">
					<span
						className={cn(
							"tabular-nums",
							slot.count > 0 ? "font-medium text-foreground" : "text-muted-foreground/60",
						)}
					>
						{slot.count}
					</span>
					{/* A real space, so the pair reads "0 improvements" to a screen reader and in a test.
					    Flex drops whitespace-only children, so the visible gap is still the one `gap-1`
					    sets and this adds nothing to the layout. */}{" "}
					<span className="min-w-0 break-words">{slot.label}</span>
				</li>
			))}
		</ul>
	);
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
	const parts = feedbackCountSlots(counts)
		.filter((slot) => slot.count > 0)
		.map((slot) => `${slot.count} ${slot.label}`);
	if (parts.length === 0) return <span>No feedback composed</span>;
	return (
		<span>
			{prefix && `${prefix} `}
			{parts.join(" · ")}
		</span>
	);
}
