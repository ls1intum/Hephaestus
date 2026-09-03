import { useQuery } from "@tanstack/react-query";
import { useParams } from "@tanstack/react-router";

import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
import { toScmProviderType } from "@/lib/provider";

export function useActiveWorkspaceSlug() {
	const { isAuthenticated, isLoading: authLoading } = useAuth();
	const query = useQuery({
		...listWorkspacesOptions(),
		enabled: isAuthenticated && !authLoading,
		staleTime: 30_000,
		refetchOnWindowFocus: true,
	});
	const workspaces = Array.isArray(query.data) ? query.data : [];
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
		error: query.error,
	};
}
