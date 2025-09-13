import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	getAllTeamsOptions,
	getUsersWithTeamsOptions,
} from "@/api/@tanstack/react-query.gen";
import { AdminMembersPage } from "@/components/admin/AdminMembersPage";
import { adaptApiUserTeams } from "@/components/admin/types";

export const Route = createFileRoute("/_authenticated/_admin/admin/members")({
	component: AdminMembersContainer,
});

function AdminMembersContainer() {
	// no queryClient needed; page is read-only regarding team assignment

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
	const isLoading = usersLoading || teamsLoading;

	// Show error state if needed
	if (usersError || teamsError) {
		const errorMessage =
			(usersError as Error)?.message || (teamsError as Error)?.message;
		toast.error(`Failed to load data: ${errorMessage}`);
	}

	return <AdminMembersPage users={users} teams={teams} isLoading={isLoading} />;
}
