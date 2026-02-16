import type { Node, NodeProps } from "@xyflow/react";
import { Handle, Position } from "@xyflow/react";
import { useState } from "react";
import type { UIAchievement } from "@/components/achievements/types.ts";
import { cn } from "@/lib/utils";
import { AchievementTooltip } from "./AchievementTooltip.tsx";
import { rarityIconSizes, raritySizes, rarityStylingClasses } from "./styles.ts";

export type AchievementNode = Node<
	{ achievement: UIAchievement; className?: string },
	"achievement"
>;

export function AchievementNode({ data }: NodeProps<AchievementNode>) {
	const { achievement } = data;
	const [isHovered, setIsHovered] = useState(false);
	const Icon = achievement.icon;

	const getIconColor = () => {
		switch (achievement.status) {
			case "unlocked":
				return "text-background";
			case "available":
				return "text-foreground/70";
			default:
				return "text-foreground/20";
		}
	};

	return (
		<>
			<Handle
				type="target"
				position={Position.Top}
				className="bg-transparent! border-0! w-0! h-0!"
				style={{ top: "50%", left: "50%", transform: "translate(-50%, -50%)" }}
			/>
			<AchievementTooltip achievement={achievement} open={isHovered}>
				<button
					type="button"
					className={cn(
						"relative flex items-center justify-center rounded-full border-2 transition-all duration-300 cursor-pointer",
						raritySizes[achievement.rarity],
						rarityStylingClasses[achievement.rarity],
						isHovered && achievement.status !== "locked" && "scale-110",
					)}
					onMouseEnter={() => setIsHovered(true)}
					onMouseLeave={() => setIsHovered(false)}
					aria-label={`Achievement: ${achievement.name}`}
				>
					{/* Inner glow ring for unlocked */}
					{achievement.status === "unlocked" && (
						<div className="absolute inset-1 rounded-full border border-background/20 opacity-50" />
					)}

					{/* Outer decorative ring for notable+ */}
					{(achievement.rarity === "rare" ||
						achievement.rarity === "epic" ||
						achievement.rarity === "legendary" ||
						achievement.rarity === "mythic") && (
						<div
							className={cn(
								"absolute -inset-1 rounded-full border border-dashed opacity-20",
								achievement.status === "unlocked" ? "border-foreground" : "border-muted-foreground",
							)}
						/>
					)}

					{/* Legendary outer ring */}
					{achievement.rarity === "legendary" && achievement.status === "unlocked" && (
						<div className="absolute -inset-3 rounded-full border border-foreground/30 animate-ping" />
					)}

					<Icon
						size={rarityIconSizes[achievement.rarity]}
						className={cn("relative z-10", getIconColor())}
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
				className="bg-transparent! border-0! w-0! h-0!"
				style={{ top: "50%", left: "50%", transform: "translate(-50%, -50%)" }}
			/>
		</>
	);
}
