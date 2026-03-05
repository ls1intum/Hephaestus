import { Tooltip as TooltipPrimitive } from "@base-ui/react/tooltip";
import type { ReactElement, ReactNode } from "react";
import { AchievementProgressDisplay } from "@/components/achievements/AchievementProgressDisplay.tsx";
import {
	rarityBorderColors,
	rarityLabels,
	rarityTitleColors,
	statusIcons,
} from "@/components/achievements/styles";
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

	const borderClass =
		achievement.status === "unlocked"
			? rarityBorderColors[achievement.rarity]
			: "border-muted-foreground/30";

	return (
		<TooltipPrimitive.Provider delay={0}>
			<TooltipPrimitive.Root open={open}>
				<TooltipPrimitive.Trigger render={children as ReactElement} />
				<TooltipPrimitive.Portal>
					<TooltipPrimitive.Positioner side="top" sideOffset={12} align="center" className="z-100">
						<TooltipPrimitive.Popup
							className={cn(
								"w-64 bg-popover border-2 shadow-2xl text-foreground rounded-lg overflow-hidden",
								"animate-in fade-in-0 zoom-in-95", // Basic animation
								borderClass,
							)}
						>
							{/* Inner Content Padding */}
							<div className="p-4 relative z-10">
								{/* Header */}
								<div className="flex items-start justify-between gap-2 mb-2">
									<div>
										<h3
											className={cn(
												"font-bold text-sm",
												rarityTitleColors[achievement.rarity],
												achievement.status !== "unlocked" && "text-muted-foreground",
											)}
										>
											{achievement.name}
										</h3>
										<span
											className={cn("text-xs uppercase tracking-wider", "text-muted-foreground")}
										>
											{rarityLabels[achievement.rarity]}
										</span>
									</div>
									<StatusIcon
										className={cn(
											"w-5 h-5 shrink-0",
											achievement.status === "unlocked" && "text-foreground",
											achievement.status !== "unlocked" && "text-muted-foreground",
										)}
									/>
								</div>

								{/* Description */}
								<p className="text-sm text-foreground/80 mb-3">{achievement.description}</p>

								{/* Progress */}
								<div className="space-y-1">
									<div className="flex justify-between text-xs">
										<span className="text-muted-foreground">Progress</span>
									</div>
									<AchievementProgressDisplay achievement={achievement} />
								</div>

								{/* Unlocked date */}
								{achievement.unlockedAt && achievement.status === "unlocked" && (
									<div className="mt-3 pt-2 border-t border-border/50 text-xs text-foreground/70">
										Unlocked on {new Date(achievement.unlockedAt).toLocaleDateString()}
									</div>
								)}
							</div>

							{/* Custom Arrow to match the border color logic and orientation */}
							<TooltipPrimitive.Arrow
								className={cn(
									"w-4 h-4 rotate-45 bg-popover z-0",
									// Positioning: Anchor to the opposite edge of the side it's on
									"data-[side=top]:-bottom-2",
									"data-[side=bottom]:-top-2",
									// Borders: Only show the borders that point towards the node
									"data-[side=top]:border-r-2 data-[side=top]:border-b-2",
									"data-[side=bottom]:border-l-2 data-[side=bottom]:border-t-2",
									borderClass,
								)}
							/>
						</TooltipPrimitive.Popup>
					</TooltipPrimitive.Positioner>
				</TooltipPrimitive.Portal>
			</TooltipPrimitive.Root>
		</TooltipPrimitive.Provider>
	);
}
