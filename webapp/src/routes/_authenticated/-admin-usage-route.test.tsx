import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRouter, RouterProvider } from "@tanstack/react-router";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import type { AdminWorkspaceLlmUsage, WorkspaceLlmUsageReport } from "@/api/types.gen";
import { currentMonthUtc, formatMonthLabel } from "@/components/admin/usage/usage-utils";
import { AuthProvider } from "@/integrations/auth/AuthContext";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";

// Mounting the real route pulls in the whole admin layout and its lazy modules.
vi.setConfig({ testTimeout: 20_000 });

/**
 * This month, because both surfaces under test are current-month-only: the "Set budget" button is not
 * rendered on a closed month, and a closed month is never "near cap" (`capState`).
 */
const MONTH = currentMonthUtc();

/** 86% of a $50 budget — over `BUDGET_WARN_PERCENT`, so the pace warning is on screen to read. */
const SPENT_USD = 43;

function row(budgetUsd: number | undefined): AdminWorkspaceLlmUsage {
	return {
		workspaceSlug: "acme",
		displayName: "Acme",
		events: 12,
		instanceTotalCostUsd: SPENT_USD,
		instanceMonthlyBudgetUsd: budgetUsd,
		instanceBudgetVerdict: "WITHIN",
		instancePaused: false,
		ownProviderTotalCostUsd: 0,
		ownProviderBudgetVerdict: "WITHIN",
		ownProviderPaused: false,
	};
}

function detail(budgetUsd: number | undefined): WorkspaceLlmUsageReport {
	return {
		month: MONTH,
		instanceTotalCostUsd: SPENT_USD,
		instanceMonthlyBudgetUsd: budgetUsd,
		instanceBudgetVerdict: "WITHIN",
		instancePaused: false,
		ownProviderTotalCostUsd: 0,
		ownProviderBudgetVerdict: "WITHIN",
		ownProviderPaused: false,
		unpricedEventCount: 0,
		byJobType: [],
		byDay: [],
	};
}

/** Every `month` the rollup was actually asked for, in order — see the clamp case below. */
const requestedMonths: string[] = [];

/**
 * Both reports read the same budget, exactly as the server does — the row and the expanded panel are
 * two views of one number, which is the whole point of the first case below.
 */
function mockUsageRoutes(options: {
	budgetUsd?: number;
	onPutBudget?: (budgetUsd: number | undefined) => Promise<Response> | Response;
}) {
	let budget = options.budgetUsd;
	requestedMonths.length = 0;
	const putBudget =
		options.onPutBudget ??
		(async (next: number | undefined) => {
			budget = next;
			return HttpResponse.json({ monthlyBudgetUsd: next ?? null });
		});
	server.use(
		http.get("*/admin/llm/usage", ({ request }) => {
			requestedMonths.push(new URL(request.url).searchParams.get("month") ?? "");
			return HttpResponse.json({ month: MONTH, workspaces: [row(budget)] });
		}),
		http.get("*/workspaces/:workspaceSlug/llm/usage", () => HttpResponse.json(detail(budget))),
		http.put("*/admin/workspaces/:workspaceSlug/llm/budget", async ({ request }) => {
			const body = (await request.json()) as { monthlyBudgetUsd?: number };
			return putBudget(body.monthlyBudgetUsd);
		}),
	);
}

async function renderUsageRoute(url = "/admin/usage") {
	// One client for the guards and the provider, exactly as `main.tsx` wires it.
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	const router = createRouter({
		routeTree,
		history: createMemoryHistory({ initialEntries: [url] }),
		context: { queryClient, auth: undefined },
	});
	render(
		<QueryClientProvider client={queryClient}>
			<AuthProvider>
				{/* biome-ignore lint/suspicious/noExplicitAny: the app's router context is wider than this test needs. */}
				<RouterProvider router={router as any} />
			</AuthProvider>
		</QueryClientProvider>,
	);
	// The first mount in this file pays the lazy transform of the whole admin layout — seconds, not
	// the 1s `findBy` default.
	await screen.findByRole("heading", { name: "AI usage" }, { timeout: 10_000 });
	// The report arrives after the first paint; until it does the table renders skeleton rows.
	return screen.findByRole("button", { name: /Set budget for Acme/ }, { timeout: 10_000 });
}

async function saveBudget(amount: string) {
	const dialog = await screen.findByRole("dialog");
	fireEvent.change(within(dialog).getByLabelText(/Monthly budget/i), { target: { value: amount } });
	fireEvent.click(within(dialog).getByRole("button", { name: "Save budget" }));
}

