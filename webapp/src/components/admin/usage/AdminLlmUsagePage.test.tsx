import {
	createMemoryHistory,
	createRootRoute,
	createRouter,
	RouterProvider,
} from "@tanstack/react-router";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import { AdminLlmUsagePage } from "./AdminLlmUsagePage";

const baseReport: WorkspaceLlmUsageReport = {
	month: "2026-07",
	instanceMonthlyBudgetUsd: 25,
	byoMonthlyBudgetUsd: 10,
	pricedTotalCostUsd: 4.25,
	byoTotalCostUsd: 1.75,
	instanceBudgetVerdict: "WITHIN",
	byoBudgetVerdict: "WITHIN",
	instanceFundedPaused: false,
	byoPaused: false,
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
			events: 5,
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

/**
 * The page links to the workspace's models page, so it needs a router in scope. The router mounts
 * asynchronously, hence the await before any assertion.
 */
async function renderPage(
	report: WorkspaceLlmUsageReport = baseReport,
	props: Partial<React.ComponentProps<typeof AdminLlmUsagePage>> = {},
) {
	const rootRoute = createRootRoute({
		component: () => (
			<AdminLlmUsagePage
				month="2026-07"
				isCurrentMonth
				workspaceSlug="acme"
				report={report}
				isLoading={false}
				error={null}
				onPrevMonth={() => {}}
				onNextMonth={() => {}}
				onEditByoCap={() => {}}
				now={new Date("2026-07-10T12:00:00.000Z")}
				{...props}
			/>
		),
	});
	const router = createRouter({
		routeTree: rootRoute,
		history: createMemoryHistory({ initialEntries: ["/"] }),
	});
	// biome-ignore lint/suspicious/noExplicitAny: the ad-hoc root-only tree isn't the app's route tree.
	render(<RouterProvider router={router as any} />);
	await screen.findByRole("heading", { name: "AI usage" });
}

describe("AdminLlmUsagePage", () => {
	it("separates instance-funded and workspace-owned spend in every rollup", async () => {
		await renderPage();

		expect(screen.getByText("Shared models — budget set by your host")).toBeTruthy();
		expect(screen.getByText("Your own provider — your cap")).toBeTruthy();

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

	it("gives each cap its own meter, named for whose money it is", async () => {
		await renderPage();

		expect(screen.getByRole("progressbar", { name: "Shared-model budget used" })).toBeTruthy();
		expect(screen.getByRole("progressbar", { name: "Your own-provider cap used" })).toBeTruthy();
	});

	it("gives the right pricing owner an actionable unpriced-usage warning", async () => {
		await renderPage();

		expect(
			screen.getByText(/For a model on your own provider, add its price in Models\./),
		).toBeTruthy();
		expect(screen.getByText(/For a shared model, ask your host to add pricing\./)).toBeTruthy();
	});

	it("shows the per-event average alongside the untouched funding columns", async () => {
		await renderPage();

		const byJobType = screen.getByRole("table", { name: "AI spend by job type" });
		expect(within(byJobType).getByRole("columnheader", { name: "Avg per event" })).toBeTruthy();
		// 5 events: $4.25 shared and $1.75 own, each averaged on its own — never summed.
		expect(within(byJobType).getByText("≈ $0.850")).toBeTruthy();
		expect(within(byJobType).getByText("shared")).toBeTruthy();
		expect(within(byJobType).getByText("≈ $0.350")).toBeTruthy();
		expect(within(byJobType).getByText("own")).toBeTruthy();
	});

	describe("pause banners", () => {
		it("routes an exhausted own-provider cap to the admin who can lift it", async () => {
			const onEditByoCap = vi.fn();
			await renderPage(
				{ ...baseReport, byoPaused: true, byoBudgetVerdict: "EXHAUSTED", byoTotalCostUsd: 10 },
				{ onEditByoCap },
			);

			expect(screen.getByText("Your monthly cap is reached")).toBeTruthy();
			expect(
				screen.getByText(
					/Work on your own provider is paused until August 1 \(UTC\), or until you raise or remove your cap\./,
				),
			).toBeTruthy();

			fireEvent.click(screen.getByRole("button", { name: "Change your cap" }));
			expect(onEditByoCap).toHaveBeenCalled();
		});

		it("tells an unenforceable own cap how to become enforceable again", async () => {
			await renderPage({ ...baseReport, byoPaused: true, byoBudgetVerdict: "UNVERIFIABLE" });

			const banner = screen.getByText("Your cap can't be enforced").closest("[role='alert']");
			expect(banner).toBeTruthy();
			expect(
				within(banner as HTMLElement)
					.getByRole("link", { name: "models page" })
					.getAttribute("href"),
			).toBe("/w/acme/admin/models");
		});

		it("keeps an exhausted shared budget non-destructive and off the workspace's plate", async () => {
			await renderPage({
				...baseReport,
				instanceFundedPaused: true,
				instanceBudgetVerdict: "EXHAUSTED",
				pricedTotalCostUsd: 25,
			});

			expect(screen.getByText("Shared-model budget reached")).toBeTruthy();
			expect(
				screen.getByText(
					/paused until August 1 \(UTC\) or until your host raises the budget\. Work on your own connected provider is not affected\./,
				),
			).toBeTruthy();
			expect(
				screen.getByRole("link", { name: "Switch a purpose to your own provider" }),
			).toBeTruthy();
		});

		it("never asks the workspace admin to price a shared model", async () => {
			await renderPage({
				...baseReport,
				instanceFundedPaused: true,
				instanceBudgetVerdict: "UNVERIFIABLE",
			});

			expect(screen.getByText("Shared-model spend can't be verified")).toBeTruthy();
			expect(
				screen.getByText(/Only your host can price a shared model — ask them to\./),
			).toBeTruthy();
		});

		it("puts the cap they can act on first when both are paused", async () => {
			await renderPage({
				...baseReport,
				byoPaused: true,
				byoBudgetVerdict: "EXHAUSTED",
				instanceFundedPaused: true,
				instanceBudgetVerdict: "EXHAUSTED",
			});

			const alerts = screen.getAllByRole("alert");
			expect(alerts[0].textContent).toContain("Your monthly cap is reached");
			expect(alerts[1].textContent).toContain("Shared-model budget reached");
		});

		it("shows no pause banner for a past month", async () => {
			await renderPage(
				{ ...baseReport, byoPaused: true, byoBudgetVerdict: "EXHAUSTED" },
				{ month: "2026-06", isCurrentMonth: false },
			);

			expect(screen.queryByText("Your monthly cap is reached")).toBeNull();
		});
	});

	describe("approaching a cap", () => {
		it("warns at 80% with a burn-rate date, as a status rather than an alert", async () => {
			await renderPage({ ...baseReport, byoTotalCostUsd: 8.4 });

			const warning = screen.getByText("You've used 84% of your own-provider cap");
			expect(warning.closest("[role='status']")).toBeTruthy();
			expect(
				screen.getByText(/At this month's pace you'll reach it around July 12\./),
			).toBeTruthy();
		});

		it("withholds the projection in the first days of the month, keeping the warning", async () => {
			await renderPage(
				{ ...baseReport, byoTotalCostUsd: 8.4 },
				{ now: new Date("2026-07-02T12:00:00.000Z") },
			);

			expect(screen.getByText("You've used 84% of your own-provider cap")).toBeTruthy();
			expect(screen.queryByText(/At this month's pace/)).toBeNull();
		});

		it("stays quiet below the threshold", async () => {
			await renderPage();

			expect(screen.queryByText(/You've used/)).toBeNull();
		});

		it("does not warn about a cap that is already paused", async () => {
			await renderPage({
				...baseReport,
				byoTotalCostUsd: 10,
				byoPaused: true,
				byoBudgetVerdict: "EXHAUSTED",
			});

			expect(screen.queryByText(/You've used 100% of your own-provider cap/)).toBeNull();
		});
	});

	describe("own-provider card", () => {
		it("offers a cap even when nothing has run on a provider of their own", async () => {
			const onEditByoCap = vi.fn();
			await renderPage(
				{
					...baseReport,
					byoMonthlyBudgetUsd: undefined,
					byoTotalCostUsd: 0,
					byJobType: [],
					byDay: [],
				},
				{ onEditByoCap },
			);

			expect(
				screen.getByText(/Nothing has run on a provider of your own this month\./),
			).toBeTruthy();
			fireEvent.click(screen.getByRole("button", { name: "Set a cap" }));
			expect(onEditByoCap).toHaveBeenCalled();
		});

		it("keeps the card whenever there is own-provider spend, capped or not", async () => {
			await renderPage({ ...baseReport, byoMonthlyBudgetUsd: undefined });

			expect(
				screen.getByText(/No cap — this workspace's own-provider spend is unlimited\./),
			).toBeTruthy();
			expect(screen.getByRole("button", { name: "Set a cap" })).toBeTruthy();
		});
	});
});
