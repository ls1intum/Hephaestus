import type { ReactNode } from "react";
import { AchievementProgressDisplay } from "@/components/achievements/AchievementProgressDisplay.tsx";
import { rarityColors, rarityLabels, statusIcons } from "@/components/achievements/styles";
import type { UIAchievement } from "@/components/achievements/types.ts";
import { cn } from "@/lib/utils";

export interface AchievementTooltipProps {
	achievement: UIAchievement;
	children: ReactNode;
	open: boolean;
}

export function AchievementTooltip(props: AchievementTooltipProps) {
	const { achievement, children, open } = props;
	const StatusIcon = statusIcons[achievement.status];

	return (
		<div className="relative">
			{children}
			{open && (
				<div
					className={cn(
						"absolute z-50 w-64 p-4 rounded-lg bg-popover border-2 shadow-2xl",
						"transform -translate-x-1/2 left-1/2 bottom-full mb-3",
						rarityColors[achievement.rarity],
					)}
				>
					{/* Arrow */}
					<div
						className={cn(
							"absolute left-1/2 -translate-x-1/2 -bottom-2 w-4 h-4 rotate-45 bg-popover border-r-2 border-b-2",
							rarityColors[achievement.rarity],
						)}
					/>

					{/* Header */}
					<div className="flex items-start justify-between gap-2 mb-2">
						<div>
							<h3
								className={cn(
									"font-bold text-sm",
									achievement.rarity === "mythic" && "text-purple-500",
									achievement.rarity === "legendary" && "text-foreground",
									achievement.rarity === "epic" && "text-foreground/90",
									achievement.status === "locked" && "text-muted-foreground",
								)}
							>
								{achievement.name}
							</h3>
							<span className={cn("text-xs uppercase tracking-wider", "text-muted-foreground")}>
								{rarityLabels[achievement.rarity]}
							</span>
						</div>
						<StatusIcon
							className={cn(
								"w-5 h-5 shrink-0",
								achievement.status === "unlocked" && "text-foreground",
								achievement.status === "available" && "text-foreground/60",
								achievement.status === "locked" && "text-muted-foreground",
							)}
						/>
					</div>

					{/* Description */}
					<p className="text-sm text-foreground/80 mb-3">{achievement.description}</p>

					{/* Progress */}
					<div className="space-y-1">
						<div className="flex justify-between text-xs">
							<span className="text-muted-foreground">Progress</span>
							<span
								className={cn(
									achievement.status === "unlocked" ? "text-foreground" : "text-foreground/80",
								)}
							/>
						</div>
						<AchievementProgressDisplay achievement={achievement} />
					</div>

					{/* Unlocked date */}
					{achievement.unlockedAt && (
						<div className="mt-3 pt-2 border-t border-border/50 text-xs text-foreground/70">
							Unlocked on {new Date(achievement.unlockedAt).toLocaleDateString()}
						</div>
					)}
				</div>
			)}
		</div>
	);
}
