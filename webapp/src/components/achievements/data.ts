import type { Edge, Node } from "@xyflow/react";
import {
	type AchievementCategory,
	type AchievementDTO,
	type AchievementTier,
	levelToTier,
	normalizeStatus,
} from "./types";

/**
 * Node data structure for the skill tree visualization.
 * Extends AchievementDTO with positioning information.
 */
export interface AchievementNodeData {
	id: string;
	name: string;
	description: string;
	category: AchievementCategory;
	tier: AchievementTier;
	status: "locked" | "available" | "unlocked";
	icon: string;
	progress?: number;
	maxProgress?: number;
	unlockedAt?: string | null;
	level: number;
	angle: number;
	ring: number;
	// For avatar node
	avatarUrl?: string;
	leaguePoints?: number;
	// Index signature required by React Flow's Node<T> constraint
	[key: string]: unknown;
}

/**
 * Category metadata for positioning achievements on the skill tree.
 * Keys match backend enum values (uppercase).
 */
export const categoryMeta: Record<
	AchievementCategory,
	{ name: string; angle: number; description: string }
> = {
	COMMITS: {
		name: "Commits",
		angle: 270, // Top (12 o'clock)
		description: "Track your code contributions",
	},
	PULL_REQUESTS: {
		name: "Pull Requests",
		angle: 342, // Top-right (2 o'clock)
		description: "Submit and merge code changes",
	},
	REVIEWS: {
		name: "Reviews",
		angle: 54, // Right (4 o'clock)
		description: "Help improve code quality",
	},
	ISSUES: {
		name: "Issues",
		angle: 126, // Bottom-right (7 o'clock)
		description: "Report and track work items",
	},
	COMMENTS: {
		name: "Comments",
		angle: 198, // Bottom-left (9 o'clock)
		description: "Engage in discussions",
	},
	CROSS_CATEGORY: {
		name: "Milestones",
		angle: 0, // Distributed between branches
		description: "Combined achievements",
	},
};

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
	"COMMITS",
	"PULL_REQUESTS",
	"REVIEWS",
	"ISSUES",
	"COMMENTS",
];

/**
 * Generates React Flow nodes and edges for the skill tree visualization.
 *
 * @param user - User information for the central avatar node
 * @param achievements - Array of achievement DTOs from the API
 * @returns Nodes and edges for React Flow
 */
export function generateSkillTreeData(
	user?: {
		name?: string;
		avatarUrl?: string;
		level?: number;
		leaguePoints?: number;
	},
	achievements: AchievementDTO[] = [],
): {
	nodes: Node<AchievementNodeData>[];
	edges: Edge[];
} {
	const nodes: Node<AchievementNodeData>[] = [];
	const edges: Edge[] = [];
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
			category: "CROSS_CATEGORY",
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

	// Process main category achievements (non-cross-category)
	for (const category of MAIN_CATEGORIES) {
		const categoryAchievements = achievements
			.filter((a) => a.category === category)
			.sort((a, b) => a.level - b.level);
		const baseAngle = categoryMeta[category].angle;

		for (const achievement of categoryAchievements) {
			const distance = levelDistances[achievement.level] || 400;
			const radians = (baseAngle * Math.PI) / 180;
			const x = centerX + distance * Math.cos(radians);
			const y = centerY + distance * Math.sin(radians);

			nodes.push({
				id: achievement.id,
				type: "achievement",
				position: { x, y },
				data: {
					id: achievement.id,
					name: achievement.name,
					description: achievement.description,
					category: achievement.category,
					tier: levelToTier(achievement.level),
					status: normalizeStatus(achievement.status),
					icon: achievement.icon,
					progress: achievement.progress,
					maxProgress: achievement.maxProgress,
					unlockedAt: achievement.unlockedAt,
					level: achievement.level,
					angle: baseAngle,
					ring: achievement.level,
				},
			});

			// Create edge based on parentId
			if (achievement.parentId !== null) {
				const parent = achievementMap.get(achievement.parentId);
				if (parent) {
					edges.push({
						id: `${achievement.parentId}-${achievement.id}`,
						source: achievement.parentId,
						target: achievement.id,
						type: "skill",
						data: {
							active:
								normalizeStatus(parent.status) === "unlocked" &&
								normalizeStatus(achievement.status) !== "locked",
						},
					});
				}
			} else if (achievement.level === 1) {
				// Level 1 achievements with no parent connect to root avatar
				edges.push({
					id: `root-${achievement.id}`,
					source: "root-avatar",
					target: achievement.id,
					type: "skill",
					data: {
						active: normalizeStatus(achievement.status) !== "locked",
					},
				});
			}
		}
	}

	// Process cross-category achievements
	const crossAchievements = achievements.filter((a) => a.category === "CROSS_CATEGORY");

	for (const achievement of crossAchievements) {
		// Position cross-category achievements based on level and a computed angle
		// Use level to determine distance and spread them around
		const distance = levelDistances[achievement.level] || 400;
		// Spread cross-category achievements between main branches
		const crossIndex = crossAchievements.indexOf(achievement);
		const baseAngle = (crossIndex * 72 + 36) % 360; // Offset from main branch angles

		const radians = (baseAngle * Math.PI) / 180;
		const x = centerX + distance * Math.cos(radians);
		const y = centerY + distance * Math.sin(radians);

		nodes.push({
			id: achievement.id,
			type: "achievement",
			position: { x, y },
			data: {
				id: achievement.id,
				name: achievement.name,
				description: achievement.description,
				category: achievement.category,
				tier: levelToTier(achievement.level),
				status: normalizeStatus(achievement.status),
				icon: achievement.icon,
				progress: achievement.progress,
				maxProgress: achievement.maxProgress,
				unlockedAt: achievement.unlockedAt,
				level: achievement.level,
				angle: baseAngle,
				ring: Math.floor(distance / 150),
			},
		});

		// Create edges for cross-category achievements
		if (achievement.parentId !== null) {
			const parent = achievementMap.get(achievement.parentId);
			if (parent) {
				edges.push({
					id: `${achievement.parentId}-${achievement.id}`,
					source: achievement.parentId,
					target: achievement.id,
					type: "skill",
					data: {
						active:
							normalizeStatus(parent.status) === "unlocked" &&
							normalizeStatus(achievement.status) !== "locked",
					},
				});
			}
		}
	}

	return { nodes, edges };
}

/**
 * Calculates achievement statistics for the stats panel.
 *
 * @param achievementList - Array of achievement DTOs
 * @returns Statistics including totals and per-category breakdowns
 */
export function calculateStats(achievementList: AchievementDTO[]) {
	const total = achievementList.length;
	const unlocked = achievementList.filter((a) => a.status === "UNLOCKED").length;
	const available = achievementList.filter((a) => a.status === "AVAILABLE").length;

	const allCategories: AchievementCategory[] = [...MAIN_CATEGORIES, "CROSS_CATEGORY"];

	const byCategory = allCategories.reduce(
		(acc, cat) => {
			const catAchievements = achievementList.filter((a) => a.category === cat);
			acc[cat] = {
				total: catAchievements.length,
				unlocked: catAchievements.filter((a) => a.status === "UNLOCKED").length,
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
