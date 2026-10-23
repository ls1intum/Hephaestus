import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
	createMemoryHistory,
	createRootRoute,
	createRoute,
	createRouter,
	RouterProvider,
} from "@tanstack/react-router";
import { act, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import { ROUTER_SEARCH } from "@/lib/router-search";
import { workspaceListItem } from "@/mocks/fixtures/workspaces";
import { Route as rootDefinition } from "@/routes/__root";
import { QUERY_STALE_TIME_MS } from "@/runtime/tanstack-query/query-defaults";

import { useActiveWorkspaceSlug } from "./use-active-workspace";

const authState = vi.hoisted(() => ({ isAuthenticated: true, isLoading: false }));

vi.mock("@/runtime/auth/AuthContext", () => ({ useAuth: () => authState }));

function ActiveWorkspace() {
	const { workspaceSlug, chromeWorkspaceSlug, chromeWorkspace, providerType } =
		useActiveWorkspaceSlug();
	return (
		<output>
			{[workspaceSlug, chromeWorkspaceSlug, chromeWorkspace?.workspaceSlug, providerType]
				.map(String)
				.join("|")}
		</output>
	);
}

function renderAt(initialEntry: string, includeBeta = true) {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { staleTime: QUERY_STALE_TIME_MS } },
	});
	queryClient.setQueryData(listWorkspacesOptions().queryKey, [
		workspaceListItem("alpha"),
		...(includeBeta ? [workspaceListItem("beta", { providerType: "GITLAB" })] : []),
	]);
	const rootRoute = createRootRoute({ validateSearch: rootDefinition.options.validateSearch });
	const workspaceRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: "w/$workspaceSlug",
		component: ActiveWorkspace,
	});
	const settingsRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: "settings",
		component: ActiveWorkspace,
	});
	const router = createRouter({
		...ROUTER_SEARCH,
		routeTree: rootRoute.addChildren([workspaceRoute, settingsRoute]),
		history: createMemoryHistory({ initialEntries: [initialEntry] }),
	});

	render(
		<QueryClientProvider client={queryClient}>
			<RouterProvider router={router} />
		</QueryClientProvider>,
	);
	return router;
}

describe("useActiveWorkspaceSlug", () => {
	beforeEach(() => {
		authState.isAuthenticated = true;
		authState.isLoading = false;
	});
	it("derives the active workspace and provider from the route", async () => {
		const router = renderAt("/w/beta");

		await screen.findByText("beta|beta|beta|GITLAB");
		await act(async () =>
			router.navigate({ to: "/w/$workspaceSlug", params: { workspaceSlug: "alpha" } }),
		);
		await screen.findByText("alpha|alpha|alpha|GITHUB");
	});

	it("leaves the route slug undefined on a route that names no workspace, and the chrome on the first", async () => {
		renderAt("/settings");

		await screen.findByText("undefined|alpha|alpha|GITHUB");
	});

	it("reads a route slug the account has no part in as no workspace, and substitutes none", async () => {
		renderAt("/w/missing");

		await screen.findByText("undefined|undefined|undefined|GITHUB");
	});
	it("uses the eligible chrome query on a global route without turning it into a route slug", async () => {
		renderAt("/settings?chromeWorkspace=beta");
		await screen.findByText("undefined|beta|beta|GITLAB");
	});

	it("lets the path workspace win over a conflicting chrome query", async () => {
		renderAt("/w/alpha?chromeWorkspace=beta");
		await screen.findByText("alpha|alpha|alpha|GITHUB");
	});

	it.each(["missing", "beta"])(
		"does not select an unknown or removed membership from context %s",
		async (context) => {
			renderAt(`/settings?chromeWorkspace=${context}`, false);
			await screen.findByText("undefined|undefined|undefined|GITHUB");
		},
	);

	it.each(["", "%20", "beta&chromeWorkspace=alpha", "%7B%22slug%22%3A%22beta%22%7D"])(
		"ignores malformed chrome context %s",
		async (context) => {
			renderAt(`/settings?chromeWorkspace=${context}`);
			await screen.findByText("undefined|alpha|alpha|GITHUB");
		},
	);

	it("keeps an unlisted public workspace a visitor despite a valid member chrome query", async () => {
		renderAt("/w/foreign?chromeWorkspace=beta");
		await screen.findByText("undefined|undefined|undefined|GITHUB");
	});

	it("does not fetch the workspace list for an anonymous account", async () => {
		authState.isAuthenticated = false;
		const queryClient = new QueryClient();
		const rootRoute = createRootRoute({ validateSearch: rootDefinition.options.validateSearch });
		const settings = createRoute({
			getParentRoute: () => rootRoute,
			path: "settings",
			component: ActiveWorkspace,
		});
		const router = createRouter({
			...ROUTER_SEARCH,
			routeTree: rootRoute.addChildren([settings]),
			history: createMemoryHistory({ initialEntries: ["/settings?chromeWorkspace=beta"] }),
		});
		render(
			<QueryClientProvider client={queryClient}>
				<RouterProvider router={router} />
			</QueryClientProvider>,
		);
		await screen.findByText("undefined|undefined|undefined|GITHUB");
		expect(queryClient.getQueryState(listWorkspacesOptions().queryKey)?.fetchStatus).toBe("idle");
	});
});
