import { expect, within } from "storybook/test";

/** The left edge of every cell in a row. */
const cellLefts = (row: Element) =>
	[...row.querySelectorAll("td")].map((cell) => cell.getBoundingClientRect().left);

/**
 * Every row of a Practice profile fold starts its cells where `firstRow` of the table above starts
 * them, within 1 px: the fold belongs to that table, not a table of its own beside it. Stacked on a
 * phone, a row is a card rather than a line of columns, so there it lines up by its edges.
 */
export async function expectFoldOnTableColumns(firstRow: Element | null, fold: HTMLElement) {
	await expect(firstRow).not.toBeNull();
	if (firstRow === null) {
		return;
	}
	const expected = cellLefts(firstRow);
	const expectedRow = firstRow.getBoundingClientRect();
	const stacked = getComputedStyle(fold).display === "block";
	for (const row of within(fold).getAllByRole("row")) {
		if (stacked) {
			const box = row.getBoundingClientRect();
			await expect(Math.abs(box.left - expectedRow.left)).toBeLessThanOrEqual(1);
			await expect(Math.abs(box.width - expectedRow.width)).toBeLessThanOrEqual(1);
			continue;
		}
		const actual = cellLefts(row);
		await expect(actual).toHaveLength(expected.length);
		for (const [index, left] of actual.entries()) {
			await expect(Math.abs(left - (expected[index] ?? Number.NaN))).toBeLessThanOrEqual(1);
		}
	}
}
