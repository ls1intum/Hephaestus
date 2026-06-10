import { useQuery } from "@tanstack/react-query";
import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import type { WorkspaceListItem } from "@/api/types.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useWorkspaceStore } from "@/stores/workspace-store";

export interface WorkspaceFeatures {
	practicesEnabled: boolean;
	mentorEnabled: boolean;
	achievementsEnabled: boolean;
	leaderboardEnabled: boolean;
	progressionEnabled: boolean;
	leaguesEnabled: boolean;
}

export function useWorkspaceFeatures(): WorkspaceFeatures & {
	isLoading: boolean;
	isError: boolean;
} {
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
		isError: query.isError,
	};
}

// Loading defaults: optimistic (true) for features whose UI surfaces are visible by default —
// flickering them off-then-on during load is worse than briefly showing a row that's about to
// be hidden. Pessimistic (false) for opt-in features whose UI must NOT appear unless explicitly
// granted (mentor, leagues). The chosen value for each field is the same as the post-load
// behavior most workspaces will see, so the typical render is flicker-free.
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
