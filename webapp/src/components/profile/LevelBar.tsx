import { LevelBadge } from "@/components/profile/LevelBadge.tsx";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";

interface LevelBarProps {
	level: number;
	currentXP: number;
	xpNeeded: number;
	className?: string;
}

export function LevelBar({ level, currentXP, xpNeeded, className }: LevelBarProps) {
	// Calculate percentage, guarding against division by zero
	const percentage = xpNeeded > 0 ? Math.min(100, Math.max(0, (currentXP / xpNeeded) * 100)) : 0;

	return (
		<div className={cn("flex items-center gap-4 w-full", className)}>
			{/* Level Badge */}
			<LevelBadge level={level}></LevelBadge>

			{/* Progress Section */}
			<div className="flex flex-col gap-1.5 flex-1 justify-center">
				<div className="flex justify-end w-full">
					<span className="text-xs font-medium text-muted-foreground">
						{currentXP} <span className="text-muted-foreground/50">/</span> {xpNeeded} XP
					</span>
				</div>
				<Progress className="h-3" value={percentage} />
			</div>
		</div>
	);
}
