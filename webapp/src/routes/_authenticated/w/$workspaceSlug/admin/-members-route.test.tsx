import { QueryClient } from "@tanstack/react-query";
import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { getAllTeamsQueryKey, getUsersWithTeamsQueryKey } from "@/api/@tanstack/react-query.gen";
import type { TeamInfo, UserTeams } from "@/api/types.gen";
import type { Wire } from "@/lib/dates";
import { workspaceListItem } from "@/mocks/fixtures/workspaces";
import { server } from "@/mocks/server";
import { QUERY_STALE_TIME_MS } from "@/runtime/tanstack-query/query-defaults";
import { deferred, pending } from "@/test/async";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

vi.setConfig({ testTimeout: 15_000 });

const WORKSPACE = {
	id: 1,
	workspaceSlug: "acme",
	displayName: "Acme",
	providerType: "GITHUB",
	status: "ACTIVE",
	practicesEnabled: true,
};

function mockMembersRoute() {
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		http.get("*/workspaces", () => HttpResponse.json([WORKSPACE])),
		http.get("*/workspaces/:workspaceSlug", () => HttpResponse.json(WORKSPACE)),
		http.get("*/workspaces/:workspaceSlug/connections/catalog", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/members", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/team", () => HttpResponse.json([])),
	);
}

const team = (id: number, name: string) =>
	({
		id,
		name,
		slug: name.toLowerCase().replaceAll(" ", "-"),
		hidden: false,
		labels: [],
		members: [],
		membershipCount: 0,
		repoPermissionCount: 0,
		repositories: [],
	}) satisfies Wire<TeamInfo>;

const FRONTEND = team(1, "Frontend Team");
const BACKEND = team(2, "Backend Team");
const TEAMS = [FRONTEND, BACKEND] satisfies Wire<TeamInfo>[];

/** A member's team as the users read carries it: a summary, without the team's members. */
const summary = ({ id, name, hidden }: Wire<TeamInfo>) => ({ id, name, hidden });

/** Ada and Grace are on Frontend, Bob only on Backend; every name contains an "e". */
const USERS = [
	{
		id: 11,
		login: "ada",
		name: "Ada Lovelace",
		url: "https://github.com/ada",
		teams: [summary(FRONTEND)],
	},
	{
		id: 12,
		login: "grace",
		name: "Grace Hopper",
		url: "https://github.com/grace",
		teams: [summary(FRONTEND)],
	},
	{
		id: 13,
		login: "bob",
		name: "Bob Builder",
		url: "https://github.com/bob",
		teams: [summary(BACKEND)],
	},
] satisfies Wire<UserTeams>[];

/** The members and their team facet, each answered by its own handler and counted apart. */
function mockMembersAndTeams(
	teams: () => Promise<Response> | Response,
	users: () => Response = () => HttpResponse.json(USERS),
) {
	const reads = { users: 0, teams: 0 };
	mockMembersRoute();
	server.use(
		http.get("*/workspaces/:workspaceSlug/users", () => {
			reads.users += 1;
			return users();
		}),
		http.get("*/workspaces/:workspaceSlug/team", async () => {
			reads.teams += 1;
			return teams();
		}),
	);
	return reads;
}

const unavailable = () =>
	HttpResponse.json({ status: 503, title: "Service Unavailable" }, { status: 503 });
const refused = () => HttpResponse.json({ status: 403, title: "Forbidden" }, { status: 403 });

const USERS_KEY = getUsersWithTeamsQueryKey({ path: { workspaceSlug: "acme" } });
const TEAMS_KEY = getAllTeamsQueryKey({ path: { workspaceSlug: "acme" } });

/** Reads one key again, as the app's own invalidation does after a change. */
async function reread(queryClient: QueryClient, queryKey: readonly unknown[]) {
	await act(async () => {
		await queryClient.invalidateQueries({ queryKey });
	});
}

/** Frontend Team, as the route writes a team id: quoted, so the search parser keeps it a string. */
const FRONTEND_SEARCH = "team=%221%22";

const SETTLE = { timeout: 5000 };

