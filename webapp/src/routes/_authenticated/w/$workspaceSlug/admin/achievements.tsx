import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Navigate } from "@tanstack/react-router";
import { getUsersWithTeamsOptions } from "@/api/@tanstack/react-query.gen";
import { AdminAchievementsPage } from "@/components/admin/AdminAchievementsPage";
import { adaptApiUserTeams } from "@/components/admin/types";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/achievements")({
	head: workspaceAdminHead("Achievements"),
	component: AdminAchievementsContainer,
});

function AdminAchievementsContainer() {
	const {
		workspaceSlug,
		isLoading: isWorkspaceLoading,
		error: workspaceError,
	} = useActiveWorkspaceSlug();
	const featureState = useWorkspaceFeatures(workspaceSlug);
	const achievementsEnabled = featureState.features?.achievementsEnabled;

	const usersQueryOptions = getUsersWithTeamsOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
	});
	const {
		data: usersData,
		isLoading: usersLoading,
		error: usersError,
		refetch: refetchUsers,
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

	if (
		!featureState.isLoading &&
		!featureState.isError &&
		achievementsEnabled === false &&
		workspaceSlug
	) {
		return <Navigate to="/w/$workspaceSlug/admin/settings" params={{ workspaceSlug }} replace />;
	}

	return (
		<AdminAchievementsPage
			users={users}
			isLoading={isLoading || featureState.isLoading || achievementsEnabled !== true}
			workspaceSlug={workspaceSlug ?? ""}
			error={featureState.error ?? workspaceError ?? usersError}
			onRetry={() => {
				featureState.refetch();
				refetchUsers();
			}}
		/>
	);
}
