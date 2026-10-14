import type { ReviewRef } from "@/api/types.gen";
import { formatWeekdayDay } from "@/lib/dates";

/** One heading of a list and the rows under it. */
export interface DayGroup<TRow> {
	label: string;
	rows: TRow[];
}

/**
 * The rows under a heading per day, in the order they arrived, each by the moment `dateOf` reads.
 * The day is named outright rather than as "Today": a page left open crosses midnight.
 */
export function groupByDay<TRow>(
	rows: readonly TRow[],
	dateOf: (row: TRow) => Date,
	now: Date,
): DayGroup<TRow>[] {
	const days: DayGroup<TRow>[] = [];
	for (const row of rows) {
		const label = formatWeekdayDay(dateOf(row), now);
		const last = days.at(-1);
		if (last?.label === label) {
			last.rows.push(row);
		} else {
			days.push({ label, rows: [row] });
		}
	}
	return days;
}

/**
 * Each review's position among the reviews of its own item, counting from the oldest, by review id.
 * The reviews arrive newest first, and must be every review there is: a page or a filter would count
 * short.
 */
export function reviewPositionsOnItem(reviews: readonly ReviewRef[]): Map<string, number> {
	const remaining = new Map<string, number>();
	for (const review of reviews) {
		remaining.set(review.item.id, (remaining.get(review.item.id) ?? 0) + 1);
	}
	const positions = new Map<string, number>();
	for (const review of reviews) {
		const position = remaining.get(review.item.id) ?? 0;
		positions.set(review.id, position);
		remaining.set(review.item.id, position - 1);
	}
	return positions;
}

/**
 * "Review 2 of 3", or "Review 2" while the count of the item's reviews is unknown. The first review
 * of a piece of work is not tagged: every work has one, so the tag would single out none.
 */
export function reviewOrdinalLabel(position: number, total?: number): string | undefined {
	if (position <= 1) {
		return undefined;
	}
	return total !== undefined && total >= position
		? `Review ${position} of ${total}`
		: `Review ${position}`;
}
