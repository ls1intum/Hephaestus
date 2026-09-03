import {
	createMemoryHistory,
	createRootRoute,
	createRoute,
	createRouter,
	Outlet,
	RouterProvider,
} from "@tanstack/react-router";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { WorkspaceListItem } from "@/api/types.gen";

import { WorkspaceRouteGuard } from "./-WorkspaceRouteGuard";

const { useQuery } = vi.hoisted(() => ({ useQuery: vi.fn() }));

vi.mock("@tanstack/react-query", async (importOriginal) => ({
	...(await importOriginal()),
	useQuery,
}));

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

function renderRoute() {
	const rootRoute = createRootRoute({
		component: () => (
			<WorkspaceRouteGuard workspaceSlug="unavailable">
				<Outlet />
			</WorkspaceRouteGuard>
		),
	});
	const indexRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: "/",
		component: () => <p>Root</p>,
	});
	const workspaceRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: "w/$workspaceSlug",
		component: Outlet,
	});
	const threadRoute = createRoute({
		getParentRoute: () => workspaceRoute,
		path: "mentor/$threadId",
		component: () => <p>Thread</p>,
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
		useQuery.mockReturnValue({
			data: [workspace("available")],
			error: null,
			isFetching: false,
			isPending: false,
		});
		const router = renderRoute();

		await waitFor(() => expect(router.state.location.href).toBe("/w/available"));
	});

	it("returns to the root when no workspace is accessible", async () => {
		useQuery.mockReturnValue({ data: [], error: null, isFetching: false, isPending: false });
		const router = renderRoute();

		await waitFor(() => expect(router.state.location.href).toBe("/"));
	});

	it("waits for a fresh workspace list before redirecting", async () => {
		useQuery.mockReturnValue({ data: [], error: null, isFetching: true, isPending: false });
		const router = renderRoute();

		await screen.findByRole("status", { name: "Loading workspace" });
		expect(router.state.location.href).toBe("/w/unavailable/mentor/thread-1?message=stale");
	});

	it("keeps rendering when the workspace list cannot be refreshed", async () => {
		useQuery.mockReturnValue({
			data: undefined,
			error: new Error("Unavailable"),
			isFetching: false,
			isPending: false,
		});
		const router = renderRoute();

		await screen.findByText("Thread");
		expect(router.state.location.href).toBe("/w/unavailable/mentor/thread-1?message=stale");
	});

	it("renders an accessible workspace route", async () => {
		useQuery.mockReturnValue({
			data: [workspace("unavailable")],
			error: null,
			isFetching: true,
			isPending: false,
		});
		const router = renderRoute();

		await screen.findByText("Thread");
		expect(router.state.location.href).toBe("/w/unavailable/mentor/thread-1?message=stale");
	});
});
