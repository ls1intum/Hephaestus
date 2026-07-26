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

function mockUsageRoute() {
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		http.get("*/workspaces/:workspaceSlug/llm/usage", () => HttpResponse.json(REPORT)),
		http.put("*/workspaces/:workspaceSlug/llm/budget", () =>
			HttpResponse.json(
				{ status: 400, title: "Bad Request", detail: REJECTION },
				{ status: 400, headers: { "Content-Type": "application/problem+json" } },
			),
		),
	);
}

async function renderUsageRoute() {
	mockUsageRoute();
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
	await screen.findByRole("heading", { name: "AI usage" });
	// The report arrives after the first paint; until it does the page renders skeletons.
	return screen.findByRole("button", { name: "Change cap" });
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
});
