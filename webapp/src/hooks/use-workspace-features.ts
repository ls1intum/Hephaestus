import { useQuery } from "@tanstack/react-query";
import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import type { WorkspaceListItem } from "@/api/types.gen";
import { useAuth } from "@/integrations/auth/AuthContext";

export interface WorkspaceFeatures {
	practicesEnabled: boolean;
	mentorEnabled: boolean;
	achievementsEnabled: boolean;
	leaderboardEnabled: boolean;
	progressionEnabled: boolean;
	leaguesEnabled: boolean;
}

export function useWorkspaceFeatures(workspaceSlug: string | undefined): WorkspaceFeatures & {
	isLoading: boolean;
	isError: boolean;
} {
	const { isAuthenticated, isLoading: authLoading } = useAuth();

	const query = useQuery({
		...listWorkspacesOptions(),
		enabled: isAuthenticated && !authLoading,
		staleTime: 30_000,
	});

	const workspaces = Array.isArray(query.data) ? query.data : [];
	const activeWorkspace = workspaces.find((ws) => ws.workspaceSlug === workspaceSlug);

	return {
		...getWorkspaceFeatures(activeWorkspace),
		isLoading: query.isLoading,
		isError: query.isError,
	};
}

export function getWorkspaceFeatures(workspace?: WorkspaceListItem): WorkspaceFeatures {
	return {
		practicesEnabled: workspace?.practicesEnabled ?? true,
		mentorEnabled: workspace?.mentorEnabled ?? false,
		achievementsEnabled: workspace?.achievementsEnabled ?? true,
		leaderboardEnabled: workspace?.leaderboardEnabled ?? true,
		progressionEnabled: workspace?.progressionEnabled ?? true,
		leaguesEnabled: workspace?.leaguesEnabled ?? false,
	};
}
