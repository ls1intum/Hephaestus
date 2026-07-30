import { screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

describe("practice catalog route", () => {
	it("keeps the catalog available while new reviews are paused", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/members/me", () =>
				HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
			),
			http.get("*/workspaces", () =>
				HttpResponse.json([
					{
						id: 1,
						workspaceSlug: "acme",
						displayName: "Acme",
						providerType: "GITHUB",
						status: "ACTIVE",
						practicesEnabled: false,
						mentorEnabled: false,
						achievementsEnabled: false,
						leaderboardEnabled: false,
						progressionEnabled: false,
						leaguesEnabled: false,
					},
				]),
			),
			http.get("*/workspaces/:workspaceSlug/practice-areas", () => HttpResponse.json([])),
			http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json([])),
		);

		renderRouteAt("/w/acme/admin/practices");

		await screen.findByRole("heading", { name: "Practice catalog" }, ROUTE_RENDER_WAIT);
		expect(screen.queryByRole("button", { name: "Enable practices" })).toBeNull();
	});
});
