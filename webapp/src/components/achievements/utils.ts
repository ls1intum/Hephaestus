import type { Achievement } from "@/api";
import { type AchievementRarity, RARITY_WEIGHT } from "@/components/achievements/types.ts";

export function sortByRarity<T extends { rarity: AchievementRarity }>(achievements: T[]): T[] {
	return [...achievements].sort((a, b) => RARITY_WEIGHT[a.rarity] - RARITY_WEIGHT[b.rarity]);
}

export const compareByRarity = (a: Achievement, b: Achievement) => {
	return RARITY_WEIGHT[a.rarity] - RARITY_WEIGHT[b.rarity];
};
