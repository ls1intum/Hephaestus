import { useQuery } from "@tanstack/react-query";
import { useMatches } from "@tanstack/react-router";
import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
import { getWorkspaceRouteMatch } from "@/lib/workspace-switching";

/**
 * Returns the active workspace derived from the current route when present,
 * otherwise falls back to the first accessible workspace from the workspace list.
 */
export function useActiveWorkspaceSlug() {
	const { isAuthenticated, isLoading: authLoading } = useAuth();
	const query = useQuery({
		...listWorkspacesOptions(),
		enabled: isAuthenticated && !authLoading,
		staleTime: 30_000,
		refetchOnWindowFocus: true,
	});
	const workspaces = Array.isArray(query.data) ? query.data : [];
	const workspaceRoute = useMatches({
		select: (matches) => getWorkspaceRouteMatch(matches),
	});

	const slugFromRoute = workspaceRoute?.workspaceSlug;
	const activeWorkspace = slugFromRoute
		? workspaces.find((workspace) => workspace.workspaceSlug === slugFromRoute)
		: workspaces[0];

	return {
		workspaceSlug: activeWorkspace?.workspaceSlug,
		workspaces,
		providerType: activeWorkspace?.providerType ?? "GITHUB",
		isLoading: authLoading || query.isLoading,
		error: query.error as Error | null,
	};
}
