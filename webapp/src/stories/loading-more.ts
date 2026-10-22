import { expect, within } from "storybook/test";

/**
 * Checks that a list loading its next page continues its table: the table is busy, a separator and
 * placeholder rows with a cell per column follow the loaded rows, and the list's end only says it
 * is loading, with no placeholder of its own.
 */
export async function expectPlaceholderRows(table: HTMLElement, rows = 3) {
	await expect(table).toHaveAttribute("aria-busy", "true");
	await expect(table.querySelectorAll("tbody tr[data-separator]")).toHaveLength(1);
	const placeholders = table.querySelectorAll("tbody [data-loading-row]");
	await expect(placeholders).toHaveLength(rows);
	// A cell per column, so the rows that replace the placeholders do not move the table.
	const columns = table.querySelectorAll("thead th").length;
	for (const row of placeholders) {
		await expect(row.querySelectorAll("td")).toHaveLength(columns);
	}
	const end = table.ownerDocument.querySelector<HTMLElement>("[data-list-end]");
	await expect(end).not.toBeNull();
	if (end === null) {
		return;
	}
	await expect(within(end).getByRole("button", { name: "Loading…" })).toHaveAttribute(
		"aria-disabled",
		"true",
	);
	await expect(end.querySelector("[data-slot=skeleton]")).toBeNull();
}