describe("workspace members route", () => {
	it("does not trap sidebar navigation", async () => {
		mockMembersRoute();
		const { router } = renderRouteAtWithRouter("/w/acme/admin/members");

		await screen.findByRole("heading", { name: "Members" }, ROUTE_RENDER_WAIT);
		fireEvent.click(await screen.findByRole("link", { name: "Settings" }));

		await waitFor(() => expect(router.state.location.pathname).toBe("/w/acme/admin/settings"));
	});

	it("lists the members when only the team filter fails, and retries the teams alone", async () => {
		let teamsReply = unavailable;
		const reads = mockMembersAndTeams(() => teamsReply());
		renderRouteAtWithRouter("/w/acme/admin/members");

		await screen.findByRole("heading", { name: "Members" }, ROUTE_RENDER_WAIT);
		await screen.findByText("Ada Lovelace", undefined, SETTLE);
		screen.getByText("Grace Hopper");
		screen.getByText("Bob Builder");

		teamsReply = () => HttpResponse.json(TEAMS);
		await userEvent.click(await screen.findByRole("button", { name: "Retry" }, SETTLE));

		await waitFor(() => expect(reads.teams).toBe(2), SETTLE);
		// The members were already in; retrying the filter does not read them again.
		expect(reads.users).toBe(1);
		await userEvent.click(screen.getByRole("combobox", { name: "Filter members by team" }));
		await screen.findByRole("option", { name: "Backend Team" }, SETTLE);
		screen.getByText("Bob Builder");
	});

	it("narrows to the address's team from the members' own teams while the team list loads", async () => {
		const held = deferred();
		mockMembersAndTeams(async () => {
			await held.promise;
			return HttpResponse.json(TEAMS);
		});
		try {
			const { router } = renderRouteAtWithRouter(`/w/acme/admin/members?${FRONTEND_SEARCH}`);

			await screen.findByRole("heading", { name: "Members" }, ROUTE_RENDER_WAIT);
			// The members are not held behind the team list, and the team in the address still applies.
			await screen.findByText("Ada Lovelace", undefined, SETTLE);
			screen.getByText("Grace Hopper");
			expect(screen.queryByText("Bob Builder")).toBeNull();

			held.resolve();
			await waitFor(
				() =>
					expect(
						screen.getByRole("combobox", { name: "Filter members by team" }).textContent,
					).toContain("Frontend Team"),
				SETTLE,
			);
			expect(screen.queryByText("Bob Builder")).toBeNull();
			expect(router.state.location.search.team).toBe("1");
		} finally {
			held.resolve();
		}
	});

	it("keeps the address's team and view when the team list fails, and restores its name on retry", async () => {
		let teamsReply = unavailable;
		const reads = mockMembersAndTeams(() => teamsReply());
		const { router } = renderRouteAtWithRouter(
			`/w/acme/admin/members?q=e&${FRONTEND_SEARCH}&sort=username&desc=true&size=20`,
		);
		const view = { q: "e", team: "1", sort: "username", desc: true, size: 20 };

		await screen.findByRole("heading", { name: "Members" }, ROUTE_RENDER_WAIT);
		// Every member matches "e"; only the team keeps Bob out.
		await screen.findByText("Ada Lovelace", undefined, SETTLE);
		screen.getByText("Grace Hopper");
		expect(screen.queryByText("Bob Builder")).toBeNull();
		expect(router.state.location.search).toStrictEqual(view);

		teamsReply = () => HttpResponse.json(TEAMS);
		await userEvent.click(await screen.findByRole("button", { name: "Retry" }, SETTLE));

		await waitFor(
			() =>
				expect(
					screen.getByRole("combobox", { name: "Filter members by team" }).textContent,
				).toContain("Frontend Team"),
			SETTLE,
		);
		expect(reads.users).toBe(1);
		expect(screen.queryByText("Bob Builder")).toBeNull();
		screen.getByText("Ada Lovelace");
		expect(router.state.location.search).toStrictEqual(view);
	});

	it("keeps the selected team while cached options are being refreshed", async () => {
		const held = deferred<Response>();
		const reads = mockMembersAndTeams(async () => held.promise);
		const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
		queryClient.setQueryData(TEAMS_KEY, [BACKEND]);
		try {
			const { router } = renderRouteAtWithRouter(
				`/w/acme/admin/members?${FRONTEND_SEARCH}`,
				queryClient,
			);
			await waitFor(() => expect(reads.teams).toBe(1), SETTLE);
			await screen.findByText("Ada Lovelace", undefined, SETTLE);
			expect(router.state.location.search.team).toBe("1");
			expect(screen.queryByText("Bob Builder")).toBeNull();

			held.resolve(HttpResponse.json(TEAMS));
			await waitFor(
				() =>
					expect(
						screen.getByRole("combobox", { name: "Filter members by team" }).textContent,
					).toContain("Frontend Team"),
				SETTLE,
			);
			expect(router.state.location.search.team).toBe("1");
			expect(screen.queryByText("Bob Builder")).toBeNull();
		} finally {
			held.resolve(HttpResponse.json(TEAMS));
		}
	});

	it("drops a team the loaded team list does not have, and keeps the rest of the view", async () => {
		mockMembersAndTeams(() => HttpResponse.json([]));
		const { router } = renderRouteAtWithRouter("/w/acme/admin/members?q=e&team=%229%22");

		await screen.findByRole("heading", { name: "Members" }, ROUTE_RENDER_WAIT);
		await waitFor(() => expect(router.state.location.search.team).toBeUndefined(), SETTLE);
		expect(router.state.location.search.q).toBe("e");
		expect(new URLSearchParams(router.state.location.searchStr).has("team")).toBe(false);
		await screen.findByText("Bob Builder", undefined, SETTLE);
		screen.getByText("Ada Lovelace");
	});

	it("never shows refused members again after a later failure, until a read succeeds", async () => {
		let usersReply = () => HttpResponse.json(USERS);
		const reads = mockMembersAndTeams(
			() => HttpResponse.json(TEAMS),
			() => usersReply(),
		);
		const { queryClient } = renderRouteAtWithRouter("/w/acme/admin/members");
		await screen.findByRole("heading", { name: "Members" }, ROUTE_RENDER_WAIT);
		await screen.findByText("Ada Lovelace", undefined, SETTLE);

		usersReply = refused;
		await reread(queryClient, USERS_KEY);
		await screen.findByText("You do not have access to members", undefined, SETTLE);
		expect(screen.queryByText("Ada Lovelace")).toBeNull();

		// A failure that may be retried must not bring back what the refusal took away.
		usersReply = unavailable;
		await reread(queryClient, USERS_KEY);
		await waitFor(() => expect(reads.users).toBe(3), SETTLE);
		await screen.findByText("We could not load members", undefined, SETTLE);
		expect(screen.queryByText("Ada Lovelace")).toBeNull();
		expect(queryClient.getQueryData(USERS_KEY)).toBeUndefined();

		usersReply = () => HttpResponse.json(USERS);
		await userEvent.click(screen.getByRole("button", { name: "Retry" }));
		await screen.findByText("Ada Lovelace", undefined, SETTLE);
	});

	it("offers no teams it was refused, while the members it may read stay listed", async () => {
		let teamsReply = () => HttpResponse.json(TEAMS);
		mockMembersAndTeams(() => teamsReply());
		const { queryClient } = renderRouteAtWithRouter("/w/acme/admin/members");
		await screen.findByRole("heading", { name: "Members" }, ROUTE_RENDER_WAIT);
		await screen.findByText("Ada Lovelace", undefined, SETTLE);

		teamsReply = refused;
		await reread(queryClient, TEAMS_KEY);
		await screen.findByText("You do not have access to teams", undefined, SETTLE);
		expect(screen.queryByRole("button", { name: "Retry" })).toBeNull();
		expect(queryClient.getQueryData(TEAMS_KEY)).toBeUndefined();

		await userEvent.click(screen.getByRole("combobox", { name: "Filter members by team" }));
		await screen.findByRole("option", { name: "All teams" }, SETTLE);
		expect(screen.queryByRole("option", { name: "Backend Team" })).toBeNull();
		expect(screen.queryByRole("option", { name: "Frontend Team" })).toBeNull();
		screen.getByText("Bob Builder");
	});

	it("recovers the team filter by the app's own retries, without reading the members again", async () => {
		// The production client's query defaults, retrying without the wait between attempts.
		const queryClient = new QueryClient({
			defaultOptions: {
				queries: { staleTime: QUERY_STALE_TIME_MS, retryDelay: 0 },
				mutations: { retry: false },
			},
		});
		const teamsReply = vi
			.fn<() => Response>()
			.mockReturnValueOnce(unavailable())
			.mockReturnValueOnce(unavailable())
			.mockReturnValue(HttpResponse.json(TEAMS));
		const reads = mockMembersAndTeams(teamsReply);
		renderRouteAtWithRouter("/w/acme/admin/members", queryClient);

		await screen.findByRole("heading", { name: "Members" }, ROUTE_RENDER_WAIT);
		await screen.findByText("Ada Lovelace", undefined, SETTLE);
		await waitFor(() => expect(reads.teams).toBe(3), SETTLE);
		await userEvent.click(screen.getByRole("combobox", { name: "Filter members by team" }));
		await screen.findByRole("option", { name: "Backend Team" }, SETTLE);
		expect(screen.queryByText("We could not load teams")).toBeNull();
		expect(reads.users).toBe(1);
	});
});

