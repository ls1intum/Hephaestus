import type { ReflectionPractice } from "@/api/types.gen";

type Standing = ReflectionPractice["standing"];

interface SegmentMeta {
	standing: Standing | "NOT_ASSESSED";
	colorClass: string;
	/** Reads inside a count ("2 need attention"); the legend needs a standalone noun instead. */
	singular: string;
	plural: string;
	legendLabel: string;
}

/** Segment order is fixed so the ring always reads worst-to-best, clockwise from twelve o'clock. */
const SEGMENTS: SegmentMeta[] = [
	{
		standing: "DEVELOPING",
		colorClass: "text-destructive",
		singular: "needs attention",
		plural: "need attention",
		legendLabel: "Needs attention",
	},
	{
		standing: "MIXED",
		colorClass: "text-warning",
		singular: "mixed",
		plural: "mixed",
		legendLabel: "Mixed",
	},
	{
		standing: "STRENGTH",
		colorClass: "text-success",
		singular: "going well",
		plural: "going well",
		legendLabel: "Going well",
	},
];

/**
 * Practices the area holds that carry no measurement yet.
 *
 * <p>The alpha is deliberate: at the /30 it started from, this grey sat at 1.5:1 against the card and
 * read as empty space rather than as a share of the area. /75 clears 3:1 in both themes while the
 * absent saturation still keeps it behind the three verdict colours.
 */
const NOT_ASSESSED: SegmentMeta = {
	standing: "NOT_ASSESSED",
	colorClass: "text-muted-foreground/75",
	singular: "not assessed yet",
	plural: "not assessed yet",
	legendLabel: "Not assessed yet",
};

/**
 * Every colour the ring can draw, in ring order, with the label that decodes it — the section legend
 * renders exactly this list so the two can never drift apart.
 */
export const STANDING_LEGEND: readonly SegmentMeta[] = [...SEGMENTS, NOT_ASSESSED];

/** r chosen so the circumference is exactly 100 — every dash length is then a percentage. */
const RADIUS = 15.9155;
/**
 * Gap between adjacent segments, in the same percentage units. Zero when only one segment shows.
 *
 * <p>Sized against the rendered pixel, not the maths: at the 64px ring 1% of the circumference is
 * ~1.5px, and antialiasing blends roughly a pixel into each neighbouring segment. A 2% gap therefore
 * left only ~1px reading as a gap, so whether it landed on a pixel boundary changed its apparent
 * width by a third and the gaps looked unequal from card to card.
 */
const GAP = 3;

/**
 * How a learner stands across ONE area's practices.
 *
 * <p>Derived over PRACTICES, not observations: a practice's standing is exact, whereas the observation
 * lists the card receives are capped for payload size and would make the proportions lie. Practices
 * with no feedback yet are not part of the area status and are simply absent from the input.
 */
export function deriveAreaStanding(practices: ReflectionPractice[], practiceCount?: number) {
	const assessed = practices.length;
	// Practices the area holds but that carry no measurement yet. The reflection only returns
	// practices with a standing, so they are absent from `practices` and would otherwise vanish from
	// the ring — making a half-measured area look as settled as a fully measured one.
	const notAssessed = Math.max((practiceCount ?? assessed) - assessed, 0);
	const total = assessed + notAssessed;
	const counts = [
		...SEGMENTS.map((segment) => ({
			...segment,
			count: practices.filter((practice) => practice.standing === segment.standing).length,
		})),
		{ ...NOT_ASSESSED, count: notAssessed },
	];
	const present = counts.filter((segment) => segment.count > 0);
	const [needsAttention, mixed] = counts.map((segment) => segment.count);

	return {
		total,
		present,
		verdict:
			needsAttention > 0 ? "Needs your attention" : mixed > 0 ? "Mostly on track" : "Going well",
		breakdown: present
			.map(
				(segment) => `${segment.count} ${segment.count === 1 ? segment.singular : segment.plural}`,
			)
			.join(", "),
	};
}

export interface PracticeAreaStandingRingProps {
	/** The practices of ONE area, each with the standing the server derived for it. */
	practices: ReflectionPractice[];
	/** How many practices the area holds in total; the surplus is drawn as the unmeasured share. */
	practiceCount?: number;
}

/**
 * Proportional standing ring for a practice area, modelled on a CI checks donut.
 *
 * <p>The centre deliberately stays empty: a fraction reads like a score even when it only counts one
 * category. The adjacent card copy states the complete split in words, so this SVG is decorative and
 * does not add a redundant hover target or keyboard stop.
 */
export function PracticeAreaStandingRing({
	practices,
	practiceCount,
}: PracticeAreaStandingRingProps) {
	const { total, present } = deriveAreaStanding(practices, practiceCount);
	if (total === 0) return null;

	const gap = present.length > 1 ? GAP : 0;
	let travelled = 0;

	return (
		<svg
			viewBox="-3 -3 42 42"
			className="size-16 shrink-0 -rotate-90"
			aria-hidden="true"
			data-testid="practice-area-standing-ring"
		>
			{/* The viewBox is padded by half the stroke width plus a hair: with r=15.9155 (circumference
			    100, so every dash is a percentage) and a 4.5 stroke, the outer edge reaches 36.17 —
			    an unpadded 0 0 36 36 box clips it. */}
			{/* No background track: the segments always account for every practice, so a track could only
			    ever show through the gaps — and there it actively hurt. The gap next to the unmeasured
			    share put `text-muted-foreground/30` beside `text-muted`, two light greys that read as one
			    body, so that gap vanished while the gaps between saturated segments stayed crisp. Letting
			    the card surface show through cuts every gap the same way, in light and dark alike. */}
			{present.map((segment) => {
				const share = (segment.count / total) * 100;
				// Inset each arc by half a gap at BOTH ends. Shortening the dash alone puts the whole
				// gap after the segment, which makes the visible gaps uneven as soon as one segment
				// is small enough to be clamped.
				const dash = Math.max(share - gap, 0.5);
				const start = travelled + (share - dash) / 2;
				travelled += share;
				return (
					<circle
						key={segment.standing}
						cx="18"
						cy="18"
						r={RADIUS}
						fill="none"
						strokeWidth="4.5"
						strokeLinecap="butt"
						strokeDasharray={`${dash} ${100 - dash}`}
						strokeDashoffset={-start}
						className={`stroke-current ${segment.colorClass}`}
					/>
				);
			})}
		</svg>
	);
}
