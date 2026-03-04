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
	common: "size-10", // 40px
	uncommon: "size-12", // 48px
	rare: "size-14", // 56px
	epic: "size-16", // 64px
	legendary: "size-20", // 80px
	mythic: "size-24", // 96px
} as const satisfies Record<AchievementRarity, string>;

export const rarityLabels = {
	common: "Common",
	uncommon: "Uncommon",
	rare: "Rare",
	epic: "Epic",
	legendary: "Legendary",
	mythic: "Mythic",
} as const satisfies Record<AchievementRarity, string>;

/**
 * Rarity border colors using the `--rarity-*` CSS custom properties.
 * Used for tooltip borders and other rarity-colored accents.
 */
export const rarityBorderColors = {
	common: "border-rarity-common",
	uncommon: "border-rarity-uncommon",
	rare: "border-rarity-rare",
	epic: "border-rarity-epic",
	legendary: "border-rarity-legendary",
	mythic: "border-rarity-mythic-from",
} as const satisfies Record<AchievementRarity, string>;

/**
 * Rarity title text colors for tooltips and headings.
 *
 * Low tiers use the standard foreground to stay clean.
 * Epic+ tiers use their chromatic `--rarity-*` tokens,
 * giving the achievement name itself a colored "aura".
 */
export const rarityTitleColors = {
	common: "text-foreground",
	uncommon: "text-rarity-uncommon",
	rare: "text-rarity-rare",
	epic: "text-rarity-epic",
	legendary: "text-rarity-legendary",
	mythic: "text-rarity-mythic",
} as const satisfies Record<AchievementRarity, string>;

/**
 * Small accent-colored backgrounds keyed by rarity.
 *
 * Used in the stats panel "Recent Unlocks" list and similar
 * contexts where a tiny colored dot or circle represents the
 * tier. Colors are the `--rarity-*` tokens at a toned-down opacity.
 */
export const rarityAccentBackgrounds = {
	common: "bg-rarity-common/40",
	uncommon: "bg-rarity-uncommon/60",
	rare: "bg-rarity-rare/60",
	epic: "bg-rarity-epic/70",
	legendary: "bg-rarity-legendary/80",
	mythic: "bg-rarity-mythic-from/80",
} as const satisfies Record<AchievementRarity, string>;

export const rarityIconSizes = {
	common: 15,
	uncommon: 15,
	rare: 25,
	epic: 25,
	legendary: 35,
	mythic: 50,
} as const satisfies Record<AchievementRarity, number>;

/**
 * Rarity styling classes for the achievement node *frame* (border, shadow, animation).
 *
 * These intentionally do NOT include background colors — backgrounds are
 * controlled by {@link statusBackgrounds} so that locked/available/unlocked
 * status is always clearly communicated regardless of rarity tier.
 *
 * Design philosophy (game dev + web dev hybrid):
 * - **Same core shape** (circle + centered icon) — only the "frame" changes.
 * - **Progressive enhancement**: each tier adds ONE new visual element.
 * - **Border differentiation**: width, color, shadow — the primary recognition signal.
 * - **Color strategy**: common→rare = monochromatic (shadcn feel), epic+ = chromatic accents.
 */
export const rarityStylingClasses = {
	common: "border-2 border-rarity-common",
	uncommon: "border-3 border-rarity-uncommon",
	rare: "border-2 border-rarity-rare outline-2 outline-rarity-rare outline-offset-2",
	epic: "border-2 border-rarity-epic outline-2 outline-rarity-epic outline-offset-3",
	legendary:
		"border-4 border-rarity-legendary outline-4 outline-rarity-legendary legendary-pulse-anim outline-offset-3",
	mythic: "achievement-mythic-hexagon",
} as const satisfies Record<AchievementRarity, string>;

/**
 * Fully-opaque background classes based on achievement status.
 *
 * - **locked**: muted gray — visible but clearly "not yet achieved".
 * - **available**: mid-tone — progress is possible, draws subtle attention.
 * - **unlocked**: high contrast fill — the achievement "lights up".
 * - **hidden**: same as locked (shown only in designer/admin views).
 */
export const statusBackgrounds = {
	locked: "bg-node-locked",
	available: "bg-node-available",
	unlocked: "bg-node-unlocked",
	hidden: "bg-node-locked",
} as const satisfies Record<AchievementStatus, string>;

/**
 * CSS Custom properties for the Mythic Hexagon's inner solid fill.
 * Maps status perfectly to the CSS variable used by Tailwind.
 */
export const mythicBackgroundVars = {
	locked: "var(--node-locked)",
	available: "var(--node-available)",
	unlocked: "var(--node-unlocked)",
	hidden: "var(--node-locked)",
} as const satisfies Record<AchievementStatus, string>;

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
	NORTH: 270, // Top (Commits)
	EAST: 0, // Right (Pull Requests)
	SOUTH: 90, // Bottom (Communication)
	WEST: 180, // Left (Issues)
} as const satisfies Record<string, number>;

/**
 * Category metadata for positioning achievements on the skill tree.
 * Keys match backend enum values (lowercase).
 */
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
		angle: 0, // Milestones don't have a specific direction, they are placed in between components or have a special border later.
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
