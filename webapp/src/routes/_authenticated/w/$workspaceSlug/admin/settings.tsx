import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	getWorkspaceOptions,
	listWorkspacesQueryKey,
	resetAndRecalculateLeaguesMutation,
	updateFeaturesMutation,
} from "@/api/@tanstack/react-query.gen";
import type { FeatureKey } from "@/components/admin/AdminFeaturesSettings";
import { AdminSettingsPage } from "@/components/admin/AdminSettingsPage";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/settings")({
	component: AdminSettings,
});

function AdminSettings() {
	const queryClient = useQueryClient();
	const { workspaceSlug, isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();

	const workspaceQueryOptions = getWorkspaceOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
	});
	const { data: workspaceData } = useQuery({
		...workspaceQueryOptions,
		enabled: Boolean(workspaceSlug),
	});

	// Reset leagues mutation
	const resetLeagues = useMutation({
		...resetAndRecalculateLeaguesMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["workspace"] });
		},
	});

	// Update features mutation
	const updateFeatures = useMutation({
		...updateFeaturesMutation(),
		onSuccess: () => {
			if (!workspaceSlug) {
				return;
			}
			queryClient.invalidateQueries({
				queryKey: workspaceQueryOptions.queryKey,
			});
			queryClient.invalidateQueries({
				queryKey: listWorkspacesQueryKey(),
			});
			toast.success("Feature settings updated");
		},
		onError: () => {
			toast.error("Failed to update feature settings");
		},
	});

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	// Handle feature toggle
	const handleToggleFeature = (feature: FeatureKey, enabled: boolean) => {
		if (!workspaceSlug) {
			return;
		}
		updateFeatures.mutate({
			path: { workspaceSlug },
			body: { [feature]: enabled },
		});
	};

	return (
		<AdminSettingsPage
			isResettingLeagues={resetLeagues.isPending}
			onResetLeagues={() => {
				if (!workspaceSlug) {
					return;
				}
				resetLeagues.mutate({ path: { workspaceSlug } });
			}}
			features={{
				practicesEnabled: workspaceData?.practicesEnabled ?? false,
				mentorEnabled: workspaceData?.mentorEnabled ?? false,
				achievementsEnabled: workspaceData?.achievementsEnabled ?? false,
				leaderboardEnabled: workspaceData?.leaderboardEnabled ?? false,
				progressionEnabled: workspaceData?.progressionEnabled ?? false,
				leaguesEnabled: workspaceData?.leaguesEnabled ?? false,
				practiceReviewAutoTriggerEnabled: workspaceData?.practiceReviewAutoTriggerEnabled ?? true,
				practiceReviewManualTriggerEnabled:
					workspaceData?.practiceReviewManualTriggerEnabled ?? true,
			}}
			isSavingFeatures={updateFeatures.isPending}
			onToggleFeature={handleToggleFeature}
		/>
	);
}
