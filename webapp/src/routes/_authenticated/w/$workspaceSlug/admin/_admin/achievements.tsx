import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Navigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { toast } from "sonner";
import { getUsersWithTeamsOptions } from "@/api/@tanstack/react-query.gen";
import { AdminAchievementsPage } from "@/components/admin/AdminAchievementsPage";
import { adaptApiUserTeams } from "@/components/admin/types";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/achievements")({
	component: AdminAchievementsContainer,
	staticData: {
		workspaceSwitch: { target: "admin.achievements" },
	},
});

function AdminAchievementsContainer() {
	const { workspaceSlug } = Route.useParams();
	const { isLoading: isWorkspaceLoading, error: workspaceError } = useActiveWorkspaceSlug();
	const { achievementsEnabled, isLoading: featuresLoading } = useWorkspaceFeatures(workspaceSlug);

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

	// Show error toasts via useEffect (not in render path)
	useEffect(() => {
		if (workspaceError || usersError) {
			const errorMessage = (workspaceError as Error)?.message || (usersError as Error)?.message;
			toast.error(`Failed to load data: ${errorMessage}`);
		}
	}, [workspaceError, usersError]);

	const users = (usersData?.map(adaptApiUserTeams) || []).sort((a, b) =>
		a.user.name.localeCompare(b.user.name),
	);
	const isLoading = isWorkspaceLoading || usersLoading;

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	// Feature guard — declarative redirect when achievements are disabled
	if (!featuresLoading && !achievementsEnabled && workspaceSlug) {
		return <Navigate to="/w/$workspaceSlug/admin/settings" params={{ workspaceSlug }} replace />;
	}

	if (featuresLoading || !achievementsEnabled) {
		return (
			<div className="flex justify-center items-center h-64">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	return (
		<AdminAchievementsPage
			users={users}
			isLoading={isLoading || !workspaceSlug}
			workspaceSlug={workspaceSlug ?? ""}
		/>
	);
}
