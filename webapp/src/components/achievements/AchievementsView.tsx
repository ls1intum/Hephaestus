import { useQuery } from "@tanstack/react-query";
import { ReactFlowProvider } from "@xyflow/react";
import { useState } from "react";
import { getUserProfileOptions } from "@/api/@tanstack/react-query.gen";
import { AchievementSidebar } from "@/components/achievements/AchievementSidebar";
import { AchievementsListView } from "@/components/achievements/AchievementsListView";
import { SkillTree } from "@/components/achievements/SkillTree";
import type { ViewMode } from "@/components/achievements/types";
import { enhanceAchievements } from "@/components/achievements/utils";
import { useAchievementNotifications } from "@/hooks/use-achievement-notifications";
import { useAchievements } from "@/hooks/use-achievements";

export interface AchievementsViewProps {
	/** The workspace slug. */
	workspaceSlug: string;
	/** The username whose achievements to display. */
	targetUsername: string;
	/** Whether the viewer is looking at their own achievements. */
	isOwnProfile: boolean;
	/** Fallback display name (used for the avatar node when profile data hasn't loaded yet). */
	fallbackName?: string;
	/** Fallback avatar URL. */
	fallbackAvatarUrl?: string;
}

/**
 * Shared achievements view used by both the "my achievements" shortcut route
 * and the parameterized `/user/$username/achievements` route.
 */
export function AchievementsView({
	workspaceSlug,
	targetUsername,
	isOwnProfile,
	fallbackName,
	fallbackAvatarUrl,
}: AchievementsViewProps) {
	const [viewMode, setViewMode] = useState<ViewMode>("tree");

	// Fetch the target user's profile
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { workspaceSlug, login: targetUsername },
		}),
		enabled: Boolean(workspaceSlug) && Boolean(targetUsername),
	});

	// Fetch achievements from the API
	const achievementsQuery = useAchievements(workspaceSlug, targetUsername);

	const uiAchievements = enhanceAchievements(achievementsQuery.data ?? []);

	// Show toast notifications only for the current user's own achievements
	useAchievementNotifications(isOwnProfile ? uiAchievements : []);

	// Derived user data for the skill tree
	const user = {
		name: profileQuery.data?.userInfo?.name ?? fallbackName ?? targetUsername,
		avatarUrl: profileQuery.data?.userInfo?.avatarUrl ?? fallbackAvatarUrl ?? "",
		level: profileQuery.data?.xpRecord?.currentLevel ?? 1,
		leaguePoints: profileQuery.data?.userInfo?.leaguePoints ?? 0,
	};

	return (
		<ReactFlowProvider>
			<div className="h-[calc(100dvh-4rem)] flex bg-background overflow-hidden">
				{/* Main content area — fills remaining space */}
				<div className="flex-1 relative h-full flex flex-col overflow-hidden">
					{viewMode === "tree" ? (
						<>
							{/* Radial gradient background */}
							<div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,var(--tw-gradient-stops))] from-primary/5 via-background to-background" />

							{/* Accessibility hint for screen reader users */}
							<div className="sr-only" role="status">
								Skill tree visualization. Use the sidebar to switch to the accessible list view.
							</div>

							{/* Skill tree */}
							<SkillTree user={user} achievements={uiAchievements} />
						</>
					) : (
						<AchievementsListView achievements={uiAchievements} />
					)}
				</div>

				{/* Right sidebar — non-foldable, achievement controls + stats */}
				<AchievementSidebar
					viewMode={viewMode}
					onViewModeChange={setViewMode}
					isLoading={achievementsQuery.isLoading}
					isError={achievementsQuery.isError}
					achievements={uiAchievements}
					isOwnProfile={isOwnProfile}
					targetUsername={targetUsername}
				/>
			</div>
		</ReactFlowProvider>
	);
}
