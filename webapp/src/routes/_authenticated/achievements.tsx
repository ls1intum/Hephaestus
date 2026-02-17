import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { ReactFlowProvider } from "@xyflow/react";
import { useState } from "react";
import { getUserProfileOptions } from "@/api/@tanstack/react-query.gen";
import { AchievementHeader } from "@/components/achievements/AchievementHeader.tsx";
import { AchievementsListView } from "@/components/achievements/AchievementsListView.tsx";
import { CategoryLabels } from "@/components/achievements/CategoryLabels.tsx";
import { SkillTree } from "@/components/achievements/SkillTree.tsx";
import { StatsPanel } from "@/components/achievements/stats-panel";
import type { ViewMode } from "@/components/achievements/types";
import { enhanceAchievements } from "@/components/achievements/utils.ts";
import { useAchievementNotifications } from "@/hooks/use-achievement-notifications";
import { useAchievements } from "@/hooks/use-achievements";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useWorkspaceStore } from "@/stores/workspace-store";

export const Route = createFileRoute("/_authenticated/achievements")({
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
				/>

				<div className="flex-1 flex overflow-hidden">
					{/* Main content area */}
					<div className="flex-1 relative">
						{viewMode === "tree" ? (
							<>
								{/* Radial gradient background */}
								<div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,var(--tw-gradient-stops))] from-primary/5 via-background to-background" />

								{/* Decorative rings */}
								<div className="absolute inset-0 flex items-center justify-center pointer-events-none">
									<div className="w-100 h-100 rounded-full border border-primary/10" />
									<div className="absolute w-150 h-150 rounded-full border border-primary/10" />
									<div className="absolute w-200 h-200 rounded-full border border-primary/10" />
									<div className="absolute w-250 h-250 rounded-full border border-primary/5" />
									<div className="absolute w-300 h-300 rounded-full border border-primary/5" />
									<div className="absolute w-375 h-375 rounded-full border border-primary/5" />
								</div>

								{/* Category labels */}
								<CategoryLabels />

								{/* Loading/error states */}
								{achievementsQuery.isLoading && (
									<div className="absolute inset-0 flex items-center justify-center z-10">
										<div className="text-muted-foreground">Loading achievements...</div>
									</div>
								)}

								{achievementsQuery.isError && (
									<div className="absolute inset-0 flex items-center justify-center z-10">
										<div className="text-destructive">
											Failed to load achievements. Please try again.
										</div>
									</div>
								)}

								{/* Skill tree */}
								<SkillTree user={user} achievements={uiAchievements} />
							</>
						) : (
							<>
								{/* Loading/error states for list view */}
								{achievementsQuery.isLoading && (
									<div className="flex-1 flex items-center justify-center">
										<div className="text-muted-foreground">Loading achievements...</div>
									</div>
								)}

								{achievementsQuery.isError && (
									<div className="flex-1 flex items-center justify-center">
										<div className="text-destructive">
											Failed to load achievements. Please try again.
										</div>
									</div>
								)}

								{/* List view */}
								{!achievementsQuery.isLoading && !achievementsQuery.isError && (
									<AchievementsListView achievements={uiAchievements} />
								)}
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
