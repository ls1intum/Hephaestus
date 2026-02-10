import {
	CircleDot,
	Flame,
	GitCommit,
	GitPullRequest,
	Layers,
	MessageSquare,
	Trophy,
} from "lucide-react";
import type React from "react";
import type { Achievement } from "@/api/types.gen";
import { Progress, ProgressIndicator, ProgressTrack } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import type { AchievementCategory } from "./achievements.config.ts";
import { calculateStats, categoryMeta } from "./data";

const categoryIcons: Record<AchievementCategory, React.ElementType> = {
	pull_requests: GitPullRequest,
	commits: GitCommit,
	communication: MessageSquare,
	issues: CircleDot,
	milestones: Layers,
};

export interface StatsPanelProps {
	achievements: Achievement[];
}

export function StatsPanel({ achievements }: StatsPanelProps) {
	const stats = calculateStats(achievements);

	return (
		<div className="w-80 bg-card/80 backdrop-blur-sm border-l border-border p-6 overflow-y-auto">
			{/* Header */}
			<div className="mb-6">
				<h2 className="text-xl font-bold text-foreground flex items-center gap-2">
					<Trophy className="w-5 h-5 text-foreground" />
					Your Progress
				</h2>
				<p className="text-sm text-muted-foreground mt-1">Grow your contributions</p>
			</div>

			{/* Overall Progress */}
			<div className="mb-8 p-4 rounded-lg bg-secondary/50 border border-border">
				<div className="flex items-center justify-between mb-2">
					<span className="text-sm font-medium text-foreground">Total Progress</span>
					<span className="text-lg font-bold text-foreground">{stats.percentage}%</span>
				</div>
				<Progress value={stats.percentage}>
					<ProgressTrack className="h-3 [&>div]:bg-foreground">
						<ProgressIndicator />
					</ProgressTrack>
				</Progress>
				<div className="flex justify-between mt-2 text-xs text-muted-foreground">
					<span>
						{stats.unlocked} / {stats.total} Achievements
					</span>
					<span>{stats.available} Available</span>
				</div>
			</div>

			{/* Category Breakdown */}
			<div className="space-y-4">
				<h3 className="text-sm font-semibold text-foreground uppercase tracking-wider">
					Categories
				</h3>

				{Object.entries(categoryMeta).map(([key, meta]) => {
					const category = key as AchievementCategory;
					const catStats = stats.byCategory[category];
					// Handle case where category has no achievements
					if (!catStats || catStats.total === 0) return null;
					const Icon = categoryIcons[category];
					const percentage = Math.round((catStats.unlocked / catStats.total) * 100);

					return (
						<div
							key={category}
							className="p-3 rounded-lg bg-secondary/30 border border-border/50 hover:border-border transition-colors"
						>
							<div className="flex items-center gap-3 mb-2">
								<div className="w-8 h-8 rounded-full flex items-center justify-center bg-foreground/10 text-foreground">
									<Icon className="w-4 h-4" />
								</div>
								<div className="flex-1">
									<div className="flex items-center justify-between">
										<span className="text-sm font-medium text-foreground">{meta.name}</span>
										<span className="text-xs text-muted-foreground">
											{catStats.unlocked}/{catStats.total}
										</span>
									</div>
								</div>
							</div>
							<Progress value={percentage}>
								<ProgressTrack className="h-1.5 [&>div]:bg-foreground/70">
									<ProgressIndicator />
								</ProgressTrack>
							</Progress>
						</div>
					);
				})}
			</div>

			{/* Recent Achievements */}
			<div className="mt-8">
				<h3 className="text-sm font-semibold text-foreground uppercase tracking-wider mb-4">
					Recent Unlocks
				</h3>
				<div className="space-y-2">
					{achievements
						.filter((a) => a.status === "unlocked" && a.unlockedAt)
						.sort((a, b) => {
							const rawDateA = a.unlockedAt;
							const rawDateB = b.unlockedAt;
							const dateA = rawDateA instanceof Date ? rawDateA : new Date(String(rawDateA));
							const dateB = rawDateB instanceof Date ? rawDateB : new Date(String(rawDateB));
							return dateB.getTime() - dateA.getTime();
						})
						.slice(0, 5)
						.map((achievement) => {
							const rarity = achievement.rarity ?? "common";
							const rawDate = achievement.unlockedAt;
							const unlockedDate = rawDate instanceof Date ? rawDate : new Date(String(rawDate));
							return (
								<div
									key={achievement.id}
									className="flex items-center gap-3 p-2 rounded-lg bg-secondary/20 hover:bg-secondary/40 transition-colors"
								>
									{/* TODO: Rethink rarity styling here*/}
									<div
										className={cn(
											"w-6 h-6 rounded-full flex items-center justify-center",
											rarity === "legendary" && "bg-foreground text-background",
											rarity === "epic" && "bg-foreground/80 text-background",
											rarity === "rare" && "bg-foreground/60 text-background",
											rarity === "uncommon" && "bg-foreground/40 text-background",
											rarity === "common" && "bg-foreground/20 text-background",
										)}
									>
										<Flame className="w-3 h-3" />
									</div>
									<div className="flex-1 min-w-0">
										<p className="text-sm font-medium text-foreground truncate">
											{achievement.name}
										</p>
										<p className="text-xs text-muted-foreground">
											{unlockedDate.toLocaleDateString()}
										</p>
									</div>
								</div>
							);
						})}
				</div>
			</div>

			{/* Legend */}
			{/*TODO: Rethink legend definitions here*/}
			<div className="mt-8 pt-6 border-t border-border">
				<h3 className="text-sm font-semibold text-foreground uppercase tracking-wider mb-3">
					Legend
				</h3>
				<div className="space-y-2 text-xs">
					<div className="flex items-center gap-2">
						<div className="w-3 h-3 rounded-full bg-foreground shadow-[0_0_6px_rgba(var(--shadow-rgb),0.3)]" />
						<span className="text-muted-foreground">Unlocked</span>
					</div>
					<div className="flex items-center gap-2">
						<div className="w-3 h-3 rounded-full bg-foreground/40 animate-pulse" />
						<span className="text-muted-foreground">Available</span>
					</div>
					<div className="flex items-center gap-2">
						<div className="w-3 h-3 rounded-full bg-muted opacity-50" />
						<span className="text-muted-foreground">Locked</span>
					</div>
					<div className="flex items-center gap-2">
						<div className="w-3 h-3 rounded-full bg-foreground shadow-[0_0_8px_rgba(var(--shadow-rgb),0.5)]" />
						<span className="text-muted-foreground">Legendary</span>
					</div>
				</div>
			</div>
		</div>
	);
}
