import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { ReactFlowProvider } from "@xyflow/react";
import { getUserProfileOptions } from "@/api/@tanstack/react-query.gen";
import { AchievementHeader } from "@/components/achievements/AchievementHeader";
import { SkillTreeDesigner } from "@/components/achievements/SkillTreeDesigner";
import { useAllAchievementDefinitions } from "@/hooks/use-all-achievement-definitions";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useWorkspaceStore } from "@/stores/workspace-store";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/achievement-designer")({
	component: AchievementDesignerPage,
});

function AchievementDesignerPage() {
	const { userProfile, getUserGithubProfilePictureUrl, username } = useAuth();
	const selectedSlug = useWorkspaceStore((state) => state.selectedSlug);

	// Fetch real profile data if we have a workspace context
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { workspaceSlug: selectedSlug || "", login: username || "" },
		}),
		enabled: Boolean(selectedSlug) && Boolean(username),
	});

	// Fetch all achievement definitions (Design Mode Data)
	const allDefinitionsQuery = useAllAchievementDefinitions(selectedSlug || "", username || "");

	// Derived user data for the skill tree
	const user = {
		name: profileQuery.data?.userInfo?.name || userProfile?.name || userProfile?.username || "",
		avatarUrl: profileQuery.data?.userInfo?.avatarUrl || getUserGithubProfilePictureUrl(),
		level: profileQuery.data?.xpRecord?.currentLevel ?? 1,
		leaguePoints: profileQuery.data?.userInfo?.leaguePoints ?? 0,
	};

	return (
		<ReactFlowProvider>
			<div className="h-screen flex flex-col bg-background overflow-hidden">
				<AchievementHeader showZoomControls={true} isError={allDefinitionsQuery.isError} isLoading={allDefinitionsQuery.isLoading} />

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
