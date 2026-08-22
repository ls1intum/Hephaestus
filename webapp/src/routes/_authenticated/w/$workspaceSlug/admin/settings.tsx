import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { Settings2 } from "lucide-react";
import {
	getWorkspaceOptions,
	resetAndRecalculateLeaguesMutation,
} from "@/api/@tanstack/react-query.gen";
import type { FeatureKey } from "@/components/admin/AdminFeaturesSettings";
import { AdminSettingsPage } from "@/components/admin/AdminSettingsPage";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useUpdateWorkspaceFeatures } from "@/hooks/use-update-workspace-features";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/settings")({
	head: workspaceAdminHead("Workspace settings"),
	component: AdminSettings,
});

function AdminSettings() {
	const queryClient = useQueryClient();
	const { workspaceSlug, isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();

	const workspaceQueryOptions = getWorkspaceOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
	});
	const workspaceQuery = useQuery({
		...workspaceQueryOptions,
		enabled: Boolean(workspaceSlug),
	});

	const resetLeagues = useMutation({
		...resetAndRecalculateLeaguesMutation(),
		onSuccess: (_data, variables) => {
			const resetSlug = variables.path.workspaceSlug;
			void queryClient.invalidateQueries({
				queryKey: [{ tags: ["Leaderboard"], path: { workspaceSlug: resetSlug } }],
			});
		},
	});

	const updateFeatures = useUpdateWorkspaceFeatures(workspaceSlug ?? "", {
		success: "Feature settings updated",
		error: "Failed to update feature settings",
	});

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	const workspaceData = workspaceQuery.data;

	const handleToggleFeature = (feature: FeatureKey, enabled: boolean) => {
		if (!workspaceSlug) return;
		updateFeatures.mutate({
			path: { workspaceSlug },
			body: { [feature]: enabled },
		});
	};

	return (
		<PageLayout>
			<PageHeader
				icon={<Settings2 />}
				title="Workspace settings"
				description="Configure workspace features, leagues, and lifecycle."
			/>
			{!workspaceSlug || workspaceQuery.isLoading ? (
				<div className="flex h-40 max-w-4xl items-center justify-center">
					<Spinner className="size-6" />
				</div>
			) : workspaceQuery.isError || !workspaceData ? (
				<div className="max-w-4xl">
					<QueryErrorAlert
						error={workspaceQuery.error}
						title="Couldn't load workspace settings"
						onRetry={() => void workspaceQuery.refetch()}
					/>
				</div>
			) : (
				<AdminSettingsPage
					isResettingLeagues={resetLeagues.isPending}
					onResetLeagues={() => {
						resetLeagues.mutate({ path: { workspaceSlug } });
					}}
					features={{
						mentorEnabled: workspaceData.mentorEnabled,
						achievementsEnabled: workspaceData.achievementsEnabled,
						leaderboardEnabled: workspaceData.leaderboardEnabled,
						progressionEnabled: workspaceData.progressionEnabled,
						leaguesEnabled: workspaceData.leaguesEnabled,
					}}
					isSavingFeatures={updateFeatures.isPending}
					onToggleFeature={handleToggleFeature}
					workspaceSlug={workspaceSlug}
				/>
			)}
		</PageLayout>
	);
}
