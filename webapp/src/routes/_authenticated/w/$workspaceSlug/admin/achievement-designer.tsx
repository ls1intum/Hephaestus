import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Navigate } from "@tanstack/react-router";
import { ReactFlowProvider } from "@xyflow/react";
import { toast } from "sonner";
import { getUserProfileOptions, reloadAchievementsMutation } from "@/api/@tanstack/react-query.gen";
import { AchievementHeader } from "@/components/achievements/AchievementHeader";
import { SkillTreeDesigner } from "@/components/achievements/SkillTreeDesigner";
import { Spinner } from "@/components/ui/spinner";
import { useAllAchievementDefinitions } from "@/hooks/use-all-achievement-definitions";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useWorkspaceStore } from "@/stores/workspace-store";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/achievement-designer")(
	{
		component: AchievementDesignerPage,
	},
);

function AchievementDesignerPage() {
	const queryClient = useQueryClient();
	const { userProfile, getUserProfilePictureUrl, username } = useAuth();
	const selectedSlug = useWorkspaceStore((state) => state.selectedSlug);
	const { achievementsEnabled, isLoading: featuresLoading } = useWorkspaceFeatures();

	const reloadMutation = useMutation({
		...reloadAchievementsMutation(),
		onSuccess: () => {
			toast.promise(Promise.resolve(), {
				loading: "Reloading achievement definitions...",
				success: () => {
					// Invalidate both definitions and user progress queries
					queryClient.invalidateQueries({
						predicate: (query) => {
							const id = (query.queryKey[0] as { _id?: string } | undefined)?._id;
							return id === "getUserAchievements" || id === "getAllAchievementDefinitions";
						},
					});
					return "Successfully reloaded achievements from YAML";
				},
				error: "Failed to reload achievement definitions",
			});
		},
	});

	const handleReload = () => {
		reloadMutation.mutate({
			path: {
				workspaceSlug: selectedSlug || "",
				login: username || "",
			},
		});
	};

	// Fetch real profile data if we have a workspace context
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { workspaceSlug: selectedSlug || "", login: username || "" },
		}),
		enabled: Boolean(selectedSlug) && Boolean(username),
	});

	// Fetch all achievement definitions (Design Mode Data)
	const allDefinitionsQuery = useAllAchievementDefinitions(selectedSlug || "", username || "");

	// Feature guard — declarative redirect when achievements are disabled
	if (!featuresLoading && !achievementsEnabled && selectedSlug) {
		return (
			<Navigate
				to="/w/$workspaceSlug/admin/settings"
				params={{ workspaceSlug: selectedSlug }}
				replace
			/>
		);
	}

	if (featuresLoading || !achievementsEnabled) {
		return (
			<div className="flex justify-center items-center h-64">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	// Derived user data for the skill tree
	const user = {
		name: profileQuery.data?.userInfo?.name || userProfile?.name || userProfile?.username || "",
		avatarUrl: profileQuery.data?.userInfo?.avatarUrl || getUserProfilePictureUrl(),
		level: profileQuery.data?.xpRecord?.currentLevel ?? 1,
		leaguePoints: profileQuery.data?.userInfo?.leaguePoints ?? 0,
	};

	return (
		<ReactFlowProvider>
			<div className="h-screen flex flex-col bg-background overflow-hidden">
				<AchievementHeader
					showZoomControls={true}
					isError={allDefinitionsQuery.isError}
					isLoading={allDefinitionsQuery.isLoading}
					onReload={handleReload}
					isReloading={reloadMutation.isPending}
				/>

				<div className="flex-1 flex overflow-hidden">
					<div className="flex-1 relative">
						{/* Radial gradient background */}
						<div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,var(--tw-gradient-stops))] from-primary/5 via-background to-background" />

						{/* Skill tree (Designer implementation) */}
						<SkillTreeDesigner user={user} allDefinitions={allDefinitionsQuery.data} />
					</div>
				</div>
			</div>
		</ReactFlowProvider>
	);
}
