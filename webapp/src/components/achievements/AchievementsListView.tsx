import { Check, Lock } from "lucide-react";
import { AchievementProgressDisplay } from "@/components/achievements/AchievementProgressDisplay.tsx";
import { categoryLabels } from "@/components/achievements/styles.ts";
import type {
	AchievementCategory,
	AchievementStatus,
	UIAchievement,
} from "@/components/achievements/types";
import { ACHIEVEMENT_CATEGORIES, compareByRarity } from "@/components/achievements/utils";
import { Badge } from "@/components/ui/badge";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";

interface AchievementListViewProps {
	achievements: UIAchievement[];
}

/**
 * Accessible list/table view of achievements.
 * Provides a screen-reader friendly alternative to the skill tree visualization.
 */
export function AchievementsListView({ achievements }: AchievementListViewProps) {
	// Group achievements by category for better organization
	const groupedAchievements = achievements.reduce(
		(acc, achievement) => {
			const category = achievement.category;
			if (!category) return acc;
			if (!acc[category]) {
				acc[category] = [];
			}
			acc[category].push(achievement);
			return acc;
		},
		{} as Record<AchievementCategory, UIAchievement[]>,
	);

	const sortedCategories = ACHIEVEMENT_CATEGORIES.filter(
		(cat) => groupedAchievements[cat]?.length > 0,
	);

	const getStatusBadge = (status: AchievementStatus) => {
		switch (status) {
			case "unlocked":
				return (
					<Badge variant="default" className="bg-green-600 hover:bg-green-700">
						<Check className="w-3 h-3 mr-1" />
						Unlocked
					</Badge>
				);
			case "available":
				return <Badge variant="secondary">Available</Badge>;
			case "locked":
				return (
					<Badge variant="outline" className="text-muted-foreground">
						<Lock className="w-3 h-3 mr-1" />
						Locked
					</Badge>
				);
			default: // case "hidden"
		}
	};

	return (
		<div className="flex-1 overflow-auto p-6" role="region" aria-label="Achievement list">
			<div className="max-w-4xl mx-auto space-y-8">
				{sortedCategories.map((category) => (
					<section key={category} aria-labelledby={`category-${category}`}>
						<h2
							id={`category-${category}`}
							className="text-lg font-semibold mb-4 flex items-center gap-2"
						>
							{categoryLabels[category]}
							<span className="text-sm font-normal text-muted-foreground">
								({groupedAchievements[category].filter((a) => a.status === "unlocked").length}/
								{groupedAchievements[category].length})
							</span>
						</h2>

						<Table>
							<TableHeader>
								<TableRow>
									<TableHead className="w-12">Icon</TableHead>
									<TableHead>Achievement</TableHead>
									<TableHead className="w-24">Tier</TableHead>
									<TableHead className="w-32">Progress</TableHead>
									<TableHead className="w-28">Status</TableHead>
								</TableRow>
							</TableHeader>
							<TableBody>
								{groupedAchievements[category].sort(compareByRarity).map((achievement) => {
									const Icon = achievement.icon;
									const status = achievement.status;

									return (
										<TableRow
											key={achievement.id}
											className={cn(status === "locked" && "opacity-60")}
										>
											<TableCell>
												<div
													className={cn(
														"w-8 h-8 rounded-full flex items-center justify-center",
														status === "unlocked" && "bg-green-600 text-white",
														status === "available" && "bg-secondary",
														status === "locked" && "bg-muted",
													)}
												>
													<Icon className="w-4 h-4" />
												</div>
											</TableCell>
											<TableCell>
												<div>
													<div className="font-medium">{achievement.name ?? "Unknown"}</div>
													<div className="text-sm text-muted-foreground">
														{achievement.description}
													</div>
												</div>
											</TableCell>
											<TableCell>
												<span className="text-sm capitalize">{achievement.rarity}</span>
											</TableCell>
											<TableCell>
												<AchievementProgressDisplay achievement={achievement} />
											</TableCell>
											<TableCell>{getStatusBadge(achievement.status)}</TableCell>
										</TableRow>
									);
								})}
							</TableBody>
						</Table>
					</section>
				))}

				{sortedCategories.length === 0 && (
					<div className="text-center text-muted-foreground py-12">
						No achievements available yet.
					</div>
				)}
			</div>
		</div>
	);
}
