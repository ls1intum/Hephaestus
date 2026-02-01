import { ClockIcon } from "@primer/octicons-react";
import { Progress as ProgressRoot } from "@base-ui/react/progress";
import { ProgressIndicator, ProgressTrack } from "@/components/ui/progress";
import { cn } from "@/lib/utils";

export interface XpProgressProps {
	/** XP earned within the current level */
	currentXP: number;
	/** XP needed to reach the next level */
	xpNeeded: number;
	/** Next level number (current level + 1) */
	nextLevel: number;
	/** Total lifetime XP accumulated */
	totalXP: number;
	/** Date when user started contributing (optional) */
	contributingSince?: string;
	className?: string;
}

export function XpProgress({
	currentXP,
	xpNeeded,
	nextLevel,
	totalXP,
	contributingSince,
	className,
}: XpProgressProps) {
	// Calculate percentage, guarding against division by zero
	const percentage = xpNeeded > 0 ? Math.min(100, Math.max(0, (currentXP / xpNeeded) * 100)) : 0;

	return (
		<div className={cn("w-full", className)}>
			<div className="flex flex-col gap-1.5">
				{/* XP Progress Label */}
				<div className="flex justify-between items-baseline px-0.5">
					<span className="text-xs font-semibold text-muted-foreground">
						{currentXP.toLocaleString()} / {xpNeeded.toLocaleString()} XP to Level {nextLevel}
					</span>
					<span className="text-xs text-muted-foreground">{totalXP.toLocaleString()} XP total</span>
				</div>

				{/* Progress Bar */}
				<div className="relative h-2.5 w-full bg-secondary/80 rounded-full overflow-hidden">
					{/* Subtle gloss effect */}
					<div className="absolute inset-0 z-10 bg-gradient-to-b from-white/10 to-transparent pointer-events-none rounded-full" />

					<ProgressRoot.Root value={percentage} className="h-full">
						<ProgressTrack className="h-full rounded-full bg-transparent">
							<ProgressIndicator className="bg-gradient-to-r from-primary/90 to-primary rounded-full transition-all duration-500" />
						</ProgressTrack>
					</ProgressRoot.Root>
				</div>

				{/* Contributing since (optional) */}
				{contributingSince && (
					<div className="flex items-center gap-1.5 text-muted-foreground text-xs mt-0.5">
						<ClockIcon size={12} className="shrink-0" />
						<span>Contributing since {contributingSince}</span>
					</div>
				)}
			</div>
		</div>
	);
}
