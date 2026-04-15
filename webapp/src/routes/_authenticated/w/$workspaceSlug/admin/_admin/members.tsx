import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useEffect } from "react";
import { toast } from "sonner";
import {
	getAllTeamsOptions,
	getUsersWithTeamsOptions,
	getUsersWithTeamsQueryKey,
	updateMemberVisibilityMutation,
} from "@/api/@tanstack/react-query.gen";
import { AdminMembersPage } from "@/components/admin/AdminMembersPage";
import { adaptApiUserTeams } from "@/components/admin/types";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/members")({
	component: AdminMembersContainer,
});

function AdminMembersContainer() {
	const { workspaceSlug } = Route.useParams();

	// Fetch users with teams
	const usersQueryOptions = getUsersWithTeamsOptions({
		path: { workspaceSlug },
	});
	const {
		data: usersData,
		isLoading: usersLoading,
		error: usersError,
	} = useQuery({
		...usersQueryOptions,
		enabled: usersQueryOptions.enabled ?? true,
	});

	// Fetch teams
	const teamsQueryOptions = getAllTeamsOptions({
		path: { workspaceSlug },
	});
	const {
		data: teamsData,
		isLoading: teamsLoading,
		error: teamsError,
	} = useQuery({
		...teamsQueryOptions,
		enabled: teamsQueryOptions.enabled ?? true,
	});

	// Mutation for toggling member visibility
	const queryClient = useQueryClient();
	const toggleHidden = useMutation({
		...updateMemberVisibilityMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getUsersWithTeamsQueryKey({ path: { workspaceSlug } }),
			});
		},
		onError: (error) => {
			toast.error(`Failed to update visibility: ${(error as Error).message}`);
		},
	});

	const handleToggleHidden = (userId: number, hidden: boolean) => {
		if (!workspaceSlug) return;
		toggleHidden.mutate({
			path: { workspaceSlug, userId },
			query: { hidden },
		});
	};

	useEffect(() => {
		if (usersError || teamsError) {
			const errorMessage = (usersError as Error)?.message || (teamsError as Error)?.message;
			toast.error(`Failed to load data: ${errorMessage}`);
		}
	}, [usersError, teamsError]);

	// Transform API data for the component and sort alphabetically
	const users = (usersData?.map(adaptApiUserTeams) || [])
		.map((user) => ({
			...user,
			teams: [...(user.teams || [])].sort((a, b) => a.name.localeCompare(b.name)),
		}))
		.sort((a, b) => a.user.name.localeCompare(b.user.name));
	const teams = [...(teamsData || [])].sort((a, b) => a.name.localeCompare(b.name));
	const isLoading = usersLoading || teamsLoading;

	return (
		<AdminMembersPage
			users={users}
			teams={teams}
			isLoading={isLoading}
			onToggleHidden={handleToggleHidden}
		/>
	);
}
