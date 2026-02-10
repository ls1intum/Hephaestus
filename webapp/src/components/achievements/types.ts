import type { Achievement } from "@/api/types.gen";

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

export const RARITY_WEIGHT: Record<AchievementRarity, number> = {
	common: 0,
	uncommon: 1,
	rare: 2,
	epic: 3,
	legendary: 4,
	mythic: 5,
};

export function sortByRarity<T extends { rarity: AchievementRarity }>(achievements: T[]): T[] {
	return [...achievements].sort((a, b) => RARITY_WEIGHT[a.rarity] - RARITY_WEIGHT[b.rarity]);
}

export const compareByRarity = (a: Achievement, b: Achievement) => {
	const weightA = a.rarity ? RARITY_WEIGHT[a.rarity] : 999;
	const weightB = b.rarity ? RARITY_WEIGHT[b.rarity] : 999;
	return weightA - weightB;
};
