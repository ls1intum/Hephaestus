import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";

export interface LevelBadgeProps {
	level: number;
	className?: string;
}

function LevelBadge({ level, className }: LevelBadgeProps) {
	return (
		<div className="flex flex-col items-center justify-center min-w-[4.5rem] p-3 rounded-xl bg-secondary/50 border border-border/50 backdrop-blur-sm shadow-sm">
			<span className="text-[0.65rem] uppercase text-muted-foreground font-bold tracking-wider">
				LEVEL
			</span>
			<span className="text-3xl font-black text-foreground leading-none tracking-tight">
				{level}
			</span>
		</div>
	);
}

export interface XpProgressProps {
	level: number;
	currentXP: number;
	xpNeeded: number;
	className?: string;
}

export function XpProgress({ level, currentXP, xpNeeded, className }: XpProgressProps) {
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
