import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { ReactFlowProvider } from "@xyflow/react";
import React from "react";
import { getUserProfileOptions } from "@/api/@tanstack/react-query.gen";
import { CategoryLabels } from "@/components/achievements/category-labels";
import { Header } from "@/components/achievements/header";
import { SkillTree } from "@/components/achievements/skill-tree";
import { StatsPanel } from "@/components/achievements/stats-panel";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useWorkspaceStore } from "@/stores/workspace-store";

export const Route = createFileRoute("/achievements")({
	component: AchievementsPage,
});

function AchievementsPage() {
	const { userProfile, getUserGithubProfilePictureUrl, username } = useAuth();
	const selectedSlug = useWorkspaceStore((state) => state.selectedSlug);

	// Attempt to fetch real profile data if we have a workspace context
	const profileQuery = useQuery({
		...getUserProfileOptions({
			path: { workspaceSlug: selectedSlug || "", login: username || "" },
		}),
		enabled: Boolean(selectedSlug) && Boolean(username),
	});

	const user = React.useMemo(
		() => ({
			name: profileQuery.data?.userInfo?.name || userProfile?.name || userProfile?.username,
			avatarUrl: profileQuery.data?.userInfo?.avatarUrl || getUserGithubProfilePictureUrl(),
			level: profileQuery.data?.xpRecord?.currentLevel ?? 1,
			leaguePoints: profileQuery.data?.userInfo?.leaguePoints ?? 0,
		}),
		[userProfile, getUserGithubProfilePictureUrl, profileQuery.data],
	);

	return (
		<ReactFlowProvider>
			<div className="h-screen flex flex-col bg-background overflow-hidden">
				<Header />

				<div className="flex-1 flex overflow-hidden">
					{/* Main skill tree area */}
					<div className="flex-1 relative">
						{/* Radial gradient background */}
						<div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-primary/5 via-background to-background" />

						{/* Decorative rings */}
						<div className="absolute inset-0 flex items-center justify-center pointer-events-none">
							<div className="w-[400px] h-[400px] rounded-full border border-primary/10" />
							<div className="absolute w-[600px] h-[600px] rounded-full border border-primary/10" />
							<div className="absolute w-[800px] h-[800px] rounded-full border border-primary/10" />
							<div className="absolute w-[1000px] h-[1000px] rounded-full border border-primary/5" />
							<div className="absolute w-[1200px] h-[1200px] rounded-full border border-primary/5" />
							<div className="absolute w-[1500px] h-[1500px] rounded-full border border-primary/5" />
						</div>

						{/* Category labels */}
						<CategoryLabels />

						{/* Skill tree */}
						<SkillTree user={user} />
					</div>

					{/* Stats panel */}
					<StatsPanel />
				</div>
			</div>
		</ReactFlowProvider>
	);
}
