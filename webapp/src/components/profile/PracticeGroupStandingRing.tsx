import type { PracticeStanding } from "@/api/types.gen";
import { PRACTICE_GROUP_STANDING_BADGE } from "./practice-group-standing-presentation";

type Standing = PracticeStanding["standing"];

/**
 * Ring order is worst-first, so the segment a reader should act on starts at twelve o'clock. The
 * words come from the standing registry rather than from here — the legend this feeds sits directly
 * above the badge that names the same value.
 */
const SEGMENT_ORDER: readonly Standing[] = [
	"DEVELOPING",
	"MIXED",
	"STRENGTH",
	"NOT_OBSERVED",
	"NO_OPPORTUNITY",
];

const SEGMENT_COLORS: Record<Standing, string> = {
	DEVELOPING: "text-destructive",
	MIXED: "text-warning",
	STRENGTH: "text-success",
	NOT_OBSERVED: "text-muted-foreground/75",
	NO_OPPORTUNITY: "text-muted-foreground/45",
};

const SEGMENTS: ReadonlyArray<{
	standing: Standing;
	colorClass: string;
	label: string;
}> = SEGMENT_ORDER.map((standing) => ({
	standing,
	colorClass: SEGMENT_COLORS[standing],
	label: PRACTICE_GROUP_STANDING_BADGE[standing].shortLabel,
}));

export const STANDING_LEGEND = SEGMENTS;

export function summarizePracticeStandings(practices: PracticeStanding[]) {
	return SEGMENTS.map((segment) => ({
		...segment,
		count: practices.filter((practice) => practice.standing === segment.standing).length,
	})).filter((segment) => segment.count > 0);
}

export interface PracticeGroupStandingRingProps {
	practices: PracticeStanding[];
}

export function PracticeGroupStandingRing({ practices }: PracticeGroupStandingRingProps) {
	const segments = summarizePracticeStandings(practices);
	if (segments.length === 0) return null;

	const total = practices.length;
	const gap = segments.length > 1 ? 3 : 0;
	const starts = segments.map((_, index) =>
		segments.slice(0, index).reduce((sum, segment) => sum + (segment.count / total) * 100, 0),
	);

	return (
		<svg viewBox="-3 -3 42 42" className="size-16 shrink-0 -rotate-90" aria-hidden="true">
			{segments.map((segment, index) => {
				const share = (segment.count / total) * 100;
				const dash = Math.max(share - gap, 0.5);
				const start = (starts[index] ?? 0) + (share - dash) / 2;
				return (
					<circle
						key={segment.standing}
						cx="18"
						cy="18"
						r="15.9155"
						fill="none"
						strokeWidth="4.5"
						strokeDasharray={`${dash} ${100 - dash}`}
						strokeDashoffset={-start}
						className={`stroke-current ${segment.colorClass}`}
					/>
				);
			})}
		</svg>
	);
}
