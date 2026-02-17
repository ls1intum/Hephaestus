import type { Edge, Node } from "@xyflow/react";
import type { Achievement } from "@/api/types.gen";
import type {
	AchievementCategory,
	AchievementRarity,
	AchievementStatus,
} from "@/components/achievements/types";

/**
 * Node data structure for the skill tree visualization.
 * Extends Achievement with positioning and UI-specific information.
 */
// export interface AchievementNodeData {
// 	id: string;
// 	name: string;
// 	description: string;
// 	category: AchievementCategory;
// 	tier: AchievementRarity;
// 	status: AchievementStatus;
// 	icon: string;
// 	progress?: number;
// 	maxProgress?: number;
// 	unlockedAt?: Date | null;
// 	level: number;
// 	angle: number;
// 	ring: number;
// 	// For avatar node
// 	avatarUrl?: string;
// 	leaguePoints?: number;
// 	// Index signature required by React Flow's Node<T> constraint
// 	[key: string]: unknown;
// }

/** Ring distances based on achievement level (1-7) */
const levelDistances: Record<number, number> = {
	1: 180,
	2: 270,
	3: 360,
	4: 460,
	5: 560,
	6: 670,
	7: 800,
};

/**
 * Calculates achievement statistics for the stats panel.
 *
 * @param achievementList - Array of achievements from the API
 * @returns Statistics including totals and per-category breakdowns
 */
export function calculateStats(achievementList: Achievement[]) {
	const total = achievementList.length;
	const unlocked = achievementList.filter((a) => a.status === "unlocked").length;
	const available = achievementList.filter((a) => a.status === "available").length;

	const byCategory = MAIN_CATEGORIES.reduce(
		(acc, cat) => {
			const catAchievements = achievementList.filter((a) => (a.category ?? "milestones") === cat);
			acc[cat] = {
				total: catAchievements.length,
				unlocked: catAchievements.filter((a) => a.status === "unlocked").length,
			};
			return acc;
		},
		{} as Record<AchievementCategory, { total: number; unlocked: number }>,
	);

	return {
		total,
		unlocked,
		available,
		percentage: total > 0 ? Math.round((unlocked / total) * 100) : 0,
		byCategory,
	};
}

// Helper for mapping rarity to visual level (since 'level' property is missing in API)
const RARITY_TO_LEVEL: Record<string, number> = {
	common: 1,
	uncommon: 2,
	rare: 3,
	epic: 4,
	legendary: 5,
	mythic: 6,
};
