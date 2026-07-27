import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRouter, RouterProvider } from "@tanstack/react-router";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { AuthProvider } from "@/integrations/auth/AuthContext";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";

// Mounting the real route pulls in the whole admin layout and its lazy modules.
vi.setConfig({ testTimeout: 15_000 });

/**
 * The first mount in a file pays the lazy transform of the whole admin layout and its route
 * modules — seconds under a loaded box, well past `findBy`'s own 1s default, which is separate
 * from the `testTimeout` above and is what actually decides these.
 */
const TRANSFORM_WAIT = { timeout: 10_000 };

const REPORT = {
	month: "2026-07",
	ownProviderMonthlyBudgetUsd: 10,
	instanceTotalCostUsd: 0,
	ownProviderTotalCostUsd: 1.5,
	instanceBudgetVerdict: "WITHIN",
	ownProviderBudgetVerdict: "WITHIN",
	instancePaused: false,
	ownProviderPaused: false,
	unpricedEventCount: 0,
	byJobType: [],
	byDay: [],
};

const REJECTION = "A cap above $1,000,000 is not accepted.";

const rejected = () =>
	HttpResponse.json(
		{ status: 400, title: "Bad Request", detail: REJECTION },
		{ status: 400, headers: { "Content-Type": "application/problem+json" } },
	);

function mockUsageRoute(onPutBudget: () => Promise<Response> | Response = rejected) {
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		http.get("*/workspaces/:workspaceSlug/llm/usage", () => HttpResponse.json(REPORT)),
		http.put("*/workspaces/:workspaceSlug/llm/budget", () => onPutBudget()),
	);
}

async function renderUsageRoute(onPutBudget?: () => Promise<Response> | Response) {
	mockUsageRoute(onPutBudget);
	// One client for the guards and the provider, exactly as `main.tsx` wires it.
	const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
	const router = createRouter({
		routeTree,
		history: createMemoryHistory({ initialEntries: ["/w/acme/admin/usage"] }),
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
	await screen.findByRole("heading", { name: "AI usage" }, TRANSFORM_WAIT);
	// The report arrives after the first paint; until it does the page renders skeletons.
	return screen.findByRole("button", { name: "Change cap" }, TRANSFORM_WAIT);
}

const capField = () => screen.getByLabelText(/Monthly cap/i);

describe("workspace AI usage route", () => {
	/**
	 * The dialog body is keyed and remounts on every open with a blank `dismissedServerError`, so a
	 * rejection left on the mutation reappears against a field the admin has not typed in yet —
	 * attached to an amount that is no longer on screen.
	 */
	it("does not re-show a dismissed rejection when the cap dialog is reopened", async () => {
		const changeCap = await renderUsageRoute();

		fireEvent.click(changeCap);
		fireEvent.change(capField(), { target: { value: "999999999" } });
		fireEvent.click(screen.getByRole("button", { name: "Save cap" }));
		await screen.findByText(REJECTION);

		fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
		await waitFor(() => expect(screen.queryByText(REJECTION)).toBeNull());

		fireEvent.click(screen.getByRole("button", { name: "Change cap" }));

		await screen.findByRole("button", { name: "Save cap" });
		expect(screen.queryByText(REJECTION)).toBeNull();
		expect(capField().getAttribute("aria-invalid")).toBe("false");
	});

	/**
	 * A money setting that fails must never fail quietly.
	 *
	 * The rejection above is rendered inline, against the amount that earned it. But the dialog stays
	 * dismissible while the PUT is in flight — deliberately, since `fetch` has no timeout of its own —
	 * and dismissing it resets the mutation. The failure then has no field left to land in: without a
	 * toast the page keeps showing the old cap and the admin walks away believing the new one is in
	 * force.
	 */
	it("says so out loud when a cap write fails after the dialog was dismissed", async () => {
		let releaseCapPut: (() => void) | undefined;
		const slowPut = new Promise<void>((resolve) => {
			releaseCapPut = resolve;
		});
		const changeCap = await renderUsageRoute(async () => {
			await slowPut;
			return rejected();
		});

		fireEvent.click(changeCap);
		fireEvent.change(capField(), { target: { value: "999999999" } });
		fireEvent.click(screen.getByRole("button", { name: "Save cap" }));

		// Escape while the write is still out: the dialog closes and the mutation is reset.
		fireEvent.keyDown(await screen.findByRole("dialog"), { key: "Escape" });
		await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

		releaseCapPut?.();

		await screen.findByText("Couldn't save the cap");
		expect(screen.getByText(REJECTION)).toBeTruthy();
	});

	/**
	 * The other half of the same rule: while the field is still on screen the inline error is the whole
	 * report, and a toast beside it would state one rejection twice.
	 */
	it("reports a rejection inline, and only inline, while the dialog is open", async () => {
		const changeCap = await renderUsageRoute();

		fireEvent.click(changeCap);
		fireEvent.change(capField(), { target: { value: "999999999" } });
		fireEvent.click(screen.getByRole("button", { name: "Save cap" }));

		await screen.findByText(REJECTION);
		expect(screen.queryByText("Couldn't save the cap")).toBeNull();
	});
});
