import type { Achievement } from "@/api";
import { type AchievementRarity, rarityWeights } from "@/components/achievements/types.ts";

export function sortByRarity<T extends { rarity: AchievementRarity }>(achievements: T[]): T[] {
	return [...achievements].sort((a, b) => rarityWeights[a.rarity] - rarityWeights[b.rarity]);
}

export const compareByRarity = (a: Achievement, b: Achievement) => {
	return rarityWeights[a.rarity] - rarityWeights[b.rarity];
};
