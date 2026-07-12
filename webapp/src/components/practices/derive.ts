import type { PracticeReportCard, PracticeReportItem } from "@/components/practices/practice-types";

/** At most this many practices are picked as the cycle's focus. */
export const FOCUS_LIMIT = 3;

/**
 * THE focus rule, in one place: which practices deserve a developer's attention this cycle.
 *
 * A practice qualifies when its status is DEVELOPING, or when it is MIXED and declining since
 * the last cycle. Qualifying practices are ordered worst status first (DEVELOPING before
 * MIXED), a declining trend breaking ties, and the pick is capped at {@link FOCUS_LIMIT} so
 * the default view always fits on one screen. Everything is derived client side from the
 * report cards the server already serves, and the rule never compares people to each other.
 */
export function pickFocus(cards: readonly PracticeReportCard[]): PracticeReportCard[] {
	return cards
		.filter(
			(card) =>
				card.status === "DEVELOPING" || (card.status === "MIXED" && card.trend === "WORSENING"),
		)
		.map((card, index) => ({ card, index }))
		.sort((a, b) => focusScore(b.card) - focusScore(a.card) || a.index - b.index)
		.slice(0, FOCUS_LIMIT)
		.map(({ card }) => card);
}

function focusScore(card: PracticeReportCard): number {
	return (card.status === "DEVELOPING" ? 2 : 0) + (card.trend === "WORSENING" ? 1 : 0);
}

/** Every evidence item on a card, problems first (they are what earned the attention). */
export function cardItems(card: PracticeReportCard): PracticeReportItem[] {
	return [...card.toWorkOn, ...card.strengths];
}

/**
 * The single evidence item that best explains a card: the highest-impact problem when there is
 * one (the server sorts toWorkOn highest-impact first), otherwise the leading strength.
 */
export function leadingEvidence(card: PracticeReportCard): PracticeReportItem | undefined {
	return card.toWorkOn[0] ?? card.strengths[0];
}

/**
 * Buckets a card's evidence timestamps into observations per week, oldest first, for the
 * sparkline. Derived from the visible evidence only, which is capped server side, so the line
 * answers "is there recent signal here", never "how much did this person do".
 */
export function weeklyEvidenceBuckets(
	card: PracticeReportCard,
	weeks = 6,
	now = new Date(),
): number[] {
	const weekMs = 7 * 24 * 60 * 60 * 1000;
	const buckets = new Array<number>(weeks).fill(0);
	for (const item of cardItems(card)) {
		// The generated type says Date, but the runtime value is the raw ISO string (the client
		// does not wire response transformers), so coerce before doing date math.
		const age = now.getTime() - new Date(item.observedAt).getTime();
		if (age < 0) continue;
		const bucketFromEnd = Math.floor(age / weekMs);
		if (bucketFromEnd < weeks) {
			buckets[weeks - 1 - bucketFromEnd] += 1;
		}
	}
	return buckets;
}
