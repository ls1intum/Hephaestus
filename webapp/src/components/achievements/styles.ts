import {
	ClockIcon,
	CodeReviewIcon,
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
	communication: CodeReviewIcon,
	issues: IssueOpenedIcon,
	milestones: MilestoneIcon,
} as const satisfies Record<AchievementCategory, React.ElementType>;

export const raritySizes = {
	common: "size-10",
	uncommon: "size-12",
	rare: "size-14",
	epic: "size-16",
	legendary: "size-20",
	mythic: "size-24",
} as const satisfies Record<AchievementRarity, string>;

export const rarityPixelSizes = {
	common: 40,
	uncommon: 48,
	rare: 56,
	epic: 64,
	legendary: 80,
	mythic: 96,
} as const satisfies Record<AchievementRarity, number>;

export const rarityLabels = {
	common: "Common",
	uncommon: "Uncommon",
	rare: "Rare",
	epic: "Epic",
	legendary: "Legendary",
	mythic: "Mythic",
} as const satisfies Record<AchievementRarity, string>;

export const rarityBorderColors = {
	common: "border-rarity-common",
	uncommon: "border-rarity-uncommon",
	rare: "border-rarity-rare",
	epic: "border-rarity-epic",
	legendary: "border-rarity-legendary",
	mythic: "border-rarity-mythic-from",
} as const satisfies Record<AchievementRarity, string>;

export const rarityAccentBackgrounds = {
	common: "bg-rarity-common",
	uncommon: "bg-rarity-uncommon/80",
	rare: "bg-rarity-rare/80",
	epic: "bg-rarity-epic/90",
	legendary: "bg-rarity-legendary",
	mythic: "bg-rarity-mythic-from",
} as const satisfies Record<AchievementRarity, string>;

export const rarityIconSizes = {
	common: 16,
	uncommon: 18,
	rare: 24,
	epic: 28,
	legendary: 36,
	mythic: 52,
} as const satisfies Record<AchievementRarity, number>;

export const rarityStylingClasses = {
	common: "border-2 border-rarity-common",
	uncommon: "border-3 border-rarity-uncommon",
	rare: "border-2 border-rarity-rare outline-2 outline-rarity-rare outline-offset-2",
	epic: "border-2 border-rarity-epic outline-2 outline-rarity-epic outline-offset-3",
	legendary:
		"border-4 border-rarity-legendary outline-4 outline-rarity-legendary legendary-pulse-anim outline-offset-3",
	mythic: "achievement-mythic-hexagon",
} as const satisfies Record<AchievementRarity, string>;

export const statusBackgrounds = {
	locked: "bg-node-locked",
	available: "bg-node-available",
	unlocked: "bg-node-unlocked",
	hidden: "bg-node-locked",
} as const satisfies Record<AchievementStatus, string>;

export const mythicBackgroundVars = {
	locked: "var(--node-locked)",
	available: "var(--node-available)",
	unlocked: "var(--node-unlocked)",
	hidden: "var(--node-locked)",
} as const satisfies Record<AchievementStatus, string>;

export const statusIcons = {
	locked: LockIcon,
	available: ClockIcon,
	unlocked: UnlockIcon,
	hidden: EyeClosedIcon,
} as const satisfies Record<AchievementStatus, React.ElementType>;

const skillTreeAngles = {
	NORTH: 270,
	EAST: 0,
	SOUTH: 90,
	WEST: 180,
} as const satisfies Record<string, number>;

export const categoryMeta = {
	commits: {
		name: "Commits",
		angle: skillTreeAngles.NORTH,
		description: "Track your code contributions",
	},
	pull_requests: {
		name: "Pull Requests",
		angle: skillTreeAngles.EAST,
		description: "Submit and merge code changes",
	},
	communication: {
		name: "Communication",
		angle: skillTreeAngles.SOUTH,
		description: "Reviews, comments, and discussions",
	},
	issues: {
		name: "Issues",
		angle: skillTreeAngles.WEST,
		description: "Report and track work items",
	},
	milestones: {
		name: "Milestones",
		angle: 0,
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
