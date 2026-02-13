import type { Achievement } from "@/api/types.gen";

/**
 * Achievement category type derived from the generated Achievement type.
 */
export type AchievementCategory = Achievement["category"];

/**
 * Tier type for visual representation.
 */
export type AchievementRarity = Achievement["rarity"];

/**
 * Achievement status type derived from the generated Achievement type.
 */
export type AchievementStatus = Achievement["status"];

/**
 * View mode options (for smaller screens that cant handle react flow graphs)
 */
export type ViewMode = "tree" | "list";

export const RARITY_WEIGHT = {
	common: 0,
	uncommon: 1,
	rare: 2,
	epic: 3,
	legendary: 4,
	mythic: 5,
} as const satisfies Record<AchievementRarity, number>;
