import {
	ClockIcon,
	CommentIcon,
	EyeClosedIcon,
	GitCommitIcon,
	GitPullRequestIcon,
	IssueOpenedIcon,
	LockIcon,
	UnlockIcon,
} from "@primer/octicons-react";
import { MilestoneIcon } from "lucide-react";
import type React from "react";
import type {
	AchievementCategory,
	AchievementRarity,
	AchievementStatus,
} from "@/components/achievements/types";

// ===== Achievement Category Styling ===== //

export const categoryLabels = {
	pull_requests: "Pull Requests",
	commits: "Commits",
	communication: "Communication",
	issues: "Issues",
	milestones: "Milestones",
} as const satisfies Record<AchievementCategory, string>;

export const defaultCategoryIcons = {
	pull_requests: GitPullRequestIcon,
	commits: GitCommitIcon,
	communication: CommentIcon,
	issues: IssueOpenedIcon,
	milestones: MilestoneIcon,
} as const satisfies Record<AchievementCategory, React.ElementType>;

// ===== Achievement Rarity Styling ===== //

export const raritySizes = {
	common: "w-10 h-10",
	uncommon: "w-11 h-11",
	rare: "w-12 h-12",
	epic: "w-14 h-14",
	legendary: "w-16 h-16",
	mythic: "w-20 h-20",
} as const satisfies Record<AchievementRarity, string>;

export const rarityLabels = {
	common: "Common",
	uncommon: "Uncommon",
	rare: "Rare",
	epic: "Epic",
	legendary: "Legendary",
	mythic: "Mythic",
} as const satisfies Record<AchievementRarity, string>;

export const rarityColors = {
	common: "border-muted-foreground/40",
	uncommon: "border-foreground/30",
	rare: "border-foreground/50",
	epic: "border-foreground/70",
	legendary: "border-foreground/90",
	mythic: "border-purple-500/90",
} as const satisfies Record<AchievementRarity, string>;

export const rarityIconSizes = {
	common: 14,
	uncommon: 16,
	rare: 18,
	epic: 22,
	legendary: 26,
	mythic: 32,
} as const satisfies Record<AchievementRarity, number>;

export const rarityStylingClasses = {
	common: "bg-node-locked border-node-locked/50 opacity-40",
	uncommon:
		"bg-node-available/80 border-node-available/70 shadow-[0_0_12px_rgba(var(--shadow-rgb),0.15)]",
	rare: "bg-node-unlocked border-node-unlocked shadow-[0_0_15px_rgba(var(--shadow-rgb),0.3),0_0_30px_rgba(var(--shadow-rgb),0.15)]",
	epic: "bg-node-epic border-node-epic shadow-[0_0_25px_rgba(var(--shadow-rgb),0.4),0_0_50px_rgba(var(--shadow-rgb),0.2),inset_0_0_15px_rgba(var(--shadow-rgb),0.15)]",
	legendary:
		"bg-node-legendary border-node-legendary shadow-[0_0_30px_rgba(var(--shadow-rgb),0.6),0_0_60px_rgba(var(--shadow-rgb),0.3),inset_0_0_20px_rgba(var(--shadow-rgb),0.2)]",
	mythic:
		"bg-node-mythic border-node-mythic shadow-[0_0_30px_rgba(var(--shadow-rgb),0.6),0_0_60px_rgba(var(--shadow-rgb),0.3),inset_0_0_20px_rgba(var(--shadow-rgb),0.2)]",
} as const satisfies Record<AchievementRarity, string>;

// ===== Achievement Status Styling ===== //

export const statusIcons = {
	locked: LockIcon,
	available: ClockIcon,
	unlocked: UnlockIcon,
	hidden: EyeClosedIcon,
} as const satisfies Record<AchievementStatus, React.ElementType>;

// ===== Achievement Label Styling ===== //

/** Skill tree radial angles with clock direction annotations */
const skillTreeAngles = {
	TOP: 270, // Top (12 o'clock)
	TOP_RIGHT: 342, // Top-right (2 o'clock)
	RIGHT: 54, // Right (4 o'clock)
	BOTTOM_RIGHT: 126, // Bottom-right (7 o'clock)
	BOTTOM_LEFT: 198, // Bottom-left (9 o'clock)
} as const satisfies Record<string, number>;

/**
 * Category metadata for positioning achievements on the skill tree.
 * Keys match backend enum values (lowercase).
 */
export const categoryMeta = {
	commits: {
		name: "Commits",
		angle: skillTreeAngles.TOP,
		description: "Track your code contributions",
	},
	pull_requests: {
		name: "Pull Requests",
		angle: skillTreeAngles.TOP_RIGHT,
		description: "Submit and merge code changes",
	},
	communication: {
		name: "Communication",
		angle: skillTreeAngles.RIGHT,
		description: "Reviews, comments, and discussions",
	},
	issues: {
		name: "Issues",
		angle: skillTreeAngles.BOTTOM_RIGHT,
		description: "Report and track work items",
	},
	milestones: {
		name: "Milestones",
		angle: skillTreeAngles.BOTTOM_LEFT,
		description: "Combined achievements",
	},
} as const satisfies Record<
	AchievementCategory,
	{
		name: string;
		angle: number;
		description: string;
	}
>;
