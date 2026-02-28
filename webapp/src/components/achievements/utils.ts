import type { Achievement } from "@/api";
import type { AchievementEdge } from "@/components/achievements/AchievementEdge.tsx";
import type { AchievementNode } from "@/components/achievements/AchievementNode.tsx";
import type { AvatarNode } from "@/components/achievements/AvatarNode.tsx";
import { ACHIEVEMENT_REGISTRY } from "@/components/achievements/definitions.ts";
import {
	type AchievementCategory,
	type AchievementRarity,
	rarityWeights,
	type UIAchievement,
} from "@/components/achievements/types.ts";
import coordinatesData from "./coordinates.json";

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

// Sort categories in a logical order
export const ACHIEVEMENT_CATEGORIES: readonly AchievementCategory[] = [
	"pull_requests",
	"commits",
	"communication",
	"issues",
	"milestones",
] as const satisfies ReadonlyArray<AchievementCategory>;

/**
 * Generates the UIAchievement from a normal array of achievements queried from the backend for the user.
 */
export function enhanceAchievements(achievements: Achievement[]): UIAchievement[] {
	return achievements.map(
		(achievement) =>
			({
				...achievement,
				...ACHIEVEMENT_REGISTRY[achievement.id],
			}) satisfies UIAchievement,
	);
}

/**
 * Generates React Flow nodes and edges for the skill tree visualization.
 *
 * @param user - User information for the central avatar node
 * @param achievements - Array of UIAchievements from the API
 * @returns Nodes and edges for React Flow
 */
export function generateSkillTreeData(
	user: {
		name: string;
		avatarUrl: string;
		level: number;
		leaguePoints: number;
	},
	achievements: UIAchievement[] = [],
): {
	nodes: (AchievementNode | AvatarNode)[];
	edges: AchievementEdge[];
} {
	const nodes: (AchievementNode | AvatarNode)[] = [];

	// Build lookup map for achievements by ID
	const achievementMap = new Map(achievements.map((a) => [a.id, a]));

	const avatarNode = {
		id: "root-avatar",
		position: {
			x: 0,
			y: 0,
		},
		data: {
			level: user?.level ?? 0,
			leaguePoints: user?.leaguePoints ?? 0,
			avatarUrl: user?.avatarUrl ?? "",
			name: user?.name ?? "",
			className: undefined,
		},
		type: "avatar",
		zIndex: 10,
	} satisfies AvatarNode;

	// Add Central Avatar Node
	nodes.push(avatarNode);

	// Process all categories
	const processedEdges: AchievementEdge[] = [];

	for (const category of ACHIEVEMENT_CATEGORIES) {
		const categoryAchievements = achievements
			.filter((a) => a.category === category)
			.sort(compareByRarity);

		// Calculate basic level if not present, or use rarity
		// The API doesn't provide a numeric 'level' field directly in Achievement type
		// but 'rarity' maps to it. Or we use the 'parentId' chain depth.
		// For now, let's assume we can derive a visual 'level' from rarity or use a default.
		// data.ts used `achievement.level`. If that property is missing from the generated type,
		// we need to compute it.
		// Generated Achievement type does NOT have 'level'. It has 'rarity'.
		// We can map Rarity -> Level (Common=1, Mythic=6).

		// const baseAngle = categoryMeta[category].angle; // Deprecated! We use the coordinates dev tool serialization now

		for (const achievement of categoryAchievements) {
			const savedCoords = (coordinatesData as Record<string, { x: number; y: number }>)[
				achievement.id
			];
			const x = savedCoords?.x ?? 0;
			const y = savedCoords?.y ?? 0;

			nodes.push({
				id: `${achievement.id}-node`,
				position: {
					x: x,
					y: y,
				},
				data: {
					achievement: achievement,
					className: undefined,
				},
				type: "achievement",
				zIndex: 10,
			} satisfies AchievementNode);

			// nodes.push({
			// 	id: achievementId,
			// 	type: "achievement",
			// 	position: { x, y },
			// 	data: {
			// 		id: achievementId,
			// 		name: achievement.name ?? "Unknown",
			// 		description: achievement.description ?? "",
			// 		category: achievement.category ?? "milestones",
			// 		tier: rarity,
			// 		status: achievement.status ?? "locked",
			// 		icon: achievement.icon ?? "GitCommit",
			// 		progress: achievement.progress,
			// 		maxProgress: achievement.maxProgress,
			// 		unlockedAt: achievement.unlockedAt ? new Date(achievement.unlockedAt) : null,
			// 		level,
			// 		angle: baseAngle,
			// 		ring: level,
			// 	},
			// } satisfies AchievementNode);

			// Create edge based on parentId
			if (achievement.parent !== undefined) {
				const parent = achievementMap.get(achievement.parent);
				if (parent) {
					processedEdges.push({
						id: `${achievement.parent}-${achievement.id}-edge`,
						source: achievement.parent,
						target: achievement.id,
						data: {
							isEnabled: achievement.status !== "locked",
						},
					} satisfies AchievementEdge);
				}
				// id: `${achievement.parentId}-${achievementId}`,
				// source: achievement.parentId,
				// target: achievementId,
				// type: "skill",
				// data: {
				// 	active: parent.status === "unlocked" && achievement.status !== "locked",
				// },
			} else {
				// Level 1 achievements with no parent connect to root avatar
				processedEdges.push({
					id: `root-${achievement.id}-edge`,
					source: avatarNode.id,
					target: achievement.id,
					data: {
						isEnabled: achievement.status !== "locked",
					},
				} satisfies AchievementEdge);
			}
		}
	}

	return { nodes, edges: processedEdges };
}

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

	const byCategory = ACHIEVEMENT_CATEGORIES.reduce(
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

export class categoryMeta {}
