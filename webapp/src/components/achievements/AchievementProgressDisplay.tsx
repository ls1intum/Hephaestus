import { CheckCircleIcon, XCircleIcon } from "@primer/octicons-react";
import type { Achievement } from "@/api/types.gen";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";

interface AchievementProgressDisplayProps {
	achievement: Achievement;
	className?: string;
}

export function AchievementProgressDisplay({
	achievement,
	className,
}: AchievementProgressDisplayProps) {
	const { progressData } = achievement;

	switch (progressData.type) {
		case "LinearAchievementProgress": {
			// Logic for Counter Achievements (e.g., 5/10 PRs)
			const percentage =
				progressData.current > 0
					? Math.round((progressData.current / progressData.target) * 100)
					: 0;

			return (
				<div className={cn("space-y-1 min-w-30", className)}>
					<Progress
						value={percentage}
						className="h-2"
						aria-label={`${progressData.current} of ${progressData.target}`}
					/>
					<div className="text-xs text-muted-foreground flex justify-between">
						<span>{percentage}%</span>
						<span>
							{progressData.current} / {progressData.target}
						</span>
					</div>
				</div>
			);
		}

		case "BinaryAchievementProgress": {
			// Logic for Boolean Achievements (e.g., Night Owl)
			// No progress bar needed, just a status indicator
			if (progressData.unlocked) {
				return (
					<div className={cn("flex items-center gap-1.5 text-green-600", className)}>
						<CheckCircleIcon className="h-4 w-4" />
						<span className="text-xs font-medium">Unlocked</span>
					</div>
				);
			}

			return (
				<div className={cn("flex items-center gap-1.5 text-red-600", className)}>
					<XCircleIcon className="h-4 w-4" />
					<span className="text-xs">Locked</span>
				</div>
			);
		}

		default:
			return null;
	}
}