/**
 * Answers the first read. A later read could only come from another workspace's change, so it never
 * answers, and a read that was not owed stays visible as one in flight.
 */
function answerOnce(reply: () => Response) {
	let reads = 0;
	return async () => {
		reads += 1;
		return reads === 1 ? reply() : pending<Response>();
	};
}

describe("a member's visibility belongs to its workspace", () => {
	it("refreshes the workspace a member was hidden in after the admin switched, and leaves the other alone", async () => {
		const user = userEvent.setup();
		const response = deferred<Response>();
		const hidden = new Set<number>();
		const changes: string[] = [];
		const acme = workspaceListItem("acme", { id: 1 });
		const beta = workspaceListItem("beta", { id: 2 });
		// The production client's freshness: an unchanged read is not asked for again within 30 s.
		const client = new QueryClient({
			defaultOptions: {
				queries: { staleTime: QUERY_STALE_TIME_MS, retry: false },
				mutations: { retry: false },
			},
		});
		mockMembersRoute();
		server.use(
			http.get("*/workspaces", () => HttpResponse.json([acme, beta])),
			http.get("*/workspaces/acme", () => HttpResponse.json(acme)),
			http.get("*/workspaces/beta", () => HttpResponse.json(beta)),
			http.get("*/workspaces/acme/users", () =>
				HttpResponse.json(USERS.map((member) => ({ ...member, hidden: hidden.has(member.id) }))),
			),
			http.get(
				"*/workspaces/beta/users",
				answerOnce(() =>
					HttpResponse.json([
						{
							id: 21,
							login: "katherine",
							name: "Katherine Johnson",
							url: "https://github.com/katherine",
							teams: [],
						},
					] satisfies Wire<UserTeams>[]),
				),
			),
			http.patch("*/workspaces/acme/members/:userId/hidden", async ({ request }) => {
				const url = new URL(request.url);
				changes.push(`${url.pathname}${url.search}`);
				return response.promise;
			}),
		);
		const betaUsers = getUsersWithTeamsQueryKey({ path: { workspaceSlug: "beta" } });
		const { router, queryClient } = renderRouteAtWithRouter("/w/acme/admin/members", client);
		try {
			await user.click(
				await screen.findByRole(
					"button",
					{ name: "Hide Ada Lovelace from workspace activity" },
					ROUTE_RENDER_WAIT,
				),
			);
			await waitFor(() =>
				expect(changes).toStrictEqual(["/workspaces/acme/members/11/hidden?hidden=true"]),
			);
			await act(async () => {
				await router.navigate({
					to: "/w/$workspaceSlug/admin/members",
					params: { workspaceSlug: "beta" },
				});
			});
			await screen.findByText("Katherine Johnson", undefined, SETTLE);

			hidden.add(11);
			await act(async () => {
				response.resolve(
					HttpResponse.json({
						userId: 11,
						userLogin: "ada",
						role: "MEMBER",
						hidden: true,
						eligibleForPracticeReview: true,
						createdAt: "2026-01-01T00:00:00Z",
					}),
				);
			});
			await waitFor(() => expect(queryClient.isMutating()).toBe(0));
			// Beta's members are not read again: acme's change says nothing about them.
			expect(queryClient.getQueryState(betaUsers)).toMatchObject({
				fetchStatus: "idle",
				isInvalidated: false,
			});
			screen.getByText("Katherine Johnson");

			// Back within the freshness window, acme reads its members again and shows Ada hidden.
			await act(async () => {
				await router.navigate({
					to: "/w/$workspaceSlug/admin/members",
					params: { workspaceSlug: "acme" },
				});
			});
			await screen.findByRole(
				"button",
				{ name: "Show Ada Lovelace in workspace activity" },
				SETTLE,
			);
		} finally {
			response.resolve(new HttpResponse(null, { status: 500 }));
		}
	});
});
