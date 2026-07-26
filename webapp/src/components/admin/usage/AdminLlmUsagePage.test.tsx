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
	ownProviderMonthlyBudgetUsd: 10,
	instanceTotalCostUsd: 4.25,
	ownProviderTotalCostUsd: 1.75,
	instanceBudgetVerdict: "WITHIN",
	ownProviderBudgetVerdict: "WITHIN",
	instancePaused: false,
	ownProviderPaused: false,
	unpricedEventCount: 2,
	byJobType: [
		{
			jobType: "PULL_REQUEST_REVIEW",
			instanceTotalCostUsd: 4.25,
			ownProviderTotalCostUsd: 1.75,
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
			instanceTotalCostUsd: 4.25,
			ownProviderTotalCostUsd: 1.75,
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
				canGoNext={false}
				workspaceSlug="acme"
				report={report}
				isLoading={false}
				error={null}
				onPrevMonth={() => {}}
				onNextMonth={() => {}}
				onEditOwnProviderCap={() => {}}
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
		expect(screen.getByText("Billed directly to you by your provider.")).toBeTruthy();

		const byJobType = screen.getByRole("table", { name: "AI spend by run type" });
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

		expect(screen.getByText("2 runs aren't counted in these totals")).toBeTruthy();
		expect(
			screen.getByText(/Add prices for your own models in .*; for shared models, ask your host\./),
		).toBeTruthy();
	});

	it("shows the per-run average alongside the untouched funding columns", async () => {
		await renderPage();

		const byJobType = screen.getByRole("table", { name: "AI spend by run type" });
		expect(within(byJobType).getByRole("columnheader", { name: "Avg per run" })).toBeTruthy();
		// 5 events: $4.25 shared and $1.75 own, each averaged on its own — never summed. No "≈": on
		// this page that glyph means "converted currency", and the column header already says "Avg".
		expect(within(byJobType).getByText("$0.85")).toBeTruthy();
		expect(within(byJobType).getByText("shared models")).toBeTruthy();
		expect(within(byJobType).getByText("$0.35")).toBeTruthy();
		expect(within(byJobType).getByText("your provider")).toBeTruthy();
	});

	describe("pause banners", () => {
		/**
		 * Whose money it is decides who is asked to act: the workspace can lift its own cap and price
		 * its own models, but a shared-model budget is the host's to raise and the host's to price.
		 */
		it.each<[string, Partial<WorkspaceLlmUsageReport>, string, string, string | undefined]>([
			[
				"a reached provider cap",
				{ ownProviderBudgetVerdict: "EXHAUSTED", ownProviderTotalCostUsd: 10 },
				"Your provider cap is reached",
				"Paused until August 1 (UTC), or until you raise or remove the cap.",
				undefined,
			],
			[
				"an unenforceable provider cap",
				{ ownProviderBudgetVerdict: "UNVERIFIABLE" },
				"Your provider cap can't be enforced",
				"2 runs on your models have no price, so the cap can't be checked and your provider is paused. Add a price to resume, or remove the cap.",
				"/w/acme/admin/models",
			],
			[
				"an exhausted shared budget",
				{ instanceBudgetVerdict: "EXHAUSTED", instanceTotalCostUsd: 25 },
				"Shared-model budget reached",
				"Paused until August 1 (UTC), or until your host raises the budget. Practice detection and Mentor can keep running on your own models.",
				"/w/acme/admin/models",
			],
			[
				"an unverifiable shared budget",
				{ instanceBudgetVerdict: "UNVERIFIABLE" },
				"Shared-model spend can't be verified",
				"2 shared-model runs have no price, so the budget can't be checked and shared models are paused. Only your host can price them.",
				undefined,
			],
		])("explains %s and who can clear it", async (_name, patch, title, body, href) => {
			const paused =
				patch.instanceBudgetVerdict != null
					? { instancePaused: true }
					: { ownProviderPaused: true };
			await renderPage({ ...baseReport, ...paused, ...patch });

			const banner = screen.getByText(title).closest("[role='alert']") as HTMLElement;
			expect(banner).toBeTruthy();
			expect(within(banner).getByText(body)).toBeTruthy();
			if (href != null) {
				expect(
					within(banner).getByRole("link", { name: "Open AI models" }).getAttribute("href"),
				).toBe(href);
			}
		});

		it("puts the cap editor inside the banner about the cap that stopped the work", async () => {
			// The lever has to be in the banner that names the pause; on this page the only other
			// route to it is the provider card further down, past the fold on a phone.
			const onEditOwnProviderCap = vi.fn();
			await renderPage(
				{
					...baseReport,
					ownProviderPaused: true,
					ownProviderBudgetVerdict: "EXHAUSTED",
					ownProviderTotalCostUsd: 10,
				},
				{ onEditOwnProviderCap },
			);

			const banner = screen.getByText("Your provider cap is reached").closest("[role='alert']");
			expect(banner).toBeTruthy();
			const adjust = within(banner as HTMLElement).getByRole("button", { name: "Adjust cap" });

			fireEvent.click(adjust);

			// A workspace admin can lift their own cap, so the control is a button they press here —
			// not a link away to whoever owns the other purse.
			expect(onEditOwnProviderCap).toHaveBeenCalledTimes(1);
			expect(adjust.tagName).toBe("BUTTON");
		});

		it("puts the cap they can act on first when both are paused", async () => {
			await renderPage({
				...baseReport,
				ownProviderPaused: true,
				ownProviderBudgetVerdict: "EXHAUSTED",
				instancePaused: true,
				instanceBudgetVerdict: "EXHAUSTED",
			});

			const alerts = screen.getAllByRole("alert");
			expect(alerts[0].textContent).toContain("Your provider cap is reached");
			expect(alerts[1].textContent).toContain("Shared-model budget reached");
		});

		it("shows no pause banner for a past month", async () => {
			await renderPage(
				{ ...baseReport, ownProviderPaused: true, ownProviderBudgetVerdict: "EXHAUSTED" },
				{ month: "2026-06", isCurrentMonth: false },
			);

			expect(screen.queryByText("Your provider cap is reached")).toBeNull();
		});
	});

	describe("approaching a cap", () => {
		/** The warning is a status, not an alert: nothing has happened yet, and nothing is blocked. */
		it.each<
			[string, Partial<WorkspaceLlmUsageReport>, Date | undefined, string | null, RegExp | null]
		>([
			[
				"warns at 80% with the date the pace reaches the cap",
				{ ownProviderTotalCostUsd: 8.4 },
				undefined,
				"You've used 84% of your provider cap",
				/At this pace you'll hit it around July 12\./,
			],
			[
				"keeps the warning but withholds a projection the month is too young to support",
				{ ownProviderTotalCostUsd: 8.4 },
				new Date("2026-07-02T12:00:00.000Z"),
				"You've used 84% of your provider cap",
				null,
			],
			["stays quiet below the threshold", {}, undefined, null, null],
			[
				"says nothing about a cap that is already paused, which the banner covers",
				{
					ownProviderTotalCostUsd: 10,
					ownProviderPaused: true,
					ownProviderBudgetVerdict: "EXHAUSTED",
				},
				undefined,
				null,
				null,
			],
		])("%s", async (_name, patch, now, warning, projection) => {
			await renderPage({ ...baseReport, ...patch }, now != null ? { now } : {});

			if (warning == null) {
				expect(screen.queryByText(/You've used/)).toBeNull();
			} else {
				expect(screen.getByText(warning).closest("[role='status']")).toBeTruthy();
			}
			if (projection == null) {
				expect(screen.queryByText(/At this pace/)).toBeNull();
			} else {
				expect(screen.getByText(projection)).toBeTruthy();
			}
		});
	});

	describe("provider card", () => {
		it.each<[string, Partial<WorkspaceLlmUsageReport>, string | RegExp]>([
			[
				"nothing has run on it",
				{ ownProviderTotalCostUsd: 0, byJobType: [], byDay: [] },
				/Nothing ran on your own provider this month\./,
			],
			["there is uncapped spend", {}, "No cap set."],
		])("offers a cap when %s", async (_name, patch, copy) => {
			const onEditOwnProviderCap = vi.fn();
			await renderPage(
				{ ...baseReport, ownProviderMonthlyBudgetUsd: undefined, ...patch },
				{ onEditOwnProviderCap },
			);

			expect(screen.getByText(copy)).toBeTruthy();
			fireEvent.click(screen.getByRole("button", { name: "Set cap" }));
			expect(onEditOwnProviderCap).toHaveBeenCalled();
		});

		it.each<[string, Partial<WorkspaceLlmUsageReport>, string]>([
			["a cap in force", {}, "Change cap"],
			["no cap yet", { ownProviderMonthlyBudgetUsd: undefined }, "Set cap"],
		])("withdraws the editor on a closed month, with %s", async (_name, patch, label) => {
			await renderPage(
				{ ...baseReport, month: "2026-06", ...patch },
				{ month: "2026-06", isCurrentMonth: false },
			);

			// A cap is not month-scoped: saved from June's view it changes what runs today. Offering it
			// here would also put "≈ €44 at today's rate" under the amount field while the caption below
			// says the month's rate is frozen — two sentences on one screen contradicting each other.
			expect(screen.queryByRole("button", { name: label })).toBeNull();
			// And the sentence that replaces the button has to point at the way back: the reader wanted
			// to change a cap, and "not here" alone leaves them looking for where.
			expect(
				screen.getByText(
					"A cap applies from the moment it is saved, not to the month you are reading. Step forward to this month to change it.",
				),
			).toBeTruthy();
		});
	});

	describe("display currency", () => {
		const eur: FxRateInfo = {
			currencyCode: "EUR",
			ratePerUsd: 0.878966,
			rateDate: new Date("2026-07-24T00:00:00.000Z"),
			source: "ECB",
		};

		/** Two days, so the breakdown table earns a footer — and the footer converts its total. */
		const twoDays: WorkspaceLlmUsageReport["byDay"] = [
			{
				day: new Date("2026-07-05T00:00:00.000Z"),
				instanceTotalCostUsd: 6.2,
				ownProviderTotalCostUsd: 0,
				unpricedEventCount: 0,
				events: 2,
			},
			{
				day: new Date("2026-07-06T00:00:00.000Z"),
				instanceTotalCostUsd: 6.2,
				ownProviderTotalCostUsd: 0,
				unpricedEventCount: 0,
				events: 2,
			},
		];

		/**
		 * A $0 shared budget — the supported "pause now" state — converts to nothing, and with no
		 * provider side no card converts at all. The table footers convert independently, so the
		 * caption must follow *them* too: otherwise "≈ €10.90" sits on screen with no rate behind it.
		 */
		it.each<[string, WorkspaceLlmUsageReport["byDay"], number, string | null]>([
			["only the table footers convert", twoDays, 12.4, "≈ €10.90"],
			["nothing on the page converted", [], 0, null],
		])("discloses the rate when %s", async (_name, byDay, instanceTotalCostUsd, footerText) => {
			await renderPage({
				...baseReport,
				instanceMonthlyBudgetUsd: 0,
				ownProviderMonthlyBudgetUsd: undefined,
				instanceTotalCostUsd,
				ownProviderTotalCostUsd: 0,
				unpricedEventCount: 0,
				byJobType: [],
				byDay,
				fx: eur,
			});

			if (footerText == null) {
				expect(screen.queryByText(/reference rate published on/)).toBeNull();
				return;
			}
			const table = screen.getByRole("table", { name: "AI spend by day" });
			expect(within(table).getByRole("row", { name: /^Total/ }).textContent).toContain(footerText);
			expect(
				screen.getByText(
					/EUR amounts are estimates at the European Central Bank reference rate published on Jul 24, 2026/,
				),
			).toBeTruthy();
		});

		it("stays silent under a cap that is set but converted nowhere on the page", async () => {
			// The first days of a month: a provider cap exists, and no spend has reached the whole unit
			// the estimates round to, so nothing on screen is in euros. A conversion computed for the
			// cap and rendered nowhere used to count towards the caption, putting "EUR amounts are
			// estimates…" under a page with no estimates on it.
			await renderPage({
				...baseReport,
				instanceMonthlyBudgetUsd: undefined,
				ownProviderMonthlyBudgetUsd: 50,
				instanceTotalCostUsd: 0,
				ownProviderTotalCostUsd: 0,
				unpricedEventCount: 0,
				byJobType: [],
				byDay: [],
				fx: eur,
			});

			expect(screen.queryByText(/≈ €/)).toBeNull();
			expect(screen.queryByText(/reference rate published on/)).toBeNull();
		});

		it("converts the projected month-end figure in the same breath as the spend it follows", async () => {
			await renderPage(
				{
					...baseReport,
					instanceMonthlyBudgetUsd: 50,
					instanceTotalCostUsd: 43.9,
					ownProviderMonthlyBudgetUsd: undefined,
					ownProviderTotalCostUsd: 0,
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
