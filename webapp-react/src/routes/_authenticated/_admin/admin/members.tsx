import {
	addTeamToUserMutation,
	automaticallyAssignTeamsMutation,
	getTeamsOptions,
	getUsersWithTeamsOptions,
	getUsersWithTeamsQueryKey,
	removeUserFromTeamMutation,
} from "@/api/@tanstack/react-query.gen";
import { addTeamToUser, removeUserFromTeam } from "@/api/sdk.gen";
import { AdminMembersPage } from "@/components/admin/AdminMembersPage";
import { adaptApiUserTeams } from "@/components/admin/types";
import type { DefaultError } from "@tanstack/query-core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";

export const Route = createFileRoute("/_authenticated/_admin/admin/members")({
	component: AdminMembersContainer,
});

function AdminMembersContainer() {
	const queryClient = useQueryClient();

	// Fetch users with teams
	const {
		data: usersData,
		isLoading: usersLoading,
		error: usersError,
	} = useQuery(getUsersWithTeamsOptions());

	// Fetch teams
	const {
		data: teamsData,
		isLoading: teamsLoading,
		error: teamsError,
	} = useQuery(getTeamsOptions());

	// Mutation for automatically assigning teams
	const automaticallyAssignTeams = useMutation({
		...automaticallyAssignTeamsMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: getUsersWithTeamsQueryKey() });
			toast.success("Teams automatically assigned successfully");
		},
		onError: (error: DefaultError) => {
			toast.error(`Failed to automatically assign teams: ${error.message}`);
		},
	});

	// Mutations for team management
	const addTeamMutation = useMutation({
		...addTeamToUserMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: getUsersWithTeamsQueryKey() });
			toast.success("User successfully added to team");
		},
		onError: (error: DefaultError) => {
			toast.error(`Failed to add user to team: ${error.message}`);
		},
	});

	const removeTeamMutation = useMutation({
		...removeUserFromTeamMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: getUsersWithTeamsQueryKey() });
			toast.success("User successfully removed from team");
		},
		onError: (error: DefaultError) => {
			toast.error(`Failed to remove user from team: ${error.message}`);
		},
	});

	// Transform API data for the component and sort alphabetically
	const users = (usersData?.map(adaptApiUserTeams) || [])
		.map((user) => ({
			...user,
			teams: [...(user.teams || [])].sort((a, b) =>
				a.name.localeCompare(b.name),
			),
		}))
		.sort((a, b) => a.user.name.localeCompare(b.user.name));
	const teams = [...(teamsData || [])].sort((a, b) =>
		a.name.localeCompare(b.name),
	);
	const isLoading = usersLoading || teamsLoading;

	// Handle individual user actions
	const handleAddTeamToUser = (userId: string, teamId: string) => {
		const user = usersData?.find((u) => u.id.toString() === userId);
		if (user) {
			addTeamMutation.mutate({
				path: {
					login: user.login,
					teamId: Number.parseInt(teamId),
				},
			});
		}
	};

	const handleRemoveUserFromTeam = (userId: string, teamId: string) => {
		const user = usersData?.find((u) => u.id.toString() === userId);
		if (user) {
			removeTeamMutation.mutate({
				path: {
					login: user.login,
					teamId: Number.parseInt(teamId),
				},
			});
		}
	};

	// Handle bulk operations
	const handleBulkAddTeam = (userIds: string[], teamId: string) => {
		const promises = userIds.map((userId) => {
			const user = usersData?.find((u) => u.id.toString() === userId);
			if (user) {
				return addTeamToUser({
					path: { login: user.login, teamId: Number.parseInt(teamId) },
					throwOnError: true,
				});
			}
			return Promise.resolve();
		});

		Promise.all(promises)
			.then(() => {
				queryClient.invalidateQueries({
					queryKey: getUsersWithTeamsQueryKey(),
				});
				toast.success(`Successfully added ${userIds.length} users to team`);
			})
			.catch((error) => {
				toast.error(`Failed to add users to team: ${error.message}`);
			});
	};

	const handleBulkRemoveTeam = (userIds: string[], teamId: string) => {
		const promises = userIds.map((userId) => {
			const user = usersData?.find((u) => u.id.toString() === userId);
			if (user) {
				return removeUserFromTeam({
					path: { login: user.login, teamId: Number.parseInt(teamId) },
					throwOnError: true,
				});
			}
			return Promise.resolve();
		});

		Promise.all(promises)
			.then(() => {
				queryClient.invalidateQueries({
					queryKey: getUsersWithTeamsQueryKey(),
				});
				toast.success(`Successfully removed ${userIds.length} users from team`);
			})
			.catch((error) => {
				toast.error(`Failed to remove users from team: ${error.message}`);
			});
	};

	const handleAutomaticallyAssignTeams = () => {
		automaticallyAssignTeams.mutate({});
	};

	// Show error state if needed
	if (usersError || teamsError) {
		const errorMessage =
			(usersError as Error)?.message || (teamsError as Error)?.message;
		toast.error(`Failed to load data: ${errorMessage}`);
	}

	return (
		<AdminMembersPage
			users={users}
			teams={teams}
			isLoading={isLoading}
			onAddTeamToUser={handleAddTeamToUser}
			onRemoveUserFromTeam={handleRemoveUserFromTeam}
			onBulkAddTeam={handleBulkAddTeam}
			onBulkRemoveTeam={handleBulkRemoveTeam}
			onAutomaticallyAssignTeams={handleAutomaticallyAssignTeams}
			isAssigningTeams={automaticallyAssignTeams.isPending}
		/>
	);
}
