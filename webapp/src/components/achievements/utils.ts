import type { Achievement } from "@/api";
import { type AchievementRarity, rarityWeights } from "@/components/achievements/types.ts";

export function sortByRarity<T extends { rarity: AchievementRarity }>(achievements: T[]): T[] {
	return [...achievements].sort((a, b) => rarityWeights[a.rarity] - rarityWeights[b.rarity]);
}

export const compareByRarity = (a: Achievement, b: Achievement) => {
	return rarityWeights[a.rarity] - rarityWeights[b.rarity];
};

export function resolveProgress(achievement: Achievement): number {
	const progressData = achievement.progressData;

	switch (progressData.type) {
		case "BinaryAchievementProgress":
			return progressData.unlocked ? 1 : 0;

		case "LinearAchievementProgress":
			// const percentage = (progressData.current / progressData.target) * 100;
			return progressData.current > 0
				? Math.round((progressData.current / progressData.target) * 100)
				: 0;
	}
}
