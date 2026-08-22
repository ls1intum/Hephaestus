import { render, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { WorkspaceListItem } from "@/api/types.gen";
import type { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import type { AuthContextType } from "@/integrations/auth/AuthContext";

const mockNavigate = vi.fn();
// A double stands in for the whole hook, not for the fields this component happens to read, so it
// carries the hook's real signature and stops matching it the moment the hook's shape moves.
const mockUseActiveWorkspaceSlug = vi.fn<typeof useActiveWorkspaceSlug>();
const mockUseAuth = vi.fn<() => AuthContextType>();

vi.mock("@tanstack/react-router", () => ({
	createFileRoute: () => (options: unknown) => options,
	useNavigate: () => mockNavigate,
}));

vi.mock("@/hooks/use-active-workspace", () => ({
	useActiveWorkspaceSlug: () => mockUseActiveWorkspaceSlug(),
}));

vi.mock("@/integrations/auth/AuthContext", () => ({
	useAuth: () => mockUseAuth(),
}));

vi.mock("@/components/workspace/NoWorkspace", () => ({
	NoWorkspace: () => <div>No Workspace</div>,
}));

import { RedirectToWorkspace } from "./RedirectToWorkspace";

const signedIn: AuthContextType = {
	isAuthenticated: true,
	isLoading: false,
	isError: false,
	username: "octocat",
	userRoles: ["ROLE_USER"],
	isAppAdmin: false,
	userProfile: undefined,
	login: () => {},
	linkAccount: () => {},
	logout: () => Promise.resolve(),
	hasRole: () => false,
	isCurrentUser: () => false,
	getUserId: () => undefined,
	getGitProviderId: () => undefined,
	getUserProfilePictureUrl: () => "",
	hasGitLabIdentity: false,
	linkedProviders: [],
	isImpersonating: false,
	impersonatedDisplayName: undefined,
};

function workspace(id: number, workspaceSlug: string): WorkspaceListItem {
	return {
		id,
		workspaceSlug,
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

describe("RedirectToWorkspace", () => {
	afterEach(() => {
		vi.clearAllMocks();
	});

	it("redirects authenticated users to the last selected workspace", async () => {
		const selectWorkspace = vi.fn();

		mockUseAuth.mockReturnValue(signedIn);
		mockUseActiveWorkspaceSlug.mockReturnValue({
			workspaceSlug: "prompt-edu",
			workspaces: [workspace(1, "ls1intum"), workspace(2, "prompt-edu")],
			providerType: "GITHUB",
			selectWorkspace,
			isLoading: false,
			error: null,
		});

		render(<RedirectToWorkspace />);

		await waitFor(() => {
			expect(selectWorkspace).toHaveBeenCalledWith("prompt-edu");
			expect(mockNavigate).toHaveBeenCalledWith({
				to: "/w/$workspaceSlug",
				params: { workspaceSlug: "prompt-edu" },
				replace: true,
			});
		});
	});

	it("waits for workspace selection hydration before redirecting", () => {
		const selectWorkspace = vi.fn();

		mockUseAuth.mockReturnValue(signedIn);
		mockUseActiveWorkspaceSlug.mockReturnValue({
			workspaceSlug: undefined,
			workspaces: [workspace(1, "ls1intum")],
			providerType: "GITHUB",
			selectWorkspace,
			isLoading: true,
			error: null,
		});

		render(<RedirectToWorkspace />);

		expect(selectWorkspace).not.toHaveBeenCalled();
		expect(mockNavigate).not.toHaveBeenCalled();
	});
});
