import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import { getUsersWithTeamsOptions } from "@/api/@tanstack/react-query.gen";
import { AdminAchievementsPage } from "@/components/admin/AdminAchievementsPage";
import { adaptApiUserTeams } from "@/components/admin/types";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/achievements")({
	component: AdminAchievementsContainer,
});

function AdminAchievementsContainer() {
	const {
		workspaceSlug,
		isLoading: isWorkspaceLoading,
		error: workspaceError,
	} = useActiveWorkspaceSlug();

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

	const users = (usersData?.map(adaptApiUserTeams) || []).sort((a, b) =>
		a.user.name.localeCompare(b.user.name),
	);
	const isLoading = isWorkspaceLoading || usersLoading;

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	if (workspaceError || usersError) {
		const errorMessage = (workspaceError as Error)?.message || (usersError as Error)?.message;
		toast.error(`Failed to load data: ${errorMessage}`);
	}

	return (
		<AdminAchievementsPage
			users={users}
			isLoading={isLoading || !workspaceSlug}
			workspaceSlug={workspaceSlug ?? ""}
		/>
	);
}
