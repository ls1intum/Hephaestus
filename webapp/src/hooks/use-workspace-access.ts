import { useQuery } from "@tanstack/react-query";

import { useAuth } from "@/integrations/auth/AuthContext";
import { workspaceMembershipQueryOptions } from "@/integrations/auth/guard";
import { hasMinimumWorkspaceRole } from "@/lib/workspace-roles";

import { useActiveWorkspaceSlug } from "./use-active-workspace";

export function useWorkspaceAccess() {
	const { workspaceSlug, workspaces, isLoading: workspacesLoading } = useActiveWorkspaceSlug();
	const { isAuthenticated, isLoading: authLoading } = useAuth();

	const membershipQuery = useQuery({
		...workspaceMembershipQueryOptions(workspaceSlug ?? ""),
		enabled: Boolean(workspaceSlug) && isAuthenticated && !authLoading,
	});

	const role = membershipQuery.data?.role;

	return {
		workspaceSlug,
		workspaces,
		role,
		isAdmin: hasMinimumWorkspaceRole(role, "ADMIN"),
		// The account's SCM identity for THIS workspace's provider, so prefer these over the global
		// `username` on the current user.
		userLogin: membershipQuery.data?.userLogin,
		userName: membershipQuery.data?.userName,
		isLoading: workspacesLoading || membershipQuery.isLoading,
		error: membershipQuery.error,
	};
}