describe("instance AI usage route", () => {
	/**
	 * `?month=` is route state, so it is the router — not the page component — that decides what the
	 * page opens on. Mounting the real router is the only way to exercise that: `usageSearchSchema`
	 * clamps a future month back to this one, because there is no such thing as next month's spend and
	 * the stepper already refuses to walk past now. A link must not reach a state the UI cannot.
	 */
	it("clamps a future ?month= back to this month", async () => {
		mockUsageRoutes({ budgetUsd: 50 });
		await renderUsageRoute("/admin/usage?month=2999-01");

		expect(screen.getByText(formatMonthLabel(MONTH))).toBeTruthy();
		// And the report was asked for this month, not for the one in the URL.
		expect(requestedMonths).toEqual([MONTH]);
	});

	/**
	 * The row and the expanded Details panel are fed by two different endpoints — the admin rollup and
	 * the workspace's own report — and both print the same budget. Invalidating only the rollup leaves
	 * two contradictory caps for one workspace on one screen: the row says "$43.00 of $200" and the
	 * panel under it still warns that Acme has used 86% of a $50 budget. Nothing refetches the panel;
	 * it stays mounted, so only a window blur/focus round trip would correct it.
	 */
	it("refreshes the expanded panel's budget, not just the row's", async () => {
		mockUsageRoutes({ budgetUsd: 50 });
		await renderUsageRoute();

		fireEvent.click(screen.getByRole("button", { name: /View usage details for Acme/ }));
		// The panel's warning is the budget as the *workspace-scoped* report states it.
		await screen.findByText("Acme has used 86% of its shared-model budget");

		fireEvent.click(screen.getByRole("button", { name: /Set budget for Acme/ }));
		await saveBudget("200");

		// The row is the easy half — its own key was already invalidated.
		await screen.findByText("Budget saved. New calls resume within a minute.");
		// $43 of $200 is 22%, so the panel's near-cap warning has no reason to exist any more. While it
		// is still on screen the admin is reading a budget that was replaced.
		await waitFor(() =>
			expect(screen.queryByText(/Acme has used \d+% of its shared-model budget/)).toBeNull(),
		);
	});

	/**
	 * A money setting that fails must never fail quietly.
	 *
	 * The rejection is normally rendered inline, against the amount that earned it. But the dialog is
	 * dismissible while the PUT is in flight — deliberately, since `fetch` has no timeout of its own —
	 * and dismissing it resets the mutation. The failure then has no field left to land in: without a
	 * toast the table keeps showing the old budget and the admin walks away believing the new one is
	 * in force.
	 */
	it("says so out loud when a budget write fails after the dialog was dismissed", async () => {
		let releaseBudgetPut: (() => void) | undefined;
		const slowPut = new Promise<void>((resolve) => {
			releaseBudgetPut = resolve;
		});
		mockUsageRoutes({
			budgetUsd: 50,
			onPutBudget: async () => {
				await slowPut;
				return HttpResponse.json(
					{ status: 500, title: "Internal Server Error", detail: "The budget service is down." },
					{ status: 500, headers: { "Content-Type": "application/problem+json" } },
				);
			},
		});
		await renderUsageRoute();

		fireEvent.click(screen.getByRole("button", { name: /Set budget for Acme/ }));
		await saveBudget("200");

		// Escape while the write is still out: the dialog closes and the mutation is reset.
		fireEvent.keyDown(await screen.findByRole("dialog"), { key: "Escape" });
		await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

		releaseBudgetPut?.();

		await screen.findByText("Couldn't save the budget");
		expect(screen.getByText("The budget service is down.")).toBeTruthy();
		// And the table still says $50, which is the truth — the write did not land.
		expect(screen.getByRole("button", { name: /Set budget for Acme/ })).toBeTruthy();
	});

	/**
	 * The other half of the same rule: while the field is still on screen the inline error is the whole
	 * report, and a toast beside it would state one rejection twice.
	 */
	it("reports a rejection inline, and only inline, while the dialog is open", async () => {
		mockUsageRoutes({
			budgetUsd: 50,
			onPutBudget: () =>
				HttpResponse.json(
					{ status: 400, title: "Bad Request", detail: "A budget above $1,000,000 is refused." },
					{ status: 400, headers: { "Content-Type": "application/problem+json" } },
				),
		});
		await renderUsageRoute();

		fireEvent.click(screen.getByRole("button", { name: /Set budget for Acme/ }));
		await saveBudget("9999999");

		const dialog = await screen.findByRole("dialog");
		await within(dialog).findByText("A budget above $1,000,000 is refused.");
		// One report, not two: the toast region is empty.
		expect(screen.queryByText("Couldn't save the budget")).toBeNull();
	});
});
