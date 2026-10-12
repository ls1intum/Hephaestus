import type {
	PracticeReviewCounts,
	PracticeReviewOverview,
	ReviewFeedbackCounts,
} from "@/api/types.gen";

import { familyCount, observationSlots, slotsTotal } from "./review-outcomes";

/**
 * The Practices table's totals row: the overview's own figures, the ones the Reviews, Observations
 * and Feedback tiles above show, so the table and the tiles cannot disagree. The observation columns
 * add up across the practices by construction, since every observation is of one practice. Feedback
 * delivered does not: a piece of feedback about two practices counts in the row of each, and a piece
 * bound to no observation counts in none, so the rows can add up to more or less than the pieces.
 */
export interface PracticeTotals {
	observations: number;
	notMet: number;
	notApplicable: number;
	undetermined: number;
	markedIncorrect: number;
	/** Pieces of feedback delivered, each once, as the Feedback tile counts them. */
	feedbackDelivered: number;
	/** What Feedback delivered is out of: every piece composed, each once (`feedbackComposed`). */
	feedbackComposed: number;
	/** What the Feedback delivered column adds up to over every practice, hidden or shown. */
	feedbackDeliveredInRows: number;
	/** The practices of the range the table's filters hide, and their observations. */
	hidden: { practices: number; observations: number };
}

/**
 * The feedback composed, what the Feedback delivered column is "of": every piece in any state, sent,
 * still waiting or withheld, but not a piece replaced by a newer one, since the newer one stands for
 * it and would count the same feedback twice. So "6 of 9" reads as the share of the feedback written
 * that reached the developer, and a practice whose feedback is mostly withheld or waiting shows it.
 */
export function feedbackComposed(counts: ReviewFeedbackCounts): number {
	return (
		counts.awaitingApproval +
		counts.prepared +
		counts.delivered +
		counts.partiallyDelivered +
		counts.unconfirmed +
		counts.suppressed +
		counts.discarded +
		counts.failed +
		counts.partiallyFailed
	);
}

const observationsOf = (counts: PracticeReviewCounts) =>
	slotsTotal(observationSlots(counts.observations));

/** The totals of the range, with the rows on screen to tell how much the filters hide. */
export function practiceTotals(
	overview: PracticeReviewOverview,
	shown: readonly PracticeReviewCounts[],
): PracticeTotals {
	const shownSlugs = new Set(shown.map((counts) => counts.practiceSlug));
	const hiddenRows = overview.practices.filter((counts) => !shownSlugs.has(counts.practiceSlug));
	return {
		observations: slotsTotal(observationSlots(overview.observations)),
		notMet: overview.observations.notMet,
		notApplicable: overview.observations.notApplicable,
		undetermined: overview.observations.undetermined,
		markedIncorrect: overview.observationsInvalidated,
		feedbackDelivered: familyCount(overview.feedback, "DELIVERED"),
		feedbackComposed: feedbackComposed(overview.feedback),
		feedbackDeliveredInRows: overview.practices.reduce(
			(sum, counts) => sum + familyCount(counts.feedback, "DELIVERED"),
			0,
		),
		hidden: {
			practices: hiddenRows.length,
			observations: hiddenRows.reduce((sum, counts) => sum + observationsOf(counts), 0),
		},
	};
}

const plural = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`;

/**
 * Where the rows and the totals row differ, and why, one sentence each: nothing when the rows add up
 * to the totals.
 */
export function practiceTotalsNotes(totals: PracticeTotals): string[] {
	const notes: string[] = [];
	if (totals.hidden.practices > 0) {
		notes.push(
			`The totals include ${plural(totals.hidden.practices, "practice", "practices")} that the filters hide, with ${plural(totals.hidden.observations, "observation", "observations")}.`,
		);
	}
	if (totals.feedbackDeliveredInRows !== totals.feedbackDelivered) {
		notes.push(
			`Feedback delivered adds up to ${totals.feedbackDeliveredInRows} across the practices, but ${plural(totals.feedbackDelivered, "piece was", "pieces were")} delivered. A piece about several practices counts in the row of each, and a piece about no practice counts in none.`,
		);
	}
	return notes;
}
