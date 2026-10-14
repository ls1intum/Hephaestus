import { useQuery } from "@tanstack/react-query";

import { accountName, workspaceIdentity } from "@/components/admin/users/account-labels";
import { hasMinimumWorkspaceRole } from "@/lib/workspace-roles";
import { useAuth } from "@/runtime/auth/AuthContext";
import { workspaceMembershipQueryOptions } from "@/runtime/auth/guard";

import { useActiveWorkspaceSlug } from "./use-active-workspace";

/** Read by the app chrome, which renders on every route, so the slug is the chrome's, not the URL's. */
export function useWorkspaceAccess() {
	const {
		chromeWorkspaceSlug,
		chromeWorkspace,
		workspaces,
		isLoading: workspacesLoading,
	} = useActiveWorkspaceSlug();
	const { isAuthenticated, isLoading: authLoading, userView, username } = useAuth();

	const membershipQuery = useQuery({
		...workspaceMembershipQueryOptions(chromeWorkspaceSlug ?? ""),
		enabled: Boolean(chromeWorkspaceSlug) && isAuthenticated && !authLoading,
	});

	const membership = membershipQuery.data;
	const role = membership?.role;
	const provider = chromeWorkspace?.providerType;

	return {
		chromeWorkspaceSlug,
		chromeWorkspace,
		workspaces,
		role,
		isAdmin: !userView && hasMinimumWorkspaceRole(role, "ADMIN"),
		// Sign-in usernames can name another linked identity; workspace activity needs this provider's login.
		selfLogin:
			chromeWorkspaceSlug === undefined || membershipQuery.isError
				? username
				: membership && (workspaceIdentity(membership.account, provider)?.login ?? undefined),
		userName: membership && accountName(membership.account, provider),
		isLoading: workspacesLoading || membershipQuery.isLoading,
		error: membershipQuery.error,
	};
}
