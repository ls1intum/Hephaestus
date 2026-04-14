import { describe, expect, it } from "vitest";
import { buildWorkspaceSwitchPlan, getWorkspaceRouteMatch } from "./workspace-switching";

describe("workspace switching", () => {
	it("extracts the deepest matched workspace route", () => {
		expect(
			getWorkspaceRouteMatch([
				{ routeId: "__root__", params: {} },
				{ routeId: "/w/$workspaceSlug/", params: { workspaceSlug: "alpha" } },
				{
					routeId: "/w/$workspaceSlug/mentor/$threadId",
					params: { workspaceSlug: "alpha", threadId: "thread-1" },
				},
			]),
		).toEqual({
			workspaceSlug: "alpha",
			routeId: "/w/$workspaceSlug/mentor/$threadId",
		});
	});

	it("preserves search only for leaderboard", () => {
		expect(buildWorkspaceSwitchPlan("/w/$workspaceSlug/", "beta")).toEqual({
			to: "/w/$workspaceSlug",
			params: { workspaceSlug: "beta" },
			preserveSearch: true,
		});
	});

	it("falls back deep resource routes to safe targets", () => {
		expect(buildWorkspaceSwitchPlan("/w/$workspaceSlug/mentor/$threadId", "beta")).toEqual({
			to: "/w/$workspaceSlug/mentor",
			params: { workspaceSlug: "beta" },
			preserveSearch: false,
		});

		expect(buildWorkspaceSwitchPlan("/$practiceSlug", "beta")).toEqual({
			to: "/w/$workspaceSlug/admin/practices",
			params: { workspaceSlug: "beta" },
			preserveSearch: false,
		});
	});

	it("defaults to workspace home when route id is unknown", () => {
		expect(buildWorkspaceSwitchPlan(undefined, "beta")).toEqual({
			to: "/w/$workspaceSlug",
			params: { workspaceSlug: "beta" },
			preserveSearch: false,
		});
	});
});
