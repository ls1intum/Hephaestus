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
		// className="w-full max-w-md flex-col gap-4"
		<div className={cn("w-full flex flex-col gap-2", className)}>
			<div className="flex w-full justify-between items-center text-sm font-medium gap-2">
				<span>Level: {level}</span>
				<span className="text-muted-foreground">
					{currentXP} / {xpNeeded} XP
				</span>
			</div>
			<Progress className="h-3" value={percentage} />
		</div>
	);
}
