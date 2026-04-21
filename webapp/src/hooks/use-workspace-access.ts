import { useQuery } from "@tanstack/react-query";
import { getCurrentUserMembershipOptions } from "@/api/@tanstack/react-query.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useActiveWorkspaceSlug } from "./use-active-workspace";
import { useWorkspaceSwitcher } from "./use-workspace-switcher";

export function useWorkspaceAccess() {
	const { workspaceSlug, workspaces, isLoading: workspacesLoading } = useActiveWorkspaceSlug();
	const switchWorkspace = useWorkspaceSwitcher();
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
		switchWorkspace,
		role,
		isMember,
		isAdmin,
		isOwner,
		isLoading: workspacesLoading || membershipQuery.isLoading,
		error: membershipQuery.error as Error | null,
	};
}
