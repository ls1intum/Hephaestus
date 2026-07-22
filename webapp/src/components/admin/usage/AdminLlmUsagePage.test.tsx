import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import { AdminLlmUsagePage } from "./AdminLlmUsagePage";

const report: WorkspaceLlmUsageReport = {
	month: "2026-07",
	monthlyBudgetUsd: 25,
	pricedTotalCostUsd: 4.25,
	byoTotalCostUsd: 1.75,
	verdict: "UNVERIFIABLE",
	usagePaused: false,
	unpricedEventCount: 2,
	byJobType: [
		{
			jobType: "PULL_REQUEST_REVIEW",
			pricedTotalCostUsd: 4.25,
			byoTotalCostUsd: 1.75,
			unpricedEventCount: 2,
			inputTokens: 1_000,
			outputTokens: 250,
			cacheReadTokens: 0,
			cacheWriteTokens: 0,
			totalCalls: 7,
			events: 3,
		},
	],
	byDay: [
		{
			day: new Date("2026-07-05T00:00:00.000Z"),
			pricedTotalCostUsd: 4.25,
			byoTotalCostUsd: 1.75,
			unpricedEventCount: 2,
			events: 3,
		},
	],
};

function renderPage() {
	render(
		<AdminLlmUsagePage
			month="2026-07"
			isCurrentMonth
			report={report}
			isLoading={false}
			error={null}
			onPrevMonth={() => {}}
			onNextMonth={() => {}}
		/>,
	);
}

describe("AdminLlmUsagePage", () => {
	it("separates instance-funded and workspace-owned spend in every rollup", () => {
		renderPage();

		expect(screen.getByText("Month-to-date instance-funded spend")).toBeTruthy();
		expect(screen.getByText("Workspace-owned spend")).toBeTruthy();

		const byJobType = screen.getByRole("table", { name: "AI spend by job type" });
		expect(within(byJobType).getByRole("columnheader", { name: "Instance-funded" })).toBeTruthy();
		expect(within(byJobType).getByRole("columnheader", { name: "Workspace-owned" })).toBeTruthy();
		expect(within(byJobType).getByRole("columnheader", { name: "Unpriced calls" })).toBeTruthy();
		expect(within(byJobType).getByText("$4.25")).toBeTruthy();
		expect(within(byJobType).getByText("$1.75")).toBeTruthy();

		const byDay = screen.getByRole("table", { name: "AI spend by day" });
		expect(within(byDay).getByRole("columnheader", { name: "Instance-funded" })).toBeTruthy();
		expect(within(byDay).getByRole("columnheader", { name: "Workspace-owned" })).toBeTruthy();
		expect(within(byDay).getByRole("columnheader", { name: "Unpriced calls" })).toBeTruthy();
		expect(within(byDay).getByText("$4.25")).toBeTruthy();
		expect(within(byDay).getByText("$1.75")).toBeTruthy();
	});

	it("gives the right pricing owner an actionable unpriced-usage warning", () => {
		renderPage();

		expect(screen.getByText(/For a workspace-owned model, add its price in Models\./)).toBeTruthy();
		expect(
			screen.getByText(/For a shared model, ask an instance admin to add pricing\./),
		).toBeTruthy();
	});
});
