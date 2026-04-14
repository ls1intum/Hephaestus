import { describe, expect, it } from "vitest";
import { buildWorkspaceSwitchPlan, getWorkspaceRouteMatch } from "./workspace-switching";

describe("workspace switching", () => {
	it("extracts the deepest workspace-aware match", () => {
		expect(
			getWorkspaceRouteMatch([
				{ params: {} },
				{
					params: { workspaceSlug: "alpha" },
					staticData: { workspaceSwitch: { target: "workspace.home", preserveSearch: true } },
				},
				{
					params: { workspaceSlug: "alpha", threadId: "thread-1" },
					staticData: { workspaceSwitch: { target: "workspace.mentor" } },
				},
			]),
		).toEqual({
			workspaceSlug: "alpha",
			workspaceSwitch: { target: "workspace.mentor" },
		});
	});

	it("inherits switch metadata from the nearest workspace ancestor", () => {
		expect(
			getWorkspaceRouteMatch([
				{ params: {} },
				{
					params: { workspaceSlug: "alpha" },
					staticData: { workspaceSwitch: { target: "admin.practices" } },
				},
				{ params: { workspaceSlug: "alpha" } },
			]),
		).toEqual({
			workspaceSlug: "alpha",
			workspaceSwitch: { target: "admin.practices" },
		});
	});

	it("builds safe route targets from route metadata", () => {
		expect(
			buildWorkspaceSwitchPlan({ target: "workspace.home", preserveSearch: true }, "beta"),
		).toEqual({
			to: "/w/$workspaceSlug",
			params: { workspaceSlug: "beta" },
			preserveSearch: true,
		});

		expect(buildWorkspaceSwitchPlan({ target: "admin.practices" }, "beta")).toEqual({
			to: "/w/$workspaceSlug/admin/practices",
			params: { workspaceSlug: "beta" },
			preserveSearch: false,
		});
	});

	it("falls back to workspace home when a route does not opt in", () => {
		expect(buildWorkspaceSwitchPlan(undefined, "beta")).toEqual({
			to: "/w/$workspaceSlug",
			params: { workspaceSlug: "beta" },
			preserveSearch: false,
		});
	});
});
