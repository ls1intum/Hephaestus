import { useQuery } from "@tanstack/react-query";
import { useParams } from "@tanstack/react-router";

import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import type { WorkspaceListItem } from "@/api/types.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
import { toScmProviderType } from "@/lib/provider";

/** Shared so an unloaded query keeps the same `workspaces` identity across renders. */
const NO_WORKSPACES: WorkspaceListItem[] = [];

export function useActiveWorkspaceSlug() {
	const { isAuthenticated, isLoading: authLoading } = useAuth();
	const query = useQuery({
		...listWorkspacesOptions(),
		enabled: isAuthenticated && !authLoading,
	});
	const workspaces = Array.isArray(query.data) ? query.data : NO_WORKSPACES;
	const workspaceSlug = useParams({ strict: false, select: (params) => params.workspaceSlug });
	// Two values, not one fallback: the app chrome also renders on routes whose URL names no
	// workspace, while `workspaceSlug` stays the only source for workspace-scoped data.
	const chromeWorkspaceSlug = workspaceSlug ?? workspaces[0]?.workspaceSlug;
	const chromeWorkspace = workspaces.find(
		(workspace) => workspace.workspaceSlug === chromeWorkspaceSlug,
	);

	return {
		workspaceSlug,
		chromeWorkspaceSlug,
		chromeWorkspace,
		workspaces,
		// A workspace is SCM-backed; SLACK (an identity provider) never reaches SCM-only UI, but the
		// generated type includes it, so narrow to the SCM ProviderType with a GITHUB fallback.
		providerType: toScmProviderType(chromeWorkspace?.providerType),
		isLoading: authLoading || query.isLoading,
		error: query.error,
	};
}
