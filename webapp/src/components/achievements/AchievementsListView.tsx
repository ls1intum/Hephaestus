import { Check, Lock } from "lucide-react";
import { AchievementProgressDisplay } from "@/components/achievements/AchievementProgressDisplay";
import { categoryLabels, rarityLabels, statusBackgrounds } from "@/components/achievements/styles";
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

export interface AchievementListViewProps {
	achievements: UIAchievement[];
}

export function AchievementsListView({ achievements }: AchievementListViewProps) {
	const groupedAchievements = achievements.reduce<
		Partial<Record<AchievementCategory, UIAchievement[]>>
	>((acc, achievement) => {
		const bucket = acc[achievement.category] ?? [];
		bucket.push(achievement);
		acc[achievement.category] = bucket;
		return acc;
	}, {});

	const sortedCategories = ACHIEVEMENT_CATEGORIES.filter(
		(cat) => (groupedAchievements[cat]?.length ?? 0) > 0,
	);

	const getStatusBadge = (status: AchievementStatus) => {
		switch (status) {
			case "unlocked":
				return (
					<Badge
						variant="default"
						className="bg-provider-success-foreground hover:bg-provider-success-foreground/80"
					>
						<Check className="w-3 h-3 mr-1" aria-hidden="true" />
						Unlocked
					</Badge>
				);
			case "available":
				return <Badge variant="secondary">Available</Badge>;
			case "locked":
				return (
					<Badge variant="outline" className="text-muted-foreground">
						<Lock className="w-3 h-3 mr-1" aria-hidden="true" />
						Locked
					</Badge>
				);
			default:
				return null;
		}
	};

	return (
		<div
			className="h-full min-h-0 flex-1 overflow-auto p-4 sm:p-6"
			role="region"
			aria-label="Achievement list"
		>
			<div className="max-w-4xl mx-auto space-y-8">
				{sortedCategories.map((category) => (
					<section key={category} aria-labelledby={`category-${category}`}>
						<h2
							id={`category-${category}`}
							className="text-lg font-semibold mb-4 flex items-center gap-2"
						>
							{categoryLabels[category]}
							<span className="text-sm font-normal text-muted-foreground">
								({groupedAchievements[category]?.filter((a) => a.status === "unlocked").length}/
								{groupedAchievements[category]?.length})
							</span>
						</h2>

						<Table aria-labelledby={`category-${category}`}>
							<TableHeader>
								<TableRow>
									<TableHead className="w-12">Icon</TableHead>
									<TableHead>Achievement</TableHead>
									<TableHead className="w-24">Rarity</TableHead>
									<TableHead className="w-32">Progress</TableHead>
									<TableHead className="w-28">Status</TableHead>
								</TableRow>
							</TableHeader>
							<TableBody>
								{[...(groupedAchievements[category] ?? [])]
									.sort(compareByRarity)
									.map((achievement) => {
										const Icon = achievement.icon;
										const status = achievement.status;

										return (
											<TableRow key={achievement.id}>
												<TableCell>
													<div
														className={cn(
															"w-10 h-10 rounded-full flex items-center justify-center shrink-0",
															statusBackgrounds[status],
															status === "unlocked" ? "text-background" : "text-foreground",
														)}
													>
														<Icon size={20} className="shrink-0" aria-hidden="true" />
													</div>
												</TableCell>
												<TableCell>
													<div>
														<div className="font-medium">{achievement.name}</div>
														<div className="text-sm text-muted-foreground">
															{achievement.description}
														</div>
													</div>
												</TableCell>
												<TableCell>
													<span className="text-sm font-semibold text-foreground">
														{rarityLabels[achievement.rarity]}
													</span>
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
