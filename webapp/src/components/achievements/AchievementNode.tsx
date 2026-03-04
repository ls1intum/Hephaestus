import type { Node, NodeProps } from "@xyflow/react";
import { Handle, Position } from "@xyflow/react";
import { useState } from "react";
import type { UIAchievement } from "@/components/achievements/types.ts";
import { cn } from "@/lib/utils";
import { AchievementTooltip } from "./AchievementTooltip.tsx";
import {
	mythicBackgroundVars,
	rarityIconSizes,
	raritySizes,
	rarityStylingClasses,
	statusBackgrounds,
} from "./styles.ts";

export type AchievementNode = Node<
	{ achievement: UIAchievement; showTooltips?: boolean; className?: string },
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
		case "available":
			return "text-muted-foreground";
		default:
			return "text-muted-foreground";
	}
}

export function AchievementNode({ data }: NodeProps<AchievementNode>) {
	const { achievement } = data;
	const [isHovered, setIsHovered] = useState(false);
	const Icon = achievement.icon;
	const isMythic = achievement.rarity === "mythic";

	return (
		<div>
			<Handle
				type="target"
				position={Position.Bottom}
				className="bg-transparent! border-0! w-0! h-0! min-w-0! min-h-0!"
				style={{ top: "50%", bottom: "auto", left: "50%", transform: "translate(-50%, -50%)" }}
			/>
			<AchievementTooltip achievement={achievement} open={isHovered && data.showTooltips !== false}>
				<button
					type="button"
					className={cn(
						"relative flex items-center justify-center transition-all duration-300 cursor-pointer",
						!isMythic && "rounded-full",
						raritySizes[achievement.rarity],
						!isMythic && statusBackgrounds[achievement.status],
						rarityStylingClasses[achievement.rarity],
						isHovered && achievement.status !== "locked" && "scale-110",
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
								className="absolute inset-0 w-full h-full -rotate-90"
								viewBox="0 0 100 100"
								aria-hidden="true"
							>
								<circle
									cx="50"
									cy="50"
									r="46"
									fill="none"
									stroke="currentColor"
									strokeWidth="4"
									className="text-muted/30"
								/>
								<circle
									cx="50"
									cy="50"
									r="46"
									fill="none"
									stroke="currentColor"
									strokeWidth="4"
									strokeDasharray={`${(achievement.progressData.current / achievement.progressData.target) * 289} 289`}
									className="text-foreground/70"
									strokeLinecap="round"
								/>
							</svg>
						)}
				</button>
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
