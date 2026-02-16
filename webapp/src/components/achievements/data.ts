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
export interface AchievementNodeData {
	id: string;
	name: string;
	description: string;
	category: AchievementCategory;
	tier: AchievementRarity;
	status: AchievementStatus;
	icon: string;
	progress?: number;
	maxProgress?: number;
	unlockedAt?: Date | null;
	level: number;
	angle: number;
	ring: number;
	// For avatar node
	avatarUrl?: string;
	leaguePoints?: number;
	// Index signature required by React Flow's Node<T> constraint
	[key: string]: unknown;
}

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

/** Main categories for the skill tree branches */
const MAIN_CATEGORIES: AchievementCategory[] = [
	"commits",
	"pull_requests",
	"communication",
	"issues",
	"milestones",
];

/**
 * Generates React Flow nodes and edges for the skill tree visualization.
 *
 * @param user - User information for the central avatar node
 * @param achievements - Array of achievements from the API
 * @returns Nodes and edges for React Flow
 */
export function generateSkillTreeData(
	user?: {
		name?: string;
		avatarUrl?: string;
		level?: number;
		leaguePoints?: number;
	},
	achievements: Achievement[] = [],
): {
	nodes: Node<AchievementNodeData>[];
	edges: Edge[];
} {
	const nodes: Node<AchievementNodeData>[] = [];
	const centerX = 0;
	const centerY = 0;

	// Build lookup map for achievements by ID
	const achievementMap = new Map(achievements.map((a) => [a.id, a]));

	// Add Central Avatar Node
	nodes.push({
		id: "root-avatar",
		type: "avatar",
		position: { x: centerX, y: centerY },
		data: {
			id: "root-avatar",
			name: user?.name || "User",
			description: "You",
			category: "milestones", // Default to milestones (was CROSS_CATEGORY)
			tier: "legendary",
			status: "unlocked",
			icon: "User",
			angle: 0,
			ring: 0,
			level: user?.level ?? 42,
			leaguePoints: user?.leaguePoints ?? 1600,
			avatarUrl: user?.avatarUrl,
		},
		zIndex: 10,
	});

	// Process all categories
	const processedEdges: Edge[] = [];

	for (const category of MAIN_CATEGORIES) {
		const categoryAchievements = achievements
			.filter((a) => (a.category ?? "milestones") === category)
			.sort((a, b) => {
				const levelA = a.rarity ? RARITY_TO_LEVEL[a.rarity] : 0;
				const levelB = b.rarity ? RARITY_TO_LEVEL[b.rarity] : 0;
				// Fallback to progress or just ID if no level
				return levelA - levelB;
			});

		// Calculate basic level if not present, or use rarity
		// The API doesn't provide a numeric 'level' field directly in Achievement type
		// but 'rarity' maps to it. Or we use the 'parentId' chain depth.
		// For now, let's assume we can derive a visual 'level' from rarity or use a default.
		// data.ts used `achievement.level`. If that property is missing from the generated type,
		// we need to compute it.
		// Generated Achievement type does NOT have 'level'. It has 'rarity'.
		// We can map Rarity -> Level (Common=1, Mythic=6).

		const baseAngle = categoryMeta[category].angle;

		for (const achievement of categoryAchievements) {
			const rarity = achievement.rarity ?? "common";
			const level = RARITY_TO_LEVEL[rarity] || 1;
			const distance = levelDistances[level] || 400;
			const radians = (baseAngle * Math.PI) / 180;
			const x = centerX + distance * Math.cos(radians);
			const y = centerY + distance * Math.sin(radians);
			const achievementId = achievement.id ?? `unknown-${Math.random()}`;

			nodes.push({
				id: achievementId,
				type: "achievement",
				position: { x, y },
				data: {
					id: achievementId,
					name: achievement.name ?? "Unknown",
					description: achievement.description ?? "",
					category: achievement.category ?? "milestones",
					tier: rarity,
					status: achievement.status ?? "locked",
					icon: achievement.icon ?? "GitCommit",
					progress: achievement.progress,
					maxProgress: achievement.maxProgress,
					unlockedAt: achievement.unlockedAt ? new Date(achievement.unlockedAt) : null,
					level,
					angle: baseAngle,
					ring: level,
				},
			});

			// Create edge based on parentId
			if (achievement.parentId != null) {
				const parent = achievementMap.get(achievement.parentId);
				if (parent) {
					processedEdges.push({
						id: `${achievement.parentId}-${achievementId}`,
						source: achievement.parentId,
						target: achievementId,
						type: "skill",
						data: {
							active: parent.status === "unlocked" && achievement.status !== "locked",
						},
					});
				}
			} else if (level === 1) {
				// Level 1 achievements with no parent connect to root avatar
				processedEdges.push({
					id: `root-${achievementId}`,
					source: "root-avatar",
					target: achievementId,
					type: "skill",
					data: {
						active: achievement.status !== "locked",
					},
				});
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
