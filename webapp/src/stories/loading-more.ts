import { expect, within } from "storybook/test";

import { LOADING_MORE_ROWS } from "@/components/common/InfiniteListEnd";

/**
 * Checks that a list loading its next page continues its table: the table is busy, a separator and
 * placeholder rows with a cell per column follow the loaded rows, and the list's end only says it
 * is loading, with no placeholder of its own.
 */
export async function expectPlaceholderRows(table: HTMLElement, rows = LOADING_MORE_ROWS) {
	await expectTablePlaceholders(table, rows);
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

/**
 * The table half of `expectPlaceholderRows`, for a table whose end is a press of its own: busy, one
 * separator, and placeholder rows with a cell per column after the loaded rows.
 */
export async function expectTablePlaceholders(table: HTMLElement, rows = LOADING_MORE_ROWS) {
	await expect(table).toHaveAttribute("aria-busy", "true");
	await expect(table.querySelectorAll("tbody tr[data-separator]")).toHaveLength(1);
	const placeholders = table.querySelectorAll("tbody [data-loading-row]");
	await expect(placeholders).toHaveLength(rows);
	// A cell per column, so the rows that replace the placeholders do not move the table.
	const columns = table.querySelectorAll("thead th").length;
	for (const row of placeholders) {
		await expect(row.querySelectorAll("td")).toHaveLength(columns);
	}
	// The separator, then only placeholders, after every loaded row.
	const bodyRows = [...table.querySelectorAll("tbody tr")];
	const separator = bodyRows.findIndex((row) => row.matches("[data-separator]"));
	await expect(bodyRows.slice(separator + 1)).toEqual([...placeholders]);
	await expect(bodyRows.slice(0, separator).some((row) => row.matches("[data-loading-row]"))).toBe(
		false,
	);
}

/**
 * Checks that a list that is not a table continues itself while its next page loads: the list is
 * busy and holds placeholders in the shape of its rows, hidden from assistive technology, together
 * after its loaded rows. `bars` is the number of skeleton bars one placeholder of that shape has.
 */
export async function expectListPlaceholders(
	list: HTMLElement,
	{ rows = LOADING_MORE_ROWS, bars }: { rows?: number; bars: number },
) {
	await expect(list).toHaveAttribute("aria-busy", "true");
	const placeholders = [...list.querySelectorAll(":scope > [data-loading-row]")];
	await expect(placeholders).toHaveLength(rows);
	for (const placeholder of placeholders) {
		await expect(placeholder).toHaveAttribute("aria-hidden", "true");
		// The bars of one row of the list's own shape, so a plain block would not pass.
		await expect(placeholder.querySelectorAll("[data-slot=skeleton]")).toHaveLength(bars);
	}
	// Together, after every loaded row.
	const children = [...list.children];
	const [head] = placeholders;
	if (head === undefined) {
		return;
	}
	const first = children.indexOf(head);
	await expect(children.slice(first, first + rows)).toEqual(placeholders);
	await expect(children.slice(0, first).some((child) => child.matches("[data-loading-row]"))).toBe(
		false,
	);
}
