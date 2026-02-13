import type { AchievementRarity } from "@/components/achievements/types";

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
