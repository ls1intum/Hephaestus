import type { ReviewRef } from "@/api/types.gen";
import { formatWeekdayDay } from "@/lib/dates";

export interface DayGroup<TRow> {
	label: string;
	rows: TRow[];
}

/** Name days explicitly; a page left open across midnight must not retain a stale Today label. */
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

/** Positions require every review of an item; partial pages cannot establish them. */
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

export function reviewOrdinalLabel(position: number, total?: number): string | undefined {
	if (position <= 1) {
		return undefined;
	}
	return total !== undefined && total >= position
		? `Review ${position} of ${total}`
		: `Review ${position}`;
}
