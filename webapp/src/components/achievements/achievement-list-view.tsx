import {
	ArrowLeftRight,
	Bug,
	Building,
	Check,
	Circle,
	CircleDot,
	Crown,
	Eye,
	FileText,
	Flag,
	Flame,
	GitCommit,
	GitMerge,
	GitPullRequest,
	GraduationCap,
	HandHelping,
	HelpCircle,
	Layers,
	Lightbulb,
	ListChecks,
	Lock,
	Megaphone,
	MessageSquare,
	MessagesSquare,
	Pentagon,
	Radar,
	Radio,
	Rocket,
	RotateCcw,
	ScrollText,
	Shield,
	Sparkles,
	Star,
	Target,
	Timer,
	Triangle,
	Wrench,
	Zap,
} from "lucide-react";
import type React from "react";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import type { AchievementCategory, AchievementDTO, AchievementStatus } from "./types";
import { levelToTier, normalizeStatus } from "./types";

const iconMap: Record<string, React.ElementType> = {
	GitCommit,
	GitPullRequest,
	GitMerge,
	Eye,
	Shield,
	Sparkles,
	Bug,
	Target,
	Flag,
	MessageSquare,
	MessagesSquare,
	Zap,
	Crown,
	Flame,
	Rocket,
	Building,
	Lock,
	CircleDot,
	Radar,
	Megaphone,
	Radio,
	Layers,
	RotateCcw,
	ArrowLeftRight,
	Circle,
	ListChecks,
	ScrollText,
	GraduationCap,
	Wrench,
	HelpCircle,
	Lightbulb,
	FileText,
	Triangle,
	Pentagon,
	Star,
	HandHelping,
	Timer,
};

const categoryLabels: Record<AchievementCategory, string> = {
	COMMITS: "Commits",
	PULL_REQUESTS: "Pull Requests",
	REVIEWS: "Reviews",
	ISSUES: "Issues",
	COMMENTS: "Comments",
	CROSS_CATEGORY: "Cross-Category",
};

interface AchievementListViewProps {
	achievements: AchievementDTO[];
}

/**
 * Accessible list/table view of achievements.
 * Provides a screen-reader friendly alternative to the skill tree visualization.
 */
export function AchievementListView({ achievements }: AchievementListViewProps) {
	// Group achievements by category for better organization
	const groupedAchievements = achievements.reduce(
		(acc, achievement) => {
			const category = achievement.category;
			if (!acc[category]) {
				acc[category] = [];
			}
			acc[category].push(achievement);
			return acc;
		},
		{} as Record<AchievementCategory, AchievementDTO[]>,
	);

	// Sort categories in a logical order
	const categoryOrder: AchievementCategory[] = [
		"COMMITS",
		"PULL_REQUESTS",
		"REVIEWS",
		"ISSUES",
		"COMMENTS",
		"CROSS_CATEGORY",
	];

	const sortedCategories = categoryOrder.filter((cat) => groupedAchievements[cat]?.length > 0);

	const getStatusBadge = (status: AchievementStatus) => {
		const normalizedStatus = normalizeStatus(status);
		switch (normalizedStatus) {
			case "unlocked":
				return (
					<Badge variant="default" className="bg-green-600 hover:bg-green-700">
						<Check className="w-3 h-3 mr-1" />
						Unlocked
					</Badge>
				);
			case "available":
				return <Badge variant="secondary">Available</Badge>;
			default:
				return (
					<Badge variant="outline" className="text-muted-foreground">
						<Lock className="w-3 h-3 mr-1" />
						Locked
					</Badge>
				);
		}
	};

	const getTierLabel = (level: number) => {
		const tier = levelToTier(level);
		const tierLabels = {
			minor: "Minor",
			notable: "Notable",
			keystone: "Keystone",
			legendary: "Legendary",
		};
		return tierLabels[tier];
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
								(
								{
									groupedAchievements[category].filter(
										(a) => normalizeStatus(a.status) === "unlocked",
									).length
								}
								/{groupedAchievements[category].length})
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
								{groupedAchievements[category]
									.sort((a, b) => a.level - b.level)
									.map((achievement) => {
										const Icon = iconMap[achievement.icon] || GitCommit;
										const status = normalizeStatus(achievement.status);
										const progressPercent =
											achievement.maxProgress > 0
												? Math.round((achievement.progress / achievement.maxProgress) * 100)
												: 0;

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
														<div className="font-medium">{achievement.name}</div>
														<div className="text-sm text-muted-foreground">
															{achievement.description}
														</div>
													</div>
												</TableCell>
												<TableCell>
													<span className="text-sm">{getTierLabel(achievement.level)}</span>
												</TableCell>
												<TableCell>
													<div className="space-y-1">
														<Progress
															value={progressPercent}
															className="h-2"
															aria-label={`Progress: ${achievement.progress} of ${achievement.maxProgress}`}
														/>
														<div className="text-xs text-muted-foreground">
															{achievement.progress}/{achievement.maxProgress}
														</div>
													</div>
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
