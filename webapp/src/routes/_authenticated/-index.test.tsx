import { render, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const mockNavigate = vi.fn();
const mockUseActiveWorkspaceSlug = vi.fn();
const mockUseAuth = vi.fn();

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

import { RedirectToWorkspace } from "./index";

describe("RedirectToWorkspace", () => {
	afterEach(() => {
		vi.clearAllMocks();
	});

	it("redirects authenticated users to the last selected workspace", async () => {
		const selectWorkspace = vi.fn();

		mockUseAuth.mockReturnValue({
			isAuthenticated: true,
		});
		mockUseActiveWorkspaceSlug.mockReturnValue({
			workspaceSlug: "prompt-edu",
			workspaces: [{ workspaceSlug: "ls1intum" }, { workspaceSlug: "prompt-edu" }],
			selectWorkspace,
			isLoading: false,
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

		mockUseAuth.mockReturnValue({
			isAuthenticated: true,
		});
		mockUseActiveWorkspaceSlug.mockReturnValue({
			workspaceSlug: undefined,
			workspaces: [{ workspaceSlug: "ls1intum" }],
			selectWorkspace,
			isLoading: true,
		});

		render(<RedirectToWorkspace />);

		expect(selectWorkspace).not.toHaveBeenCalled();
		expect(mockNavigate).not.toHaveBeenCalled();
	});
});
