import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useEffect } from "react";
import { toast } from "sonner";
import {
	addRepositoryToMonitorMutation,
	getRepositoriesToMonitorOptions,
	getWorkspaceOptions,
	listWorkspacesQueryKey,
	removeRepositoryToMonitorMutation,
	resetAndRecalculateLeaguesMutation,
	updateFeaturesMutation,
} from "@/api/@tanstack/react-query.gen";
import type { FeatureKey } from "@/components/admin/AdminFeaturesSettings";
import { AdminSettingsPage } from "@/components/admin/AdminSettingsPage";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/settings")({
	component: AdminSettings,
});

// Define the repository item type
type RepositoryItem = {
	nameWithOwner: string;
};

/**
 * Admin settings route component with data fetching and state management
 */
function AdminSettings() {
	const queryClient = useQueryClient();
	const { workspaceSlug } = Route.useParams();

	// Fetch full workspace data for gitProviderMode
	const workspaceQueryOptions = getWorkspaceOptions({
		path: { workspaceSlug },
	});
	const { data: workspaceData } = useQuery({
		...workspaceQueryOptions,
	});

	// Check if repository management is allowed
	// For GitHub App Installation workspaces, repositories are managed by the installation
	const isAppInstallationWorkspace = workspaceData?.gitProviderMode === "GITHUB_APP_INSTALLATION";

	// Repositories query
	const repositoriesQueryOptions = getRepositoriesToMonitorOptions({
		path: { workspaceSlug },
	});
	const {
		data: repositories,
		isLoading: isLoadingRepositories,
		error: repositoriesError,
	} = useQuery({
		...repositoriesQueryOptions,
		enabled: repositoriesQueryOptions.enabled ?? true,
	});

	// Add repository mutation
	const addRepository = useMutation({
		...addRepositoryToMonitorMutation(),
		onSuccess: () => {
			if (!workspaceSlug) {
				return;
			}
			queryClient.invalidateQueries({
				queryKey: repositoriesQueryOptions.queryKey,
			});
		},
	});

	// Remove repository mutation
	const removeRepository = useMutation({
		...removeRepositoryToMonitorMutation(),
		onSuccess: () => {
			if (!workspaceSlug) {
				return;
			}
			queryClient.invalidateQueries({
				queryKey: repositoriesQueryOptions.queryKey,
			});
		},
	});

	// Reset leagues mutation
	const resetLeagues = useMutation({
		...resetAndRecalculateLeaguesMutation(),
		onSuccess: () => {
			// Consider what queries need invalidation after leagues reset
			queryClient.invalidateQueries({ queryKey: ["workspace"] });
		},
	});

	// Update features mutation
	const updateFeatures = useMutation({
		...updateFeaturesMutation(),
		onSuccess: () => {
			if (!workspaceSlug) {
				return;
			}
			queryClient.invalidateQueries({
				queryKey: workspaceQueryOptions.queryKey,
			});
			queryClient.invalidateQueries({
				queryKey: listWorkspacesQueryKey(),
			});
			toast.success("Feature settings updated");
		},
		onError: () => {
			toast.error("Failed to update feature settings");
		},
	});

	useEffect(() => {
		if (repositoriesError) {
			toast.error(`Failed to load data: ${(repositoriesError as Error).message}`);
		}
	}, [repositoriesError]);

	// Handle add repository
	const handleAddRepository = (nameWithOwner: string) => {
		if (!workspaceSlug) {
			return;
		}
		addRepository.mutate({
			path: { workspaceSlug },
			query: { nameWithOwner },
		});
	};

	// Handle remove repository
	const handleRemoveRepository = (nameWithOwner: string) => {
		if (!workspaceSlug) {
			return;
		}
		removeRepository.mutate({
			path: { workspaceSlug },
			query: { nameWithOwner },
		});
	};

	// Handle feature toggle
	const handleToggleFeature = (feature: FeatureKey, enabled: boolean) => {
		if (!workspaceSlug) {
			return;
		}
		updateFeatures.mutate({
			path: { workspaceSlug },
			body: { [feature]: enabled },
		});
	};

	// Format repositories data for the UI component
	const formattedRepositories: RepositoryItem[] = (repositories || []).map((repo: string) => ({
		nameWithOwner: repo,
	}));

	return (
		<AdminSettingsPage
			repositories={formattedRepositories}
			isLoadingRepositories={isLoadingRepositories}
			repositoriesError={repositoriesError as Error | null}
			addRepositoryError={addRepository.error as Error | null}
			isAddingRepository={addRepository.isPending}
			isRemovingRepository={removeRepository.isPending}
			isResettingLeagues={resetLeagues.isPending}
			isAppInstallationWorkspace={isAppInstallationWorkspace}
			onAddRepository={handleAddRepository}
			onRemoveRepository={handleRemoveRepository}
			onResetLeagues={() => {
				if (!workspaceSlug) {
					return;
				}
				resetLeagues.mutate({ path: { workspaceSlug } });
			}}
			practicesEnabled={workspaceData?.practicesEnabled ?? false}
			achievementsEnabled={workspaceData?.achievementsEnabled ?? false}
			leaderboardEnabled={workspaceData?.leaderboardEnabled ?? false}
			progressionEnabled={workspaceData?.progressionEnabled ?? false}
			leaguesEnabled={workspaceData?.leaguesEnabled ?? false}
			practiceReviewAutoTriggerEnabled={workspaceData?.practiceReviewAutoTriggerEnabled ?? true}
			practiceReviewManualTriggerEnabled={workspaceData?.practiceReviewManualTriggerEnabled ?? true}
			isSavingFeatures={updateFeatures.isPending}
			onToggleFeature={handleToggleFeature}
		/>
	);
}
