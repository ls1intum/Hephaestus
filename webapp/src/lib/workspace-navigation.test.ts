import { describe, expect, it } from "vitest";
import { getWorkspaceSwitchNavigation } from "./workspace-navigation";

describe("getWorkspaceSwitchNavigation", () => {
	it("preserves leaderboard search-capable navigation", () => {
		expect(getWorkspaceSwitchNavigation("/w/alpha", "beta")).toEqual({
			to: "/w/$workspaceSlug",
			params: { workspaceSlug: "beta" },
			preserveSearch: true,
		});
	});

	it("keeps users on portable workspace routes", () => {
		expect(getWorkspaceSwitchNavigation("/w/alpha/admin/settings", "beta")).toEqual({
			to: "/w/$workspaceSlug/admin/settings",
			params: { workspaceSlug: "beta" },
			preserveSearch: false,
		});

		expect(getWorkspaceSwitchNavigation("/w/alpha/mentor/thread-1", "beta")).toEqual({
			to: "/w/$workspaceSlug/mentor",
			params: { workspaceSlug: "beta" },
			preserveSearch: false,
		});
	});

	it("falls back to workspace home for non-portable routes", () => {
		expect(getWorkspaceSwitchNavigation("/w/alpha/user/alice", "beta")).toEqual({
			to: "/w/$workspaceSlug",
			params: { workspaceSlug: "beta" },
			preserveSearch: false,
		});
	});
});
