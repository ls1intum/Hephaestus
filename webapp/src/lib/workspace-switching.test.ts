import { describe, expect, it } from "vitest";

import { getWorkspaceRouteMatch, isPortableWorkspaceRoute } from "./workspace-switching";

describe("workspace switching", () => {
	it("keeps a route with no workspace-owned path parameters portable", () => {
		const match = getWorkspaceRouteMatch([
			{ routeId: "__root__", params: {} },
			{ routeId: "/w/$workspaceSlug/teams/", params: { workspaceSlug: "alpha" } },
		]);

		expect(match).toStrictEqual({
			routeId: "/w/$workspaceSlug/teams/",
			params: { workspaceSlug: "alpha" },
		});
		expect(isPortableWorkspaceRoute(match)).toBe(true);
	});

	it.each([
		["mentor thread", { workspaceSlug: "alpha", threadId: "thread-1" }],
		["user profile", { workspaceSlug: "alpha", username: "octocat" }],
		["practice", { workspaceSlug: "alpha", practiceSlug: "reviews" }],
	])("treats a %s as non-portable", (_name, params) => {
		expect(isPortableWorkspaceRoute({ routeId: "a workspace route", params })).toBe(false);
	});

	it("defaults an unknown route to non-portable", () => {
		expect(isPortableWorkspaceRoute(undefined)).toBe(false);
	});
});
