import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRouter, RouterProvider } from "@tanstack/react-router";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import {
	computeUserLeagueStatsQueryKey,
	getLeaderboardQueryKey,
} from "@/api/@tanstack/react-query.gen";
import { AuthProvider } from "@/integrations/auth/AuthContext";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";

// Mounting the real route pulls in the whole admin layout and its lazy modules.
vi.setConfig({ testTimeout: 15_000 });

const WORKSPACE = {
	id: 1,
	workspaceSlug: "acme",
	displayName: "Acme",
	providerType: "GITHUB",
	status: "ACTIVE",
	leaguesEnabled: true,
	leaderboardEnabled: true,
	practicesEnabled: false,
	mentorEnabled: false,
	achievementsEnabled: false,
	progressionEnabled: false,
	practiceReviewAutoTriggerEnabled: true,
	practiceReviewManualTriggerEnabled: true,
};

/** The exact key a mounted leaderboard holds: one workspace, one time range, one mode. */
const LEADERBOARD_KEY = getLeaderboardQueryKey({
	path: { workspaceSlug: "acme" },
	query: {
		after: new Date("2026-07-01T00:00:00.000Z"),
		before: new Date("2026-07-31T00:00:00.000Z"),
		team: "all",
		sort: "LEAGUE_POINTS",
		mode: "INDIVIDUAL",
	},
});

const LEAGUE_STATS_KEY = computeUserLeagueStatsQueryKey({
	path: { workspaceSlug: "acme", login: "ada" },
	query: {
		after: new Date("2026-07-01T00:00:00.000Z"),
		before: new Date("2026-07-31T00:00:00.000Z"),
	},
});

async function renderSettingsRoute() {
	let resetCalls = 0;
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		http.get("*/workspaces", () => HttpResponse.json([WORKSPACE])),
		http.get("*/workspaces/:workspaceSlug", () => HttpResponse.json(WORKSPACE)),
		http.put("*/workspaces/:workspaceSlug/league/reset", () => {
			resetCalls += 1;
			return new HttpResponse(null, { status: 204 });
		}),
	);

	const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
	// Two already-fetched league surfaces, the way they sit in the cache when an admin opens settings
	// in another tab. `staleTime: Infinity` is what makes the assertion mean something: only an
	// invalidation can mark these stale.
	for (const queryKey of [LEADERBOARD_KEY, LEAGUE_STATS_KEY]) {
		queryClient.setQueryData(queryKey, []);
		queryClient.setQueryDefaults(queryKey, { staleTime: Number.POSITIVE_INFINITY });
	}

	const router = createRouter({
		routeTree,
		history: createMemoryHistory({ initialEntries: ["/w/acme/admin/settings"] }),
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
	await screen.findByRole("button", { name: "Reset and Recalculate Leagues" });
	return { queryClient, resetCalls: () => resetCalls };
}

describe("workspace settings route", () => {
	/**
	 * A reset re-derives every standing on the server, so the leaderboard and the league stats beside
	 * it are stale the moment it returns. Invalidating a hand-typed `["workspace"]` is not the shape
	 * the generated helpers produce and matches no query at all: the button does its work on the
	 * server and the screen keeps showing the old table.
	 *
	 * A cache-contract assertion, deliberately. The honest form — count the requests the refetch makes
	 * — is not available here: neither the leaderboard nor the league-stats surface is mounted on the
	 * settings route, so an invalidation correctly fires no request at all. What can be checked is
	 * that the two keys the mounted surfaces hold are the two keys this route marks stale, which is
	 * exactly what the hand-typed key got wrong.
	 */
	it("marks the leaderboard and league stats stale after a reset", async () => {
		const { queryClient, resetCalls } = await renderSettingsRoute();

		expect(queryClient.getQueryState(LEADERBOARD_KEY)?.isInvalidated).toBe(false);

		fireEvent.click(screen.getByRole("button", { name: "Reset and Recalculate Leagues" }));
		const dialog = await screen.findByRole("alertdialog");
		fireEvent.click(await within(dialog).findByRole("button", { name: "Reset and Recalculate" }));

		await waitFor(() => expect(resetCalls()).toBe(1));
		await waitFor(() =>
			expect(queryClient.getQueryState(LEADERBOARD_KEY)?.isInvalidated).toBe(true),
		);
		expect(queryClient.getQueryState(LEAGUE_STATS_KEY)?.isInvalidated).toBe(true);
	});
});
