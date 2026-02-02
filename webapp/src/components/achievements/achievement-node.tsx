import type { NodeProps } from "@xyflow/react";
import { Handle, Position } from "@xyflow/react";
import {
	ArrowLeftRight,
	Bug,
	Building,
	Circle,
	CircleDot,
	Crown,
	Eye,
	FileText,
	Flag,
	Flame,
	GitCommit,
	GitMerge,
	GitPullRequest,
	GraduationCap,
	HandHelping,
	HelpCircle,
	Layers,
	Lightbulb,
	ListChecks,
	Lock,
	Megaphone,
	MessageSquare,
	MessagesSquare,
	Pentagon,
	Radar,
	Radio,
	Rocket,
	RotateCcw,
	ScrollText,
	Shield,
	Sparkles,
	Star,
	Target,
	Timer,
	Triangle,
	Wrench,
	Zap,
} from "lucide-react";
import type React from "react";
import { memo, useState } from "react";
import { cn } from "@/lib/utils";
import { AchievementTooltip } from "./achievement-tooltip";
import type { AchievementNodeData } from "./data";

const iconMap: Record<string, React.ElementType> = {
	GitCommit,
	GitPullRequest,
	GitMerge,
	Eye,
	Shield,
	Sparkles,
	Bug,
	Target,
	Flag,
	MessageSquare,
	MessagesSquare,
	Zap,
	Crown,
	Flame,
	Rocket,
	Building,
	Lock,
	CircleDot,
	Radar,
	Megaphone,
	Radio,
	Layers,
	RotateCcw,
	ArrowLeftRight,
	Circle,
	ListChecks,
	ScrollText,
	GraduationCap,
	Wrench,
	HelpCircle,
	Lightbulb,
	FileText,
	Triangle,
	Pentagon,
	Star,
	HandHelping,
	Timer,
};

const tierSizes = {
	minor: "w-10 h-10",
	notable: "w-12 h-12",
	keystone: "w-14 h-14",
	legendary: "w-16 h-16",
};

const tierIconSizes = {
	minor: 14,
	notable: 18,
	keystone: 22,
	legendary: 26,
};

function AchievementNodeComponent(props: NodeProps) {
	const { data } = props;
	const achievementData = data as unknown as AchievementNodeData;
	const [isHovered, setIsHovered] = useState(false);
	const Icon = iconMap[achievementData.icon] || GitCommit;

	const getStatusClasses = () => {
		switch (achievementData.status) {
			case "unlocked":
				if (achievementData.tier === "legendary") {
					return "bg-node-legendary border-node-legendary shadow-[0_0_30px_rgba(var(--shadow-rgb),0.6),0_0_60px_rgba(var(--shadow-rgb),0.3),inset_0_0_20px_rgba(var(--shadow-rgb),0.2)]";
				}
				if (achievementData.tier === "keystone") {
					return "bg-node-unlocked border-node-unlocked shadow-[0_0_25px_rgba(var(--shadow-rgb),0.4),0_0_50px_rgba(var(--shadow-rgb),0.2),inset_0_0_15px_rgba(var(--shadow-rgb),0.15)]";
				}
				return "bg-node-unlocked border-node-unlocked shadow-[0_0_15px_rgba(var(--shadow-rgb),0.3),0_0_30px_rgba(var(--shadow-rgb),0.15)]";
			case "available":
				return "bg-node-available/80 border-node-available/70 shadow-[0_0_12px_rgba(var(--shadow-rgb),0.15)]";
			default:
				return "bg-node-locked border-node-locked/50 opacity-40";
		}
	};

	const getIconColor = () => {
		switch (achievementData.status) {
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
				className="!bg-transparent !border-0 !w-0 !h-0"
				style={{ top: "50%", left: "50%", transform: "translate(-50%, -50%)" }}
			/>
			<AchievementTooltip achievement={achievementData} open={isHovered}>
				<button
					type="button"
					className={cn(
						"relative flex items-center justify-center rounded-full border-2 transition-all duration-300 cursor-pointer",
						tierSizes[achievementData.tier],
						getStatusClasses(),
						isHovered && achievementData.status !== "locked" && "scale-110",
					)}
					onMouseEnter={() => setIsHovered(true)}
					onMouseLeave={() => setIsHovered(false)}
					aria-label={`Achievement: ${achievementData.name}`}
				>
					{/* Inner glow ring for unlocked */}
					{achievementData.status === "unlocked" && (
						<div className="absolute inset-1 rounded-full border border-background/20 opacity-50" />
					)}

					{/* Outer decorative ring for notable+ */}
					{(achievementData.tier === "notable" ||
						achievementData.tier === "keystone" ||
						achievementData.tier === "legendary") && (
						<div
							className={cn(
								"absolute -inset-1 rounded-full border border-dashed opacity-20",
								achievementData.status === "unlocked"
									? "border-foreground"
									: "border-muted-foreground",
							)}
						/>
					)}

					{/* Legendary outer ring */}
					{achievementData.tier === "legendary" && achievementData.status === "unlocked" && (
						<div className="absolute -inset-3 rounded-full border border-foreground/30 animate-ping" />
					)}

					<Icon
						size={tierIconSizes[achievementData.tier]}
						className={cn("relative z-10", getIconColor())}
					/>

					{/* Progress indicator for available achievements */}
					{achievementData.status === "available" &&
						achievementData.progress !== undefined &&
						achievementData.maxProgress !== undefined && (
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
									strokeDasharray={`${(achievementData.progress / achievementData.maxProgress) * 289} 289`}
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
				className="!bg-transparent !border-0 !w-0 !h-0"
				style={{ top: "50%", left: "50%", transform: "translate(-50%, -50%)" }}
			/>
		</>
	);
}

export const AchievementNode = memo(AchievementNodeComponent);
