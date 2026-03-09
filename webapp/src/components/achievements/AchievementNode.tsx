import type { Node, NodeProps } from "@xyflow/react";
import { Handle, Position } from "@xyflow/react";
import { useState } from "react";
import type { UIAchievement } from "@/components/achievements/types.ts";
import { cn } from "@/lib/utils";
import { AchievementTooltip } from "./AchievementTooltip.tsx";
import {
	mythicBackgroundVars,
	rarityIconSizes,
	rarityPixelSizes,
	raritySizes,
	rarityStylingClasses,
	statusBackgrounds,
} from "./styles.ts";
import { StandaloneAura } from "./StandaloneAura.tsx";

export type AchievementNode = Node<
	{ achievement: UIAchievement; showTooltips?: boolean; className?: string; forceAura?: boolean },
	"achievement"
>;

/**
 * Returns the icon color class based on the achievement's current status.
 * Unlocked = high contrast against filled background, available = medium, locked = dim.
 */
function getIconColor(status: UIAchievement["status"]): string {
	switch (status) {
		case "unlocked":
			return "text-background";
		default: // available, locked, or hidden
			return "text-foreground";
	}
}

export function AchievementNode({ data }: NodeProps<AchievementNode>) {
	const { achievement } = data;
	const [isHovered, setIsHovered] = useState(false);
	const Icon = achievement.icon;
	const isMythic = achievement.rarity === "mythic";
	const isStandalone = achievement.parent === achievement.id || (achievement as any).parentId === achievement.id;
	const showAura = data.forceAura || achievement.forceAura || isStandalone;

	return (
		<div>
			<Handle
				type="target"
				position={Position.Bottom}
				className="bg-transparent! border-0! w-0! h-0! min-w-0! min-h-0!"
				style={{ top: "50%", bottom: "auto", left: "50%", transform: "translate(-50%, -50%)" }}
			/>
			<AchievementTooltip achievement={achievement} open={isHovered && data.showTooltips !== false}>
				<div className="relative flex items-center justify-center">
					{showAura && (
						<StandaloneAura
							achievement={achievement}
							size={rarityPixelSizes[achievement.rarity]}
						/>
					)}
					<button
						type="button"
						className={cn(
							"relative flex items-center justify-center transition-all duration-300 cursor-pointer",
							!isMythic && "rounded-full",
							raritySizes[achievement.rarity],
							!isMythic && statusBackgrounds[achievement.status],
							rarityStylingClasses[achievement.rarity],
							(achievement.rarity === "rare" ||
								achievement.rarity === "epic" ||
								achievement.rarity === "legendary") &&
								(achievement.status === "unlocked" ? "outline-solid" : "outline-dashed"),
							isHovered && achievement.status !== "locked" && "scale-110",
							achievement.status !== "unlocked" && "grayscale",
						)}
						style={
							isMythic
								? ({ "--mythic-bg": mythicBackgroundVars[achievement.status] } as React.CSSProperties)
								: undefined
						}
						onMouseEnter={() => setIsHovered(true)}
						onMouseLeave={() => setIsHovered(false)}
						aria-label={`Achievement: ${achievement.name}`}
					>
						<Icon
							size={rarityIconSizes[achievement.rarity]}
							className={cn("relative z-10", getIconColor(achievement.status))}
						/>

						{/* Progress indicator for available achievements */}
						{achievement.status === "available" &&
							achievement.progressData?.type === "LinearAchievementProgress" && (
								<svg
									className={cn("absolute inset-0 w-full h-full", !isMythic && "-rotate-90")}
									viewBox="0 0 100 100"
									aria-hidden="true"
								>
									{isMythic ? (
										<>
											{/* Hexagon Track */}
											<path
												d="M 50 9.5 L 84.64 29.5 L 84.64 69.5 L 50 89.5 L 15.36 69.5 L 15.36 29.5 Z"
												fill="none"
												stroke="currentColor"
												strokeWidth="4"
												className="text-muted/30"
											/>
											{/* Hexagon Progress */}
											<path
												d="M 50 9.5 L 84.64 29.5 L 84.64 69.5 L 50 89.5 L 15.36 69.5 L 15.36 29.5 Z"
												fill="none"
												stroke="currentColor"
												strokeWidth="4"
												strokeDasharray={`${((achievement.progressData.current ?? 0) / achievement.progressData.target) * 240} 240`}
												className="text-foreground/70"
												strokeLinecap="round"
											/>
										</>
									) : (
										<>
											{/* Circle Track */}
											<circle
												cx="50"
												cy="50"
												r="46"
												fill="none"
												stroke="currentColor"
												strokeWidth="4"
												className="text-muted/30"
											/>
											{/* Circle Progress */}
											<circle
												cx="50"
												cy="50"
												r="46"
												fill="none"
												stroke="currentColor"
												strokeWidth="4"
												strokeDasharray={`${((achievement.progressData.current ?? 0) / achievement.progressData.target) * 289} 289`}
												className="text-foreground/70"
												strokeLinecap="round"
											/>
										</>
									)}
								</svg>
							)}
					</button>
				</div>
			</AchievementTooltip>
			<Handle
				type="source"
				position={Position.Bottom}
				className="bg-transparent! border-0! w-0! h-0! min-w-0! min-h-0!"
				style={{ top: "50%", bottom: "auto", left: "50%", transform: "translate(-50%, -50%)" }}
			/>
		</div>
	);
}
