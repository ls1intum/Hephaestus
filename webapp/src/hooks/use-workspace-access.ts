import { useQuery } from "@tanstack/react-query";

import { useAuth } from "@/integrations/auth/AuthContext";
import { workspaceMembershipQueryOptions } from "@/integrations/auth/guard";
import { hasMinimumWorkspaceRole } from "@/lib/workspace-roles";

import { useActiveWorkspaceSlug } from "./use-active-workspace";

/** Read by the app chrome, which renders on every route, so the slug is the chrome's, not the URL's. */
export function useWorkspaceAccess() {
	const {
		chromeWorkspaceSlug: workspaceSlug,
		workspaces,
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
