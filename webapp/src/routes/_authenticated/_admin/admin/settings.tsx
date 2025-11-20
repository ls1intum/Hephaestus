import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
	addRepositoryToMonitorMutation,
	getRepositoriesToMonitorOptions,
	removeRepositoryToMonitorMutation,
	resetAndRecalculateLeaguesMutation,
} from "@/api/@tanstack/react-query.gen";
import { AdminSettingsPage } from "@/components/admin/AdminSettingsPage";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

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
	const {
		slug,
		isLoading: isWorkspaceLoading,
		error: workspaceError,
	} = useActiveWorkspaceSlug();

	// Repositories query
	const repositoriesQueryOptions = getRepositoriesToMonitorOptions({
		path: { slug: slug ?? "" },
	});
	const {
		data: repositories,
		isLoading: isLoadingRepositories,
		error: repositoriesError,
	} = useQuery({
		...repositoriesQueryOptions,
		enabled: Boolean(slug) && (repositoriesQueryOptions.enabled ?? true),
	});

	// Add repository mutation
	const addRepository = useMutation({
		...addRepositoryToMonitorMutation(),
		onSuccess: () => {
			if (!slug) {
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
			if (!slug) {
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

	// Handle add repository
	const handleAddRepository = (nameWithOwner: string) => {
		if (!slug) {
			return;
		}
		const [owner, name] = nameWithOwner.split("/");
		addRepository.mutate({
			path: {
				slug,
				owner,
				name,
			},
		});
	};

	// Handle remove repository
	const handleRemoveRepository = (nameWithOwner: string) => {
		if (!slug) {
			return;
		}
		const [owner, name] = nameWithOwner.split("/");
		removeRepository.mutate({
			path: {
				slug,
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
			isLoadingRepositories={
				isWorkspaceLoading || isLoadingRepositories || !slug
			}
			repositoriesError={
				(workspaceError as Error | null) ?? (repositoriesError as Error | null)
			}
			addRepositoryError={addRepository.error as Error | null}
			isAddingRepository={addRepository.isPending}
			isRemovingRepository={removeRepository.isPending}
			isResettingLeagues={resetLeagues.isPending}
			onAddRepository={handleAddRepository}
			onRemoveRepository={handleRemoveRepository}
			onResetLeagues={() => {
				if (!slug) {
					return;
				}
				resetLeagues.mutate({ path: { slug } });
			}}
		/>
	);
}
