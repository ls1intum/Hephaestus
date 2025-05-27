import {
	addLabelToTeamMutation,
	addRepositoryToTeamMutation,
	createTeamMutation,
	deleteTeamMutation,
	getRepositoriesToMonitorOptions,
	getTeamsOptions,
	getTeamsQueryKey,
	getUsersWithTeamsOptions,
	getUsersWithTeamsQueryKey,
	hideTeamMutation,
	removeLabelFromTeamMutation,
	removeRepositoryFromTeamMutation,
} from "@/api/@tanstack/react-query.gen";
import { AdminTeamsTable } from "@/components/admin/AdminTeamsTable";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/_admin/admin/teams")({
	component: AdminTeamsContainer,
});

function AdminTeamsContainer() {
	const queryClient = useQueryClient();

	// Query for teams data
	const teamsQuery = useQuery(getTeamsOptions({}));

	// Query for users with teams
	const usersQuery = useQuery(getUsersWithTeamsOptions({}));

	// Query for available repositories
	const repositoriesQuery = useQuery(getRepositoriesToMonitorOptions({}));

	// Mutations
	const createTeam = useMutation({
		...createTeamMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: getTeamsQueryKey() });
			queryClient.invalidateQueries({ queryKey: getUsersWithTeamsQueryKey() });
		},
	});

	const deleteTeam = useMutation({
		...deleteTeamMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: getTeamsQueryKey() });
			queryClient.invalidateQueries({ queryKey: getUsersWithTeamsQueryKey() });
		},
	});

	const hideTeam = useMutation({
		...hideTeamMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: getTeamsQueryKey() });
		},
	});

	const removeRepositoryFromTeam = useMutation({
		...removeRepositoryFromTeamMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: getTeamsQueryKey() });
		},
	});

	const addRepositoryToTeam = useMutation({
		...addRepositoryToTeamMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: getTeamsQueryKey() });
		},
	});

	const addLabelToTeam = useMutation({
		...addLabelToTeamMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: getTeamsQueryKey() });
		},
	});

	const removeLabelFromTeam = useMutation({
		...removeLabelFromTeamMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: getTeamsQueryKey() });
		},
	});

	// Handler functions
	const handleCreateTeam = async (name: string, color: string) => {
		await createTeam.mutateAsync({
			body: {
				id: 0, // Will be assigned by server
				name,
				color,
				hidden: false,
				repositories: [],
				labels: [],
				members: [],
			},
		});
	};

	const handleDeleteTeam = async (teamId: number) => {
		await deleteTeam.mutateAsync({
			path: { teamId },
		});
	};

	const handleHideTeam = async (teamId: number, hidden: boolean) => {
		await hideTeam.mutateAsync({
			path: { id: teamId },
			body: hidden,
		});
	};

	const handleRemoveRepositoryFromTeam = async (
		teamId: number,
		repositoryOwner: string,
		repositoryName: string,
	) => {
		await removeRepositoryFromTeam.mutateAsync({
			path: { teamId, repositoryOwner, repositoryName },
		});
	};

	const handleAddRepositoryToTeam = async (
		teamId: number,
		repositoryOwner: string,
		repositoryName: string,
	) => {
		await addRepositoryToTeam.mutateAsync({
			path: { teamId, repositoryOwner, repositoryName },
		});
	};

	const handleAddLabelToTeam = async (
		teamId: number,
		repositoryId: number,
		label: string,
	) => {
		await addLabelToTeam.mutateAsync({
			path: { teamId, repositoryId, label },
		});
	};

	const handleRemoveLabelFromTeam = async (teamId: number, labelId: number) => {
		await removeLabelFromTeam.mutateAsync({
			path: { teamId, labelId },
		});
	};

	// Convert repositories array to RepositoryInfo objects
	const availableRepositories = repositoriesQuery.data?.map((repo, index) => {
		const [, name] = repo.split("/");
		return {
			id: index + 1, // Simple ID assignment
			name,
			nameWithOwner: repo,
			description: `Repository: ${repo}`,
			htmlUrl: `https://github.com/${repo}`,
		};
	});

	return (
		<AdminTeamsTable
			teams={teamsQuery.data || []}
			availableRepositories={availableRepositories}
			users={usersQuery.data}
			isLoading={teamsQuery.isLoading || usersQuery.isLoading}
			onCreateTeam={handleCreateTeam}
			onDeleteTeam={handleDeleteTeam}
			onHideTeam={handleHideTeam}
			onAddRepositoryToTeam={handleAddRepositoryToTeam}
			onRemoveRepositoryFromTeam={handleRemoveRepositoryFromTeam}
			onAddLabelToTeam={handleAddLabelToTeam}
			onRemoveLabelFromTeam={handleRemoveLabelFromTeam}
		/>
	);
}
