import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/integrations/auth/AuthContext";
import { workspaceMembershipQueryOptions } from "@/integrations/auth/guard";
import { hasMinimumWorkspaceRole } from "@/lib/workspace-roles";
import { useActiveWorkspaceSlug } from "./use-active-workspace";

export function useWorkspaceAccess() {
	const {
		workspaceSlug,
		workspaces,
		selectWorkspace,
		isLoading: workspacesLoading,
	} = useActiveWorkspaceSlug();
	const { isAuthenticated, isLoading: authLoading } = useAuth();

	const membershipQuery = useQuery({
		...workspaceMembershipQueryOptions(workspaceSlug ?? ""),
		enabled: Boolean(workspaceSlug) && isAuthenticated && !authLoading,
	});

	const role = membershipQuery.data?.role;

	return {
		workspaceSlug,
		workspaces,
		selectWorkspace,
		role,
		isAdmin: hasMinimumWorkspaceRole(role, "ADMIN"),
		// The account's SCM identity FOR THIS workspace's provider (ADR 0017): a linked account resolves
		// to its GitHub user in a GitHub workspace, its GitLab user in a GitLab workspace. Backed by the
		// server's workspace-scoped current-user resolution, so callers should prefer these over the
		// global `username` for in-workspace identity (profile link, displayed name).
		userLogin: membershipQuery.data?.userLogin,
		userName: membershipQuery.data?.userName,
		isLoading: workspacesLoading || membershipQuery.isLoading,
		error: membershipQuery.error as Error | null,
	};
}
