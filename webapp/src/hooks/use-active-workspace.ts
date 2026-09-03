import { useQuery } from "@tanstack/react-query";
import { useParams } from "@tanstack/react-router";

import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import type { WorkspaceListItem } from "@/api/types.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
import { toScmProviderType } from "@/lib/provider";

const NO_WORKSPACES: WorkspaceListItem[] = [];

export function useActiveWorkspaceSlug() {
	const { isAuthenticated, isLoading: authLoading } = useAuth();
	const query = useQuery({
		...listWorkspacesOptions(),
		enabled: isAuthenticated && !authLoading,
		staleTime: 30_000,
		refetchOnWindowFocus: true,
	});
	const workspaces = Array.isArray(query.data) ? query.data : NO_WORKSPACES;
	const workspaceSlug = useParams({
		strict: false,
		select: (params) => ("workspaceSlug" in params ? params.workspaceSlug : undefined),
	});
	const activeWorkspace = workspaces.find((workspace) => workspace.workspaceSlug === workspaceSlug);

	return {
		workspaceSlug,
		workspaces,
		providerType: toScmProviderType(activeWorkspace?.providerType),
		isLoading: authLoading || query.isLoading,
		workspacesLoaded: query.data !== undefined,
		error: query.error,
	};
}
