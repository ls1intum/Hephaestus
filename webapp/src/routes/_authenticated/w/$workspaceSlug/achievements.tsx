import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { ReactFlowProvider } from "@xyflow/react";
import { useState } from "react";
import { getUserProfileOptions } from "@/api/@tanstack/react-query.gen";
import { AchievementHeader } from "@/components/achievements/AchievementHeader.tsx";
import { AchievementsListView } from "@/components/achievements/AchievementsListView.tsx";
import { SkillTree } from "@/components/achievements/SkillTree.tsx";
import { StatsPanel } from "@/components/achievements/stats-panel";
import type { ViewMode } from "@/components/achievements/types";
import { enhanceAchievements } from "@/components/achievements/utils.ts";
import { useAchievementNotifications } from "@/hooks/use-achievement-notifications";
import { useAchievements } from "@/hooks/use-achievements";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useWorkspaceStore } from "@/stores/workspace-store";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/achievements")({
	component: AchievementsPage,
});

function AchievementsPage() {
	const { userProfile, getUserGithubProfilePictureUrl, username } = useAuth();
	const selectedSlug = useWorkspaceStore((state) => state.selectedSlug);
	const [viewMode, setViewMode] = useState<ViewMode>("tree");

	// Attempt to fetch real profile data if we have a workspace context
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { workspaceSlug: selectedSlug || "", login: username || "" },
		}),
		enabled: Boolean(selectedSlug) && Boolean(username),
	});

	// Fetch achievements from the API
	const achievementsQuery = useAchievements(selectedSlug || "", username || "");

	const uiAchievements = enhanceAchievements(achievementsQuery.data ?? []);

	// Show toast notifications when achievements are unlocked
	useAchievementNotifications(uiAchievements);

	// Derived user data for the skill tree (React Compiler handles memoization)
	const user = {
		name: profileQuery.data?.userInfo?.name || userProfile?.name || userProfile?.username || "",
		avatarUrl: profileQuery.data?.userInfo?.avatarUrl || getUserGithubProfilePictureUrl(),
		level: profileQuery.data?.xpRecord?.currentLevel ?? 1,
		leaguePoints: profileQuery.data?.userInfo?.leaguePoints ?? 0,
	};

	return (
		<ReactFlowProvider>
			<div className="h-screen flex flex-col bg-background overflow-hidden">
				<AchievementHeader
					viewMode={viewMode}
					onViewModeChange={setViewMode}
					showZoomControls={viewMode === "tree"}
					isError={achievementsQuery.isError}
					isLoading={achievementsQuery.isLoading}
				/>

				<div className="flex-1 flex overflow-hidden">
					{/* Main content area */}
					<div className="flex-1 relative">
						{viewMode === "tree" ? (
							<>
								{/* Radial gradient background */}
								<div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,var(--tw-gradient-stops))] from-primary/5 via-background to-background" />





								{/* Skill tree */}
								<SkillTree
									user={user}
									achievements={uiAchievements}
								/>
							</>
						) : (
							<>
								{/* List view */}
								<AchievementsListView achievements={uiAchievements} />
							</>
						)}
					</div>

					{/* Stats panel - visible in both views */}
					<StatsPanel achievements={uiAchievements} />
				</div>
			</div>
		</ReactFlowProvider>
	);
}
