import type { CleanWork, ReviewedWorkOutcome } from "./PracticeFeedbackCard";

/**
 * The card's timeline is a window on the supplied reviews of this developer's work, at most this
 * many, the evidence and the clean work alike: a glance at the pattern, not a log of every place it
 * was seen. The older pieces fold into one "N earlier" place at the start of the line.
 */
export const TIMELINE_WORK_LIMIT = 5;

/** One place on the timeline, in the order it is drawn: a piece of work, what the review made of it, and when. */
export interface TimelinePiece extends ReviewedWorkOutcome {
	key: string;
}

/** "1 of 3 clean": the card's meter in words, which the group table's pattern label repeats. */
export const cleanLabel = ({
	cleanCount,
	cleanNeeded,
}: {
	cleanCount: number;
	cleanNeeded: number;
}) => `${cleanCount} of ${cleanNeeded} clean`;

const byDate = (a: { date: Date }, b: { date: Date }) => a.date.getTime() - b.date.getTime();

/**
 * The supplied evidence and clean work, ordered by their recorded review timestamps. The window
 * keeps the newest supplied records, at most `limit`; evidence need not predate the card.
 */
export function timelinePieces(
	reviewedWork: ReviewedWorkOutcome[],
	cleanWork: CleanWork[],
	limit: number = TIMELINE_WORK_LIMIT,
): TimelinePiece[] {
	return [
		// The same thread can be reviewed twice on different days.
		...reviewedWork.map((work) => ({
			...work,
			key: `${work.ref.kind} ${work.ref.id} ${work.date.getTime()}`,
		})),
		...cleanWork.map((clean) => ({
			...clean,
			key: `${clean.ref.kind} ${clean.ref.id} clean`,
			outcome: "MET" as const,
		})),
	]
		.sort(byDate)
		.slice(-limit);
}
