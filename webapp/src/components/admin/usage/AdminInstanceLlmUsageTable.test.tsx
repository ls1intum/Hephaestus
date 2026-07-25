import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { FxRateInfo, WorkspaceLlmUsageReport } from "@/api/types.gen";
import {
	AdminInstanceLlmUsageTable,
	type AdminWorkspaceLlmUsageRow,
} from "./AdminInstanceLlmUsageTable";

const workspace: AdminWorkspaceLlmUsageRow = {
	workspaceId: 1,
	workspaceSlug: "example-workspace",
	displayName: "Example Workspace",
	instanceMonthlyBudgetUsd: 25,
	pricedTotalCostUsd: 4.25,
	instanceBudgetVerdict: "WITHIN",
	instanceFundedPaused: false,
	byoTotalCostUsd: 1.75,
	byoBudgetVerdict: "WITHIN",
	byoPaused: false,
	events: 3,
};

const detailReport: WorkspaceLlmUsageReport = {
	month: "2026-07",
	instanceMonthlyBudgetUsd: 25,
	pricedTotalCostUsd: 4.25,
	instanceBudgetVerdict: "WITHIN",
	instanceFundedPaused: false,
	byoMonthlyBudgetUsd: 10,
	byoTotalCostUsd: 1.75,
	byoBudgetVerdict: "WITHIN",
	byoPaused: false,
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
			totalCalls: 4,
			// Two events, so the per-event averages ($2.125 and $0.875) can't collide with the spend
			// figures they are derived from and each assertion below names one cell.
			events: 2,
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

function renderTable(
	rows: AdminWorkspaceLlmUsageRow[],
	overrides: { isCurrentMonth?: boolean } = {},
) {
	return render(
		<AdminInstanceLlmUsageTable
			rows={rows}
			isCurrentMonth={overrides.isCurrentMonth ?? true}
			isLoading={false}
			error={null}
			expandedWorkspaceId={null}
			isDetailLoading={false}
			detailError={null}
			onToggleDetails={() => {}}
			onEditBudget={() => {}}
		/>,
	);
}

/** The body row for the single-workspace fixtures below. */
function firstDataRow() {
	return screen.getAllByRole("row")[1];
}

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

		expect(screen.getByRole("columnheader", { name: "Shared-model spend" })).toBeTruthy();
		expect(screen.getByRole("columnheader", { name: "Provider spend" })).toBeTruthy();
		const toggle = screen.getByRole("button", {
			name: "View usage details for Example Workspace",
		});
		expect(toggle.getAttribute("aria-expanded")).toBe("false");
		// The detail row doesn't exist yet, so nothing may point at it.
		expect(toggle.getAttribute("aria-controls")).toBeNull();

		fireEvent.click(toggle);
		expect(onToggleDetails).toHaveBeenCalledWith(workspace);
	});

	it("separates the instance cap from the workspace's own provider cap", () => {
		renderTable([{ ...workspace, byoMonthlyBudgetUsd: 10, byoBudgetVerdict: "WITHIN" }]);

		expect(screen.getByRole("columnheader", { name: "Instance cap" })).toBeTruthy();
		expect(screen.getByRole("columnheader", { name: "Provider cap" })).toBeTruthy();
		const row = within(firstDataRow());
		// A cap renders without trailing cents — "$25", not "$25.00".
		expect(row.getByText("$25")).toBeTruthy();
		expect(row.getByText("$10")).toBeTruthy();
	});

	it("shows how much of each cap is used, not just whether it is reached", () => {
		renderTable([{ ...workspace, instanceMonthlyBudgetUsd: 50, pricedTotalCostUsd: 38.2 }]);

		const row = within(firstDataRow());
		expect(row.getByText("$38.20 · 76%")).toBeTruthy();
		expect(
			row.getByRole("progressbar", { name: "Instance cap used by Example Workspace" }),
		).toBeTruthy();
		expect(screen.getByRole("cell", { name: /Within budget/ })).toBeTruthy();
	});

	it("warns before the cap is reached", () => {
		renderTable([{ ...workspace, instanceMonthlyBudgetUsd: 50, pricedTotalCostUsd: 41 }]);

		const row = within(firstDataRow());
		expect(row.getByText("Near cap · instance cap")).toBeTruthy();
		// The tone alone must never carry the state (WCAG SC 1.4.1).
		expect(row.getByText("$41.00 · 82% · Near cap")).toBeTruthy();
	});

	it("names the cap that paused the workspace", () => {
		renderTable([
			{
				...workspace,
				instanceBudgetVerdict: "EXHAUSTED",
				instanceFundedPaused: true,
				byoMonthlyBudgetUsd: 10,
				byoTotalCostUsd: 10,
				byoBudgetVerdict: "EXHAUSTED",
				byoPaused: true,
			},
		]);

		const row = within(firstDataRow());
		expect(row.getByText("Paused · instance cap")).toBeTruthy();
		expect(row.getByText("Paused · provider cap")).toBeTruthy();
	});

	it("reports a provider-cap pause even when the instance cap is untouched", () => {
		renderTable([
			{
				...workspace,
				instanceMonthlyBudgetUsd: undefined,
				byoMonthlyBudgetUsd: 10,
				byoTotalCostUsd: 10,
				byoBudgetVerdict: "EXHAUSTED",
				byoPaused: true,
			},
		]);

		const row = within(firstDataRow());
		expect(row.getByText("Paused · provider cap")).toBeTruthy();
		expect(row.queryByText("Paused · instance cap")).toBeNull();
	});

	it("keeps the provider cap read-only — it is the workspace's own money", () => {
		renderTable([{ ...workspace, byoMonthlyBudgetUsd: 10, byoBudgetVerdict: "WITHIN" }]);

		const buttons = within(firstDataRow())
			.getAllByRole("button")
			.map((button) => button.getAttribute("aria-label") ?? button.textContent);
		expect(buttons).toEqual(["View usage details for Example Workspace", "Set instance cap"]);
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

	describe("display currency", () => {
		const eur: FxRateInfo = {
			currencyCode: "EUR",
			ratePerUsd: 0.878966,
			rateDate: new Date("2026-07-24T00:00:00.000Z"),
		};

		it("converts both spend columns, not just the one the host pays for", () => {
			renderTable([{ ...workspace, fx: eur }]);

			const row = within(firstDataRow());
			// $4.25 shared and $1.75 on the workspace's own provider — the same physical quantity,
			// differently funded. Converting one and not the other reads as "these differ in kind".
			expect(row.getByLabelText("approximately 3.74 euros")).toBeTruthy();
			expect(row.getByLabelText("approximately 1.54 euros")).toBeTruthy();
		});

		it("puts the rate footnote after the figures it qualifies", () => {
			const { container } = renderTable([{ ...workspace, fx: eur }]);

			const disclosure = screen.getByText(/ECB reference rate/);
			const table = container.querySelector("table");
			if (table == null) {
				throw new Error("expected the rollup table to render");
			}
			// A footnote that precedes its numbers reads as a preamble to something else.
			expect(
				table.compareDocumentPosition(disclosure) & Node.DOCUMENT_POSITION_FOLLOWING,
			).toBeTruthy();
		});

		it("stays silent about a rate nothing on the table used", () => {
			renderTable([{ ...workspace, pricedTotalCostUsd: 0, byoTotalCostUsd: 0, fx: eur }]);

			expect(screen.queryByText(/ECB reference rate/)).toBeNull();
		});

		it("hands the table's own rate to the expanded breakdown", () => {
			render(
				<AdminInstanceLlmUsageTable
					rows={[{ ...workspace, fx: eur }]}
					isCurrentMonth
					isLoading={false}
					error={null}
					expandedWorkspaceId={workspace.workspaceId}
					// The detail response carries its own block. One month resolves to one rate, so the two
					// agree today — but nothing enforces that, and two rates on one screen is a bug nobody
					// would spot. The panel renders the table's.
					detailReport={{
						...detailReport,
						fx: { ...eur, currencyCode: "GBP", ratePerUsd: 0.5 },
						byDay: [
							detailReport.byDay[0],
							{
								day: new Date("2026-07-06T00:00:00.000Z"),
								pricedTotalCostUsd: 4.25,
								byoTotalCostUsd: 1.75,
								unpricedEventCount: 0,
								events: 1,
							},
						],
					}}
					isDetailLoading={false}
					detailError={null}
					onToggleDetails={() => {}}
					onEditBudget={() => {}}
				/>,
			);

			const byDay = screen.getByRole("table", { name: "AI spend by day" });
			const footer = within(byDay).getByRole("row", { name: /^Total/ });
			expect(footer.textContent).toContain("€");
			expect(footer.textContent).not.toContain("£");
		});
	});
});
