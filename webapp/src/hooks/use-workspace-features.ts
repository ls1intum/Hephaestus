import { useQuery } from "@tanstack/react-query";
import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import type { WorkspaceListItem } from "@/api/types.gen";
import { useAuth } from "@/integrations/auth/AuthContext";

export interface WorkspaceFeatures {
	practicesEnabled: boolean;
	achievementsEnabled: boolean;
	leaderboardEnabled: boolean;
	progressionEnabled: boolean;
	leaguesEnabled: boolean;
}

/**
 * Returns feature flags for the requested workspace from the shared workspace list.
 */

export function useWorkspaceFeatures(
	workspaceSlug?: string,
): WorkspaceFeatures & { isLoading: boolean } {
	const { isAuthenticated, isLoading: authLoading } = useAuth();

	const query = useQuery({
		...listWorkspacesOptions(),
		enabled: isAuthenticated && !authLoading,
		staleTime: 30_000,
	});

	const workspaces = Array.isArray(query.data) ? query.data : [];
	const activeWorkspace = workspaces.find((workspace) => workspace.workspaceSlug === workspaceSlug);

	return {
		...getWorkspaceFeatures(activeWorkspace),
		isLoading: authLoading || query.isLoading,
	};
}

/**
 * Extracts feature flags from a WorkspaceListItem.
 * Missing workspace data resolves to a safe disabled state.
 */
export function getWorkspaceFeatures(workspace?: WorkspaceListItem): WorkspaceFeatures {
	return {
		practicesEnabled: workspace?.practicesEnabled ?? false,
		achievementsEnabled: workspace?.achievementsEnabled ?? false,
		leaderboardEnabled: workspace?.leaderboardEnabled ?? false,
		progressionEnabled: workspace?.progressionEnabled ?? false,
		leaguesEnabled: workspace?.leaguesEnabled ?? false,
	};
}
