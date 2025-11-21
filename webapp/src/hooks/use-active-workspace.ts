import { useQuery } from "@tanstack/react-query";
import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";

/**
 * Returns the currently selected workspace slug.
 * Temporarily defaults to the first workspace until multi-workspace selection is implemented.
 */
export function useActiveWorkspaceSlug() {
	const query = useQuery(listWorkspacesOptions());
	const workspaceSlug = query.data?.[0]?.workspaceSlug;

	return {
		workspaceSlug,
		workspaces: query.data,
		isLoading: query.isLoading,
		error: query.error as Error | null,
	};
}
