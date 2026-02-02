import { CheckCircle2, Clock, Lock } from "lucide-react";
import type { ReactNode } from "react";
import { Progress, ProgressIndicator, ProgressTrack } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import type { Achievement } from "./data";

export interface AchievementTooltipProps {
	achievement: Achievement;
	children: ReactNode;
	open: boolean;
}

const tierColors = {
	minor: "border-muted-foreground/40",
	notable: "border-foreground/40",
	keystone: "border-foreground/60",
	legendary: "border-foreground/80",
};

const tierLabels = {
	minor: "Minor",
	notable: "Notable",
	keystone: "Keystone",
	legendary: "Legendary",
};

const statusIcons = {
	locked: Lock,
	available: Clock,
	unlocked: CheckCircle2,
};

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
						tierColors[achievement.tier],
						achievement.status === "unlocked" && "shadow-[0_0_20px_rgba(0,0,0,0.5)]",
					)}
				>
					{/* Arrow */}
					<div
						className={cn(
							"absolute left-1/2 -translate-x-1/2 -bottom-2 w-4 h-4 rotate-45 bg-popover border-r-2 border-b-2",
							tierColors[achievement.tier],
						)}
					/>

					{/* Header */}
					<div className="flex items-start justify-between gap-2 mb-2">
						<div>
							<h3
								className={cn(
									"font-bold text-sm",
									achievement.tier === "legendary" && "text-foreground",
									achievement.tier === "keystone" && "text-foreground/90",
									achievement.status === "locked" && "text-muted-foreground",
								)}
							>
								{achievement.name}
							</h3>
							<span className={cn("text-xs uppercase tracking-wider", "text-muted-foreground")}>
								{tierLabels[achievement.tier]}
							</span>
						</div>
						<StatusIcon
							className={cn(
								"w-5 h-5 flex-shrink-0",
								achievement.status === "unlocked" && "text-foreground",
								achievement.status === "available" && "text-foreground/60",
								achievement.status === "locked" && "text-muted-foreground",
							)}
						/>
					</div>

					{/* Description */}
					<p className="text-sm text-foreground/80 mb-3">{achievement.description}</p>

					{/* Requirement */}
					<div className="text-xs text-muted-foreground mb-2">
						<span className="font-medium">Requirement:</span> {achievement.requirement}
					</div>

					{/* Progress */}
					{achievement.progress !== undefined && achievement.maxProgress !== undefined && (
						<div className="space-y-1">
							<div className="flex justify-between text-xs">
								<span className="text-muted-foreground">Progress</span>
								<span
									className={cn(
										achievement.status === "unlocked" ? "text-foreground" : "text-foreground/80",
									)}
								>
									{achievement.progress} / {achievement.maxProgress}
								</span>
							</div>
							<Progress value={(achievement.progress / achievement.maxProgress) * 100}>
								<ProgressTrack
									className={cn(
										"h-2",
										achievement.status === "unlocked" && "[&>div]:bg-foreground",
										achievement.status === "available" && "[&>div]:bg-foreground/60",
									)}
								>
									<ProgressIndicator />
								</ProgressTrack>
							</Progress>
						</div>
					)}

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
