import {
	createMemoryHistory,
	createRootRoute,
	createRoute,
	createRouter,
	Outlet,
	RouterProvider,
} from "@tanstack/react-router";
import { render, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { WorkspaceListItem } from "@/api/types.gen";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

import { WorkspaceRouteGuard } from "./-WorkspaceRouteGuard";

vi.mock("@/hooks/use-active-workspace");

function workspace(workspaceSlug: string): WorkspaceListItem {
	return {
		id: 1,
		workspaceSlug,
		accountLogin: workspaceSlug,
		displayName: workspaceSlug,
		createdAt: new Date("2026-01-01T00:00:00Z"),
		status: "ACTIVE",
		achievementsEnabled: false,
		leaderboardEnabled: false,
		leaguesEnabled: false,
		mentorEnabled: false,
		practicesEnabled: false,
		progressionEnabled: false,
	};
}

function renderRoute(workspaces: WorkspaceListItem[]) {
	vi.mocked(useActiveWorkspaceSlug).mockReturnValue({
		workspaceSlug: "unavailable",
		workspaces,
		providerType: "GITHUB",
		isLoading: false,
		workspacesLoaded: true,
		error: null,
	});

	const rootRoute = createRootRoute({
		component: () => (
			<WorkspaceRouteGuard>
				<Outlet />
			</WorkspaceRouteGuard>
		),
	});
	const indexRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: "/",
		component: () => null,
	});
	const workspaceRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: "w/$workspaceSlug",
		component: Outlet,
	});
	const threadRoute = createRoute({
		getParentRoute: () => workspaceRoute,
		path: "mentor/$threadId",
		component: () => null,
	});
	const router = createRouter({
		routeTree: rootRoute.addChildren([indexRoute, workspaceRoute.addChildren([threadRoute])]),
		history: createMemoryHistory({
			initialEntries: ["/w/unavailable/mentor/thread-1?message=stale"],
		}),
	});

	render(<RouterProvider router={router} />);
	return router;
}

describe("WorkspaceRouteGuard", () => {
	it("returns an inaccessible workspace route to an accessible workspace home", async () => {
		const router = renderRoute([workspace("available")]);

		await waitFor(() => expect(router.state.location.href).toBe("/w/available"));
	});

	it("returns to the root when no workspace is accessible", async () => {
		const router = renderRoute([]);

		await waitFor(() => expect(router.state.location.href).toBe("/"));
	});
});
