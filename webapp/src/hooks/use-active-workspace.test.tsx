import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
	createMemoryHistory,
	createRootRoute,
	createRoute,
	createRouter,
	RouterProvider,
} from "@tanstack/react-router";
import { act, render, screen } from "@testing-library/react";
import { describe, it, vi } from "vitest";

import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import type { WorkspaceListItem } from "@/api/types.gen";

import { useActiveWorkspaceSlug } from "./use-active-workspace";

vi.mock("@/integrations/auth/AuthContext", () => ({
	useAuth: () => ({ isAuthenticated: true, isLoading: false }),
}));

function workspace(
	workspaceSlug: string,
	providerType: WorkspaceListItem["providerType"],
): WorkspaceListItem {
	return {
		id: 1,
		workspaceSlug,
		providerType,
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

function ActiveWorkspace() {
	const { workspaceSlug, providerType } = useActiveWorkspaceSlug();
	return <output>{`${workspaceSlug}|${providerType}`}</output>;
}

describe("useActiveWorkspaceSlug", () => {
	it("derives the active workspace and provider from the route", async () => {
		const queryClient = new QueryClient();
		queryClient.setQueryData(listWorkspacesOptions().queryKey, [
			workspace("alpha", "GITHUB"),
			workspace("beta", "GITLAB"),
		]);
		const rootRoute = createRootRoute();
		const workspaceRoute = createRoute({
			getParentRoute: () => rootRoute,
			path: "w/$workspaceSlug",
			component: ActiveWorkspace,
		});
		const router = createRouter({
			routeTree: rootRoute.addChildren([workspaceRoute]),
			history: createMemoryHistory({ initialEntries: ["/w/beta"] }),
		});

		render(
			<QueryClientProvider client={queryClient}>
				<RouterProvider router={router} />
			</QueryClientProvider>,
		);

		await screen.findByText("beta|GITLAB");
		await act(() =>
			router.navigate({ to: "/w/$workspaceSlug", params: { workspaceSlug: "alpha" } }),
		);
		await screen.findByText("alpha|GITHUB");
	});
});
