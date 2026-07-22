import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { AdminWorkspaceLlmUsage, WorkspaceLlmUsageReport } from "@/api/types.gen";
import { AdminInstanceLlmUsageTable } from "./AdminInstanceLlmUsageTable";

const workspace: AdminWorkspaceLlmUsage = {
	workspaceId: 1,
	workspaceSlug: "example-workspace",
	displayName: "Example Workspace",
	monthlyBudgetUsd: 25,
	pricedTotalCostUsd: 4.25,
	byoTotalCostUsd: 1.75,
	events: 3,
	verdict: "WITHIN",
};

const detailReport: WorkspaceLlmUsageReport = {
	month: "2026-07",
	monthlyBudgetUsd: 25,
	pricedTotalCostUsd: 4.25,
	byoTotalCostUsd: 1.75,
	verdict: "WITHIN",
	usagePaused: false,
	unpricedEventCount: 0,
	byJobType: [
		{
			jobType: "MENTOR_TURN",
			pricedTotalCostUsd: 4.25,
			byoTotalCostUsd: 1.75,
			unpricedEventCount: 0,
			inputTokens: 100,
			outputTokens: 25,
			cacheReadTokens: 0,
			cacheWriteTokens: 0,
			totalCalls: 2,
			events: 1,
		},
	],
	byDay: [
		{
			day: new Date("2026-07-05T00:00:00.000Z"),
			pricedTotalCostUsd: 4.25,
			byoTotalCostUsd: 1.75,
			unpricedEventCount: 0,
			events: 1,
		},
	],
};

describe("AdminInstanceLlmUsageTable", () => {
	it("offers an accessible per-workspace detail toggle", () => {
		const onToggleDetails = vi.fn();
		render(
			<AdminInstanceLlmUsageTable
				rows={[workspace]}
				isCurrentMonth
				isLoading={false}
				error={null}
				expandedWorkspaceId={null}
				isDetailLoading={false}
				detailError={null}
				onToggleDetails={onToggleDetails}
				onEditBudget={() => {}}
			/>,
		);

		expect(screen.getByRole("columnheader", { name: "Instance-funded" })).toBeTruthy();
		expect(screen.getByRole("columnheader", { name: "Workspace-owned" })).toBeTruthy();
		const toggle = screen.getByRole("button", {
			name: "View usage details for Example Workspace",
		});
		expect(toggle.getAttribute("aria-expanded")).toBe("false");

		fireEvent.click(toggle);
		expect(onToggleDetails).toHaveBeenCalledWith(workspace);
	});

	it("shows daily and job-type funding breakdowns for the expanded workspace", () => {
		render(
			<AdminInstanceLlmUsageTable
				rows={[workspace]}
				isCurrentMonth
				isLoading={false}
				error={null}
				expandedWorkspaceId={workspace.workspaceId}
				detailReport={detailReport}
				isDetailLoading={false}
				detailError={null}
				onToggleDetails={() => {}}
				onEditBudget={() => {}}
			/>,
		);

		expect(
			screen
				.getByRole("button", { name: "Hide usage details for Example Workspace" })
				.getAttribute("aria-expanded"),
		).toBe("true");
		const byJobType = screen.getByRole("table", { name: "AI spend by job type" });
		expect(within(byJobType).getByText("Mentor turn")).toBeTruthy();
		expect(within(byJobType).getByText("$1.75")).toBeTruthy();
		const byDay = screen.getByRole("table", { name: "AI spend by day" });
		expect(within(byDay).getByText("Jul 5")).toBeTruthy();
		expect(within(byDay).getByText("$4.25")).toBeTruthy();
	});
});
