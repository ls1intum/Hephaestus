import {
	createMemoryHistory,
	createRootRoute,
	createRouter,
	RouterProvider,
} from "@tanstack/react-router";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { FxRateInfo, WorkspaceLlmUsageReport } from "@/api/types.gen";
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
	it("separates shared-model and provider spend in every rollup", async () => {
		await renderPage();

		expect(screen.getByText("Shared-model spend so far")).toBeTruthy();
		expect(screen.getByText("Shared-model budget · set by your host")).toBeTruthy();
		expect(screen.getByText("Your provider spend so far")).toBeTruthy();
		expect(screen.getByText("Billed to your own provider key.")).toBeTruthy();

		const byJobType = screen.getByRole("table", { name: "AI spend by job type" });
		expect(within(byJobType).getByRole("columnheader", { name: "Shared models" })).toBeTruthy();
		expect(within(byJobType).getByRole("columnheader", { name: "Your provider" })).toBeTruthy();
		expect(within(byJobType).getByRole("columnheader", { name: "No price set" })).toBeTruthy();
		expect(within(byJobType).getByText("$4.25")).toBeTruthy();
		expect(within(byJobType).getByText("$1.75")).toBeTruthy();

		const byDay = screen.getByRole("table", { name: "AI spend by day" });
		expect(within(byDay).getByRole("columnheader", { name: "Shared models" })).toBeTruthy();
		expect(within(byDay).getByRole("columnheader", { name: "Your provider" })).toBeTruthy();
		expect(within(byDay).getByRole("columnheader", { name: "No price set" })).toBeTruthy();
		expect(within(byDay).getByText("$4.25")).toBeTruthy();
		expect(within(byDay).getByText("$1.75")).toBeTruthy();
	});

	it("gives each cap its own meter, named for whose money it is", async () => {
		await renderPage();

		expect(screen.getByRole("progressbar", { name: "Shared-model budget used" })).toBeTruthy();
		expect(screen.getByRole("progressbar", { name: "Your provider cap used" })).toBeTruthy();
	});

	it("gives the right pricing owner an actionable no-price-set warning", async () => {
		await renderPage();

		expect(screen.getByText("2 calls aren't counted in these totals")).toBeTruthy();
		expect(
			screen.getByText(/Add prices for your own models in .*; for shared models, ask your host\./),
		).toBeTruthy();
	});

	it("shows the per-event average alongside the untouched funding columns", async () => {
		await renderPage();

		const byJobType = screen.getByRole("table", { name: "AI spend by job type" });
		expect(within(byJobType).getByRole("columnheader", { name: "Avg per event" })).toBeTruthy();
		// 5 events: $4.25 shared and $1.75 own, each averaged on its own — never summed. No "≈": on
		// this page that glyph means "converted currency", and the column header already says "Avg".
		expect(within(byJobType).getByText("$0.85")).toBeTruthy();
		expect(within(byJobType).getByText("shared models")).toBeTruthy();
		expect(within(byJobType).getByText("$0.35")).toBeTruthy();
		expect(within(byJobType).getByText("your provider")).toBeTruthy();
	});

	describe("pause banners", () => {
		it("routes a reached provider cap to the admin who can lift it", async () => {
			const onEditByoCap = vi.fn();
			await renderPage(
				{ ...baseReport, byoPaused: true, byoBudgetVerdict: "EXHAUSTED", byoTotalCostUsd: 10 },
				{ onEditByoCap },
			);

			expect(screen.getByText("Your provider cap is reached")).toBeTruthy();
			expect(
				screen.getByText("Paused until August 1 (UTC), or until you raise or remove the cap."),
			).toBeTruthy();

			fireEvent.click(screen.getByRole("button", { name: "Adjust cap" }));
			expect(onEditByoCap).toHaveBeenCalled();
		});

		it("tells an unenforceable provider cap how to become enforceable again", async () => {
			await renderPage({ ...baseReport, byoPaused: true, byoBudgetVerdict: "UNVERIFIABLE" });

			const banner = screen.getByText("Your cap can't be enforced").closest("[role='alert']");
			expect(banner).toBeTruthy();
			expect(
				within(banner as HTMLElement).getByText(
					"2 calls on your models have no price, so the cap can't be checked and your provider is paused. Add a price to resume, or remove the cap.",
				),
			).toBeTruthy();
			expect(
				within(banner as HTMLElement)
					.getByRole("link", { name: "Open AI models" })
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
				screen.getByText("Paused until August 1 (UTC), or until your host raises the budget."),
			).toBeTruthy();
			expect(screen.getByRole("link", { name: "Switch a purpose to your provider" })).toBeTruthy();
		});

		it("never asks the workspace admin to price a shared model", async () => {
			await renderPage({
				...baseReport,
				instanceFundedPaused: true,
				instanceBudgetVerdict: "UNVERIFIABLE",
			});

			expect(screen.getByText("Shared-model spend can't be verified")).toBeTruthy();
			expect(
				screen.getByText(
					"2 shared-model calls have no price, so the budget can't be checked and shared models are paused. Only your host can price them.",
				),
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
			expect(alerts[0].textContent).toContain("Your provider cap is reached");
			expect(alerts[1].textContent).toContain("Shared-model budget reached");
		});

		it("shows no pause banner for a past month", async () => {
			await renderPage(
				{ ...baseReport, byoPaused: true, byoBudgetVerdict: "EXHAUSTED" },
				{ month: "2026-06", isCurrentMonth: false },
			);

			expect(screen.queryByText("Your provider cap is reached")).toBeNull();
		});
	});

	describe("approaching a cap", () => {
		it("warns at 80% with a burn-rate date, as a status rather than an alert", async () => {
			await renderPage({ ...baseReport, byoTotalCostUsd: 8.4 });

			const warning = screen.getByText("You've used 84% of your provider cap");
			expect(warning.closest("[role='status']")).toBeTruthy();
			expect(screen.getByText(/At this pace you'll hit it around July 12\./)).toBeTruthy();
		});

		it("withholds the projection in the first days of the month, keeping the warning", async () => {
			await renderPage(
				{ ...baseReport, byoTotalCostUsd: 8.4 },
				{ now: new Date("2026-07-02T12:00:00.000Z") },
			);

			expect(screen.getByText("You've used 84% of your provider cap")).toBeTruthy();
			expect(screen.queryByText(/At this pace/)).toBeNull();
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

			expect(screen.queryByText(/You've used 100% of your provider cap/)).toBeNull();
		});
	});

	describe("provider card", () => {
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

			expect(screen.getByText(/Nothing ran on a provider of your own this month\./)).toBeTruthy();
			fireEvent.click(screen.getByRole("button", { name: "Set cap" }));
			expect(onEditByoCap).toHaveBeenCalled();
		});

		it("keeps the card whenever there is provider spend, capped or not", async () => {
			await renderPage({ ...baseReport, byoMonthlyBudgetUsd: undefined });

			expect(screen.getByText("No cap set.")).toBeTruthy();
			expect(screen.getByRole("button", { name: "Set cap" })).toBeTruthy();
		});
	});

	describe("display currency", () => {
		const eur: FxRateInfo = {
			currencyCode: "EUR",
			ratePerUsd: 0.878966,
			rateDate: new Date("2026-07-24T00:00:00.000Z"),
		};

		/** Two days, so the breakdown table earns a footer — and the footer converts its total. */
		const twoDays: WorkspaceLlmUsageReport["byDay"] = [
			{
				day: new Date("2026-07-05T00:00:00.000Z"),
				pricedTotalCostUsd: 6.2,
				byoTotalCostUsd: 0,
				unpricedEventCount: 0,
				events: 2,
			},
			{
				day: new Date("2026-07-06T00:00:00.000Z"),
				pricedTotalCostUsd: 6.2,
				byoTotalCostUsd: 0,
				unpricedEventCount: 0,
				events: 2,
			},
		];

		/**
		 * Regression: the caption used to be derived from the *cards* alone. A $0 shared budget — the
		 * supported "pause now" state — converts to nothing, and with no provider side every card
		 * conversion was null, so the caption disappeared. The table footers convert independently and
		 * kept rendering "≈ €10.90": euros on screen with no rate, no date and no disclosure.
		 */
		it("discloses the rate when only the table footers convert", async () => {
			await renderPage({
				...baseReport,
				instanceMonthlyBudgetUsd: 0,
				byoMonthlyBudgetUsd: undefined,
				pricedTotalCostUsd: 12.4,
				byoTotalCostUsd: 0,
				unpricedEventCount: 0,
				byJobType: [],
				byDay: twoDays,
				fx: eur,
			});

			const byDay = screen.getByRole("table", { name: "AI spend by day" });
			const footer = within(byDay).getByRole("row", { name: /^Total/ });
			expect(footer.textContent).toContain("≈ €10.90");
			expect(
				screen.getByText(/EUR amounts are estimates at the ECB reference rate for Jul 24, 2026/),
			).toBeTruthy();
		});

		it("says nothing about a rate when nothing on the page converted", async () => {
			await renderPage({
				...baseReport,
				instanceMonthlyBudgetUsd: 0,
				byoMonthlyBudgetUsd: undefined,
				pricedTotalCostUsd: 0,
				byoTotalCostUsd: 0,
				unpricedEventCount: 0,
				byJobType: [],
				byDay: [],
				fx: eur,
			});

			expect(screen.queryByText(/ECB reference rate/)).toBeNull();
		});

		it("converts the projected month-end figure in the same breath as the spend it follows", async () => {
			await renderPage(
				{
					...baseReport,
					instanceMonthlyBudgetUsd: 50,
					pricedTotalCostUsd: 43.9,
					byoMonthlyBudgetUsd: undefined,
					byoTotalCostUsd: 0,
					unpricedEventCount: 0,
					fx: eur,
					// Late enough in the month that the pace lands under the cap, which is the branch that
					// quotes a month-end figure instead of a date.
				},
				{ now: new Date("2026-07-28T12:00:00.000Z") },
			);

			const alert = screen.getByText(/At this pace/);
			// Half a converted sentence reads as a mistake: "$43.90 of $50 (≈ €38.59 of €44) … you'll
			// finish the month around $61.20" made the reader switch currencies mid-breath.
			expect(alert.textContent).toContain("≈ €38.59 of €44");
			expect(alert.textContent).toMatch(/finish the month around \$[\d.]+ \(≈ €[\d.]+\)\./);
		});
	});
});
