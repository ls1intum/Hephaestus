import {
	createMemoryHistory,
	createRootRoute,
	createRoute,
	createRouter,
	RouterProvider,
} from "@tanstack/react-router";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode } from "react";
import { expect, it, vi } from "vitest";

import { ROUTER_SEARCH } from "@/lib/router-search";
import { RouteError } from "./RouteError";

it("runs a failed route again on retry and shows the page once it renders", async () => {
	const error = new Error("failed route with private detail");
	const renderPage = vi
		.fn(() => <h1>Recovered page</h1>)
		.mockImplementation(() => {
			throw error;
		});
	const onCaughtError = vi.fn();
	const rootRoute = createRootRoute();
	const route = createRoute({
		getParentRoute: () => rootRoute,
		path: "/",
		component: renderPage,
	});
	const router = createRouter({
		...ROUTER_SEARCH,
		routeTree: rootRoute.addChildren([route]),
		history: createMemoryHistory({ initialEntries: ["/"] }),
		defaultErrorComponent: RouteError,
	});

	render(
		<StrictMode>
			<RouterProvider router={router} />
		</StrictMode>,
		{ onCaughtError },
	);

	const alert = await screen.findByRole("alert");
	expect(alert.textContent).toContain("We could not load this page");
	// The page names no error detail: a message can carry data the reader should not see.
	expect(document.body.textContent).not.toContain(error.message);

	// Retry runs the route again: a failure that persists is caught again, and the page still offers
	// a retry.
	const caughtBeforeRetry = onCaughtError.mock.calls.length;
	await userEvent.click(screen.getByRole("button", { name: "Retry" }));
	await vi.waitFor(() =>
		expect(onCaughtError.mock.calls.length).toBeGreaterThan(caughtBeforeRetry),
	);
	expect(screen.getByRole("alert").textContent).toContain("We could not load this page");

	renderPage.mockReturnValue(<h1>Recovered page</h1>);
	await userEvent.click(screen.getByRole("button", { name: "Retry" }));
	await screen.findByRole("heading", { name: "Recovered page" });
	expect(screen.queryByRole("alert")).toBeNull();
	for (const [caught] of onCaughtError.mock.calls) {
		expect(caught).toBe(error);
	}
});
