import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { ReactFlowProvider } from "@xyflow/react";
import { getUserProfileOptions } from "@/api/@tanstack/react-query.gen";
import { CategoryLabels } from "@/components/achievements/category-labels";
import { Header } from "@/components/achievements/header";
import { SkillTree } from "@/components/achievements/skill-tree";
import { StatsPanel } from "@/components/achievements/stats-panel";
import { useAchievements } from "@/hooks/use-achievements";
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

	// Fetch achievements from the API
	const achievementsQuery = useAchievements(selectedSlug || "", username || "");

	// Derived user data for the skill tree (React Compiler handles memoization)
	const user = {
		name: profileQuery.data?.userInfo?.name || userProfile?.name || userProfile?.username,
		avatarUrl: profileQuery.data?.userInfo?.avatarUrl || getUserGithubProfilePictureUrl(),
		level: profileQuery.data?.xpRecord?.currentLevel ?? 1,
		leaguePoints: profileQuery.data?.userInfo?.leaguePoints ?? 0,
	};

	const achievements = achievementsQuery.data ?? [];

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
						<SkillTree user={user} achievements={achievements} />
					</div>

					{/* Stats panel */}
					<StatsPanel achievements={achievements} />
				</div>
			</div>
		</ReactFlowProvider>
	);
}
