import type { Achievement } from "@/api/types.gen";

/* --- Extra Types --- */

/**
 * Achievement category type derived from the generated Achievement type.
 */
export type AchievementCategory = Achievement["category"];

/**
 * Achievement status type derived from the generated Achievement type.
 */
export type AchievementStatus = Achievement["status"];

/**
 * Tier type for visual representation.
 */
export type AchievementRarity = Achievement["rarity"];

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

/* --- Styling Config --- */

export const tierSizes = {
	common: "w-10 h-10",
	uncommon: "w-11 h-11",
	rare: "w-12 h-12",
	epic: "w-14 h-14",
	legendary: "w-16 h-16",
	mythic: "w-20 h-20",
} as const satisfies Record<AchievementRarity, string>;

export const tierIconSizes = {
	common: 14,
	uncommon: 16,
	rare: 18,
	epic: 22,
	legendary: 26,
	mythic: 32,
} as const satisfies Record<AchievementRarity, number>;

/* --- Extra Type Functionality --- */

export function sortByRarity<T extends { rarity: AchievementRarity }>(achievements: T[]): T[] {
	return [...achievements].sort((a, b) => RARITY_WEIGHT[a.rarity] - RARITY_WEIGHT[b.rarity]);
}

export const compareByRarity = (a: Achievement, b: Achievement) => {
	return RARITY_WEIGHT[a.rarity] - RARITY_WEIGHT[b.rarity];
};
