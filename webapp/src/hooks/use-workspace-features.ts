import { useQuery } from "@tanstack/react-query";
import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import type { WorkspaceListItem } from "@/api/types.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useWorkspaceStore } from "@/stores/workspace-store";

export interface WorkspaceFeatures {
	practicesEnabled: boolean;
	mentorEnabled: boolean;
	achievementsEnabled: boolean;
	healthVisibility: WorkspaceListItem["healthVisibility"];
}

export function useWorkspaceFeatures(workspaceSlug?: string): WorkspaceFeatures & {
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
	const activeWorkspace = workspaces.find(
		(ws) => ws.workspaceSlug === (workspaceSlug ?? selectedSlug),
	);

	return {
		...getWorkspaceFeatures(activeWorkspace),
		isLoading: query.isLoading,
		isError: query.isError,
	};
}

export function getWorkspaceFeatures(workspace?: WorkspaceListItem): WorkspaceFeatures {
	return {
		practicesEnabled: workspace?.practicesEnabled ?? false,
		mentorEnabled: workspace?.mentorEnabled ?? false,
		achievementsEnabled: workspace?.achievementsEnabled ?? true,
		healthVisibility: workspace?.healthVisibility ?? "MENTORS_ONLY",
	};
}
