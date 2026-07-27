import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import {
	computeUserLeagueStatsQueryKey,
	getLeaderboardQueryKey,
} from "@/api/@tanstack/react-query.gen";
import { server } from "@/mocks/server";
import { renderRouteAt, TRANSFORM_WAIT, testQueryClient } from "@/test/router-harness";

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

	const queryClient = testQueryClient();
	// Two already-fetched league surfaces, as they sit in the cache when an admin opens settings in
	// another tab. `staleTime: Infinity` leaves invalidation as the only thing that can mark them stale.
	for (const queryKey of [LEADERBOARD_KEY, LEAGUE_STATS_KEY]) {
		queryClient.setQueryData(queryKey, []);
		queryClient.setQueryDefaults(queryKey, { staleTime: Number.POSITIVE_INFINITY });
	}

	renderRouteAt("/w/acme/admin/settings", queryClient);
	await screen.findByRole("button", { name: "Reset and Recalculate Leagues" }, TRANSFORM_WAIT);
	return { queryClient, resetCalls: () => resetCalls };
}

describe("workspace settings route", () => {
	/**
	 * A hand-typed `["workspace"]` is not the shape the generated helpers produce and matches no query
	 * at all, so the reset lands on the server and the screen keeps the old table.
	 *
	 * Asserted against the cache rather than by counting refetch requests, because neither league
	 * surface is mounted on the settings route: a correct invalidation here fires no request at all.
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
