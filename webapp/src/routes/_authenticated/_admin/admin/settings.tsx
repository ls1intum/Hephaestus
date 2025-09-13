import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
	addRepositoryToMonitorMutation,
	getRepositoriesToMonitorOptions,
	removeRepositoryToMonitorMutation,
	resetAndRecalculateLeaguesMutation,
} from "@/api/@tanstack/react-query.gen";
import { AdminSettingsPage } from "@/components/admin/AdminSettingsPage";

export const Route = createFileRoute("/_authenticated/_admin/admin/settings")({
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

	// Repositories query
	const {
		data: repositories,
		isLoading: isLoadingRepositories,
		error: repositoriesError,
	} = useQuery({
		...getRepositoriesToMonitorOptions({}),
	});

	// Add repository mutation
	const addRepository = useMutation({
		...addRepositoryToMonitorMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getRepositoriesToMonitorOptions({}).queryKey,
			});
		},
	});

	// Remove repository mutation
	const removeRepository = useMutation({
		...removeRepositoryToMonitorMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getRepositoriesToMonitorOptions({}).queryKey,
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

	// Handle add repository
	const handleAddRepository = (nameWithOwner: string) => {
		const [owner, name] = nameWithOwner.split("/");
		addRepository.mutate({
			path: {
				owner,
				name,
			},
		});
	};

	// Handle remove repository
	const handleRemoveRepository = (nameWithOwner: string) => {
		const [owner, name] = nameWithOwner.split("/");
		removeRepository.mutate({
			path: {
				owner,
				name,
			},
		});
	};

	// Format repositories data for the UI component
	const formattedRepositories: RepositoryItem[] = (repositories || []).map(
		(repo: string) => ({
			nameWithOwner: repo,
		}),
	);

	return (
		<AdminSettingsPage
			repositories={formattedRepositories}
			isLoadingRepositories={isLoadingRepositories}
			repositoriesError={repositoriesError as Error | null}
			addRepositoryError={addRepository.error as Error | null}
			isAddingRepository={addRepository.isPending}
			isRemovingRepository={removeRepository.isPending}
			isResettingLeagues={resetLeagues.isPending}
			onAddRepository={handleAddRepository}
			onRemoveRepository={handleRemoveRepository}
			onResetLeagues={() => resetLeagues.mutate({})}
		/>
	);
}
