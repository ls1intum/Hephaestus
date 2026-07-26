import { render, screen, within } from "@testing-library/react";
import type { ReactElement } from "react";
import { describe, expect, it } from "vitest";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import { LlmUsageByDayTable, LlmUsageByJobTypeTable } from "./LlmUsageBreakdownTables";

/**
 * Deliberately inconsistent with its own rows: the day and job-type rows come to $9.00 and $3.00,
 * while the report's month totals say $4.25 and $1.75. Only a table that prints the server's figure
 * can show 4.25 — one that re-adds the rows shows 9.00.
 *
 * Real payloads agree, of course. The disagreement here is the instrument: it is the only way to see
 * *which* of the two numbers a footer is made of.
 */
const report: WorkspaceLlmUsageReport = {
	month: "2026-07",
	instanceTotalCostUsd: 4.25,
	ownProviderTotalCostUsd: 1.75,
	instanceBudgetVerdict: "WITHIN",
	instancePaused: false,
	ownProviderBudgetVerdict: "WITHIN",
	ownProviderPaused: false,
	unpricedEventCount: 3,
	byDay: [
		{
			day: new Date("2026-07-05T00:00:00.000Z"),
			instanceTotalCostUsd: 4.5,
			ownProviderTotalCostUsd: 1.5,
			unpricedEventCount: 1,
			events: 10,
		},
		{
			day: new Date("2026-07-06T00:00:00.000Z"),
			instanceTotalCostUsd: 4.5,
			ownProviderTotalCostUsd: 1.5,
			unpricedEventCount: 2,
			events: 20,
		},
	],
	byJobType: [
		{
			jobType: "PULL_REQUEST_REVIEW",
			instanceTotalCostUsd: 4.5,
			ownProviderTotalCostUsd: 1.5,
			unpricedEventCount: 1,
			inputTokens: 1000,
			outputTokens: 200,
			cacheReadTokens: 0,
			cacheWriteTokens: 0,
			totalCalls: 12,
			events: 10,
		},
		{
			jobType: "MENTOR_TURN",
			instanceTotalCostUsd: 4.5,
			ownProviderTotalCostUsd: 1.5,
			unpricedEventCount: 2,
			inputTokens: 3000,
			outputTokens: 400,
			cacheReadTokens: 0,
			cacheWriteTokens: 0,
			totalCalls: 24,
			events: 20,
		},
	],
};

function totalsRowOf(tableName: string): HTMLElement {
	const table = screen.getByRole("table", { name: tableName });
	return within(table).getByRole("row", { name: /^Total/ });
}

describe("usage breakdown totals", () => {
	// The same claim about both breakdowns, so it is made once: neither footer may re-add its rows.
	it.each<[string, () => ReactElement, string]>([
		["day", () => <LlmUsageByDayTable report={report} />, "AI spend by day"],
		["run type", () => <LlmUsageByJobTypeTable report={report} />, "AI spend by run type"],
	])("prints the server's month spend, not a re-addition of the %s rows", (_name, table, name) => {
		render(table());

		const footer = totalsRowOf(name);
		expect(footer.textContent).toContain("$4.25");
		expect(footer.textContent).toContain("$1.75");
		expect(footer.textContent).not.toContain("$9.00");
		expect(footer.textContent).not.toContain("$3.00");
	});

	it("still adds the counts up itself — integers sum exactly, and the report carries no token totals", () => {
		render(<LlmUsageByJobTypeTable report={report} />);

		const footer = totalsRowOf("AI spend by run type");
		// Server money, then client counts: 1+2 unpriced, 1000+3000 input, 200+400 output, 12+24
		// calls, 10+20 runs. The blended average is deliberately absent (two purses, one dash).
		expect(footer.textContent).toBe("Total$4.25$1.75—34,0006003630");
	});

	it("drops the footer for a single row rather than restating the line above it", () => {
		render(<LlmUsageByDayTable report={{ ...report, byDay: [report.byDay[0]] }} />);

		const table = screen.getByRole("table", { name: "AI spend by day" });
		expect(within(table).queryByRole("row", { name: /^Total/ })).toBeNull();
	});
});
