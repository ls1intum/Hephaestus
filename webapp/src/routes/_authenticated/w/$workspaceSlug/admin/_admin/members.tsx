import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	getAllTeamsOptions,
	getUsersWithTeamsOptions,
	getUsersWithTeamsQueryKey,
	updateMemberVisibilityMutation,
} from "@/api/@tanstack/react-query.gen";
import { AdminMembersPage } from "@/components/admin/AdminMembersPage";
import { adaptApiUserTeams } from "@/components/admin/types";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/members")({
	component: AdminMembersContainer,
	staticData: {
		workspaceSwitch: { target: "admin.members" },
	},
});

function AdminMembersContainer() {
	const { workspaceSlug } = Route.useParams();
	const { isLoading: isWorkspaceLoading, error: workspaceError } = useActiveWorkspaceSlug();

	// Fetch users with teams
	const usersQueryOptions = getUsersWithTeamsOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
	});
	const {
		data: usersData,
		isLoading: usersLoading,
		error: usersError,
	} = useQuery({
		...usersQueryOptions,
		enabled: Boolean(workspaceSlug) && (usersQueryOptions.enabled ?? true),
	});

	// Fetch teams
	const teamsQueryOptions = getAllTeamsOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
	});
	const {
		data: teamsData,
		isLoading: teamsLoading,
		error: teamsError,
	} = useQuery({
		...teamsQueryOptions,
		enabled: Boolean(workspaceSlug) && (teamsQueryOptions.enabled ?? true),
	});

	// Mutation for toggling member visibility
	const queryClient = useQueryClient();
	const toggleHidden = useMutation({
		...updateMemberVisibilityMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getUsersWithTeamsQueryKey({ path: { workspaceSlug: workspaceSlug ?? "" } }),
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

	// Transform API data for the component and sort alphabetically
	const users = (usersData?.map(adaptApiUserTeams) || [])
		.map((user) => ({
			...user,
			teams: [...(user.teams || [])].sort((a, b) => a.name.localeCompare(b.name)),
		}))
		.sort((a, b) => a.user.name.localeCompare(b.user.name));
	const teams = [...(teamsData || [])].sort((a, b) => a.name.localeCompare(b.name));
	const isLoading = isWorkspaceLoading || usersLoading || teamsLoading;

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	// Show error state if needed
	if (workspaceError || usersError || teamsError) {
		const errorMessage =
			(workspaceError as Error)?.message ||
			(usersError as Error)?.message ||
			(teamsError as Error)?.message;
		toast.error(`Failed to load data: ${errorMessage}`);
	}

	return (
		<AdminMembersPage
			users={users}
			teams={teams}
			isLoading={isLoading || !workspaceSlug}
			onToggleHidden={handleToggleHidden}
		/>
	);
}
