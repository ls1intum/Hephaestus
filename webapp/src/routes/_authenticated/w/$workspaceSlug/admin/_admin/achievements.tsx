import { useQuery } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
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
});

function AdminAchievementsContainer() {
	const navigate = useNavigate();
	const {
		workspaceSlug,
		isLoading: isWorkspaceLoading,
		error: workspaceError,
	} = useActiveWorkspaceSlug();
	const { achievementsEnabled, isLoading: featuresLoading } = useWorkspaceFeatures();

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

	// Feature guard — redirect to admin settings when achievements are disabled
	useEffect(() => {
		if (!featuresLoading && !achievementsEnabled && workspaceSlug) {
			toast.error("Achievements are not enabled for this workspace");
			navigate({
				to: "/w/$workspaceSlug/admin/settings",
				params: { workspaceSlug },
				replace: true,
			});
		}
	}, [featuresLoading, achievementsEnabled, workspaceSlug, navigate]);

	const users = (usersData?.map(adaptApiUserTeams) || []).sort((a, b) =>
		a.user.name.localeCompare(b.user.name),
	);
	const isLoading = isWorkspaceLoading || usersLoading;

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	if (featuresLoading || !achievementsEnabled) {
		return (
			<div className="flex justify-center items-center h-64">
				<Spinner className="h-8 w-8" />
			</div>
		);
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
