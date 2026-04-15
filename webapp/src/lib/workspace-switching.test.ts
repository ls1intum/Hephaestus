import { describe, expect, it } from "vitest";
import { buildWorkspaceSwitchPlan, getWorkspaceRouteMatch } from "./workspace-switching";

describe("workspace switching", () => {
	it("extracts the deepest matched workspace route and nearest override", () => {
		expect(
			getWorkspaceRouteMatch([
				{ routeId: "__root__", params: {} },
				{
					routeId: "/w/$workspaceSlug/",
					params: { workspaceSlug: "alpha" },
					staticData: { workspaceSwitch: { preserveSearch: true } },
				},
				{
					routeId: "/w/$workspaceSlug/mentor/$threadId",
					params: { workspaceSlug: "alpha", threadId: "thread-1" },
					staticData: { workspaceSwitch: { fallbackTo: "/w/$workspaceSlug/mentor" } },
				},
			]),
		).toEqual({
			workspaceSlug: "alpha",
			routeId: "/w/$workspaceSlug/mentor/$threadId",
			workspaceSwitch: { fallbackTo: "/w/$workspaceSlug/mentor" },
		});
	});

	it("defaults to same-route relative navigation", () => {
		expect(
			buildWorkspaceSwitchPlan(
				{
					workspaceSlug: "alpha",
					routeId: "/w/$workspaceSlug/teams/",
				},
				"beta",
			),
		).toEqual({
			kind: "relative",
			from: "/w/$workspaceSlug/teams/",
			to: ".",
			preserveSearch: true,
		});
	});

	it("supports route-local fallback targets", () => {
		expect(
			buildWorkspaceSwitchPlan(
				{
					workspaceSlug: "alpha",
					routeId: "/w/$workspaceSlug/mentor/$threadId",
					workspaceSwitch: { fallbackTo: "/w/$workspaceSlug/mentor" },
				},
				"beta",
			),
		).toEqual({
			kind: "relative",
			from: "/w/$workspaceSlug/mentor/$threadId",
			to: "/w/$workspaceSlug/mentor",
			preserveSearch: false,
		});
	});

	it("defaults to workspace home when route id is unknown", () => {
		expect(buildWorkspaceSwitchPlan(undefined, "beta")).toEqual({
			kind: "absolute",
			to: "/w/$workspaceSlug",
			params: { workspaceSlug: "beta" },
			preserveSearch: false,
		});
	});
});
