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
	workspaceSlug: string;
	targetUsername: string;
	isOwnProfile: boolean;
	fallbackName?: string;
	fallbackAvatarUrl?: string;
}

export function AchievementsView({
	workspaceSlug,
	targetUsername,
	isOwnProfile,
	fallbackName,
	fallbackAvatarUrl,
}: AchievementsViewProps) {
	const [viewMode, setViewMode] = useState<ViewMode>("tree");

	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { workspaceSlug, login: targetUsername },
		}),
		enabled: Boolean(workspaceSlug) && Boolean(targetUsername),
	});

	const achievementsQuery = useAchievements(workspaceSlug, targetUsername);

	const uiAchievements = enhanceAchievements(achievementsQuery.data ?? []);

	useAchievementNotifications(isOwnProfile ? uiAchievements : []);

	const user = {
		name: profileQuery.data?.userInfo?.name ?? fallbackName ?? targetUsername,
		avatarUrl: profileQuery.data?.userInfo?.avatarUrl ?? fallbackAvatarUrl ?? "",
		level: profileQuery.data?.xpRecord?.currentLevel ?? 1,
		leaguePoints: profileQuery.data?.userInfo?.leaguePoints ?? 0,
	};

	return (
		<ReactFlowProvider>
			<div className="flex h-full overflow-hidden bg-background">
				<div className="flex-1 relative h-full flex flex-col overflow-hidden">
					{viewMode === "tree" ? (
						<>
							<div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,var(--tw-gradient-stops))] from-primary/5 via-background to-background" />

							<div className="sr-only" role="status">
								Skill tree visualization. Use the sidebar to switch to the accessible list view.
							</div>

							<SkillTree user={user} achievements={uiAchievements} />
						</>
					) : (
						<AchievementsListView achievements={uiAchievements} />
					)}
				</div>

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
