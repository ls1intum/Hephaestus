import type { DefaultError } from "@tanstack/query-core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";

import {
	getTeamsOptions,
	getUsersWithTeamsOptions,
} from "@/api/@tanstack/react-query.gen";
import { addTeamToUser, removeUserFromTeam } from "@/api/sdk.gen";
import type { UserInfo } from "@/api/types.gen";
import { UsersTable } from "@/components/workspace/UsersTable";
import { adaptApiUserTeams } from "@/components/workspace/types";

export const Route = createFileRoute("/_authenticated/workspace/users")({
	component: RouteComponent,
});

function RouteComponent() {
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

	// Mutations for team management
	const addTeamMutation = useMutation<
		UserInfo,
		Error,
		{ userId: string; teamId: string }
	>({
		mutationFn: async ({
			userId,
			teamId,
		}: { userId: string; teamId: string }) => {
			const { data } = await addTeamToUser({
				path: {
					login: userId,
					teamId: Number.parseInt(teamId),
				},
				throwOnError: true,
			});
			return data;
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["getUsersWithTeams"] });
			toast.success("User added to team successfully");
		},
		onError: (error: DefaultError) => {
			toast.error(`Failed to add user to team: ${error.message}`);
			console.error("Error adding user to team:", error);
		},
	});

	const removeTeamMutation = useMutation<
		UserInfo,
		Error,
		{ userId: string; teamId: string }
	>({
		mutationFn: async ({
			userId,
			teamId,
		}: { userId: string; teamId: string }) => {
			const { data } = await removeUserFromTeam({
				path: {
					login: userId,
					teamId: Number.parseInt(teamId),
				},
				throwOnError: true,
			});
			return data;
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["getUsersWithTeams"] });
			toast.success("User removed from team successfully");
		},
		onError: (error: DefaultError) => {
			toast.error(`Failed to remove user from team: ${error.message}`);
			console.error("Error removing user from team:", error);
		},
	});

	const handleAddTeamToUser = (userId: string, teamId: string) => {
		addTeamMutation.mutate({ userId, teamId });
	};

	const handleRemoveUserFromTeam = (userId: string, teamId: string) => {
		removeTeamMutation.mutate({ userId, teamId });
	};

	const handleBulkAddTeam = async (userIds: string[], teamId: string) => {
		try {
			await Promise.all(
				userIds.map((userId) =>
					addTeamToUser({
						path: {
							login: userId,
							teamId: Number.parseInt(teamId),
						},
						throwOnError: true,
					}),
				),
			);
			queryClient.invalidateQueries({ queryKey: ["getUsersWithTeams"] });
			toast.success(`Added ${userIds.length} users to team successfully`);
		} catch (error) {
			toast.error("Failed to add users to team");
			console.error("Error in bulk add team:", error);
		}
	};

	const handleBulkRemoveTeam = async (userIds: string[], teamId: string) => {
		try {
			await Promise.all(
				userIds.map((userId) =>
					removeUserFromTeam({
						path: {
							login: userId,
							teamId: Number.parseInt(teamId),
						},
						throwOnError: true,
					}),
				),
			);
			queryClient.invalidateQueries({ queryKey: ["getUsersWithTeams"] });
			toast.success(`Removed ${userIds.length} users from team successfully`);
		} catch (error) {
			toast.error("Failed to remove users from team");
			console.error("Error in bulk remove team:", error);
		}
	};

	const isLoading = usersLoading || teamsLoading;
	const hasError = usersError || teamsError;

	if (hasError) {
		return (
			<div className="flex items-center justify-center h-96">
				<div className="text-center">
					<h2 className="text-lg font-semibold text-destructive mb-2">
						Error loading data
					</h2>
					<p className="text-muted-foreground">
						{usersError?.message ||
							teamsError?.message ||
							"Something went wrong"}
					</p>
				</div>
			</div>
		);
	}

	return (
		<div className="container mx-auto py-6">
			<div className="mb-6">
				<h1 className="text-3xl font-bold tracking-tight">Users</h1>
				<p className="text-muted-foreground">
					Manage users and their team assignments in your workspace.
				</p>
			</div>

			<UsersTable
				users={(usersData || []).map((user) => adaptApiUserTeams(user))}
				teams={teamsData || []}
				isLoading={isLoading}
				onAddTeamToUser={handleAddTeamToUser}
				onRemoveUserFromTeam={handleRemoveUserFromTeam}
				onBulkAddTeam={handleBulkAddTeam}
				onBulkRemoveTeam={handleBulkRemoveTeam}
			/>
		</div>
	);
}
