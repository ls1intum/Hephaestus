import { useQuery } from "@tanstack/react-query";
import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import type { WorkspaceListItem } from "@/api/types.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useWorkspaceStore } from "@/stores/workspace-store";

export interface WorkspaceFeatures {
	practicesEnabled: boolean;
	achievementsEnabled: boolean;
	leaderboardEnabled: boolean;
	progressionEnabled: boolean;
}

/**
 * Returns the feature flags for the active workspace.
 * Reads from the listWorkspaces query cache (same query as useActiveWorkspaceSlug).
 * Flags default to false while loading — consumers must check `isLoading`
 * before acting on flag values.
 */
export function useWorkspaceFeatures(): WorkspaceFeatures & { isLoading: boolean } {
	const { selectedSlug } = useWorkspaceStore();
	const { isAuthenticated, isLoading: authLoading } = useAuth();

	const query = useQuery({
		...listWorkspacesOptions(),
		enabled: isAuthenticated && !authLoading,
		staleTime: 30_000,
	});

	const workspaces = Array.isArray(query.data) ? query.data : [];
	const activeWorkspace = workspaces.find((ws) => ws.workspaceSlug === selectedSlug);

	return {
		...getWorkspaceFeatures(activeWorkspace),
		isLoading: query.isLoading,
	};
}

/**
 * Extracts feature flags from a WorkspaceListItem.
 * Defaults all to false when workspace is undefined (loading / error / not found).
 * This is the safe default — all consumers already show a spinner or return null
 * while `isLoading` is true, so the false default only matters on query failure
 * where showing nothing is safer than showing gated content.
 */
export function getWorkspaceFeatures(workspace?: WorkspaceListItem): WorkspaceFeatures {
	return {
		practicesEnabled: workspace?.practicesEnabled ?? false,
		achievementsEnabled: workspace?.achievementsEnabled ?? false,
		leaderboardEnabled: workspace?.leaderboardEnabled ?? false,
		progressionEnabled: workspace?.progressionEnabled ?? false,
	};
}
