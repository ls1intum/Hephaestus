import { useQuery } from "@tanstack/react-query";
import { getCurrentUserMembershipOptions } from "@/api/@tanstack/react-query.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
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
		...getCurrentUserMembershipOptions({
			path: { workspaceSlug: workspaceSlug ?? "" },
		}),
		enabled: Boolean(workspaceSlug) && isAuthenticated && !authLoading,
	});

	const role = membershipQuery.data?.role;
	const isMember = Boolean(role);
	const isAdmin = role === "ADMIN" || role === "OWNER";
	const isOwner = role === "OWNER";

	return {
		workspaceSlug,
		workspaces,
		selectWorkspace,
		role,
		isMember,
		isAdmin,
		isOwner,
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
