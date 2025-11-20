import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	getAllTeamsOptions,
	getUsersWithTeamsOptions,
} from "@/api/@tanstack/react-query.gen";
import { AdminMembersPage } from "@/components/admin/AdminMembersPage";
import { adaptApiUserTeams } from "@/components/admin/types";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/_admin/admin/members")({
	component: AdminMembersContainer,
});

function AdminMembersContainer() {
	const {
		slug,
		isLoading: isWorkspaceLoading,
		error: workspaceError,
	} = useActiveWorkspaceSlug();

	// Fetch users with teams
	const usersQueryOptions = getUsersWithTeamsOptions({
		path: { slug: slug ?? "" },
	});
	const {
		data: usersData,
		isLoading: usersLoading,
		error: usersError,
	} = useQuery({
		...usersQueryOptions,
		enabled: Boolean(slug) && (usersQueryOptions.enabled ?? true),
	});

	// Fetch teams
	const {
		data: teamsData,
		isLoading: teamsLoading,
		error: teamsError,
	} = useQuery(getAllTeamsOptions({}));

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
	const isLoading = isWorkspaceLoading || usersLoading || teamsLoading;

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
			isLoading={isLoading || !slug}
		/>
	);
}
