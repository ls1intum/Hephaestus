import type { Achievement } from "@/api";
import type { AchievementEdge } from "@/components/achievements/AchievementEdge.tsx";
import type { AchievementNode } from "@/components/achievements/AchievementNode.tsx";
import type { AvatarNode } from "@/components/achievements/AvatarNode.tsx";
import type { EqualizerEdge } from "@/components/achievements/EqualizerEdge.tsx";
import type { SynthwaveEdge } from "@/components/achievements/SynthwaveEdge.tsx";
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

export type EdgeDisplayMode =
	| "achievement"
	| "synthwave-neon"
	| "synthwave-rarity"
	| "equalizer-traveling"
	| "equalizer-static"
	| "equalizer-traveling-mono"
	| "equalizer-static-mono";

export type AnyAchievementEdge = AchievementEdge | SynthwaveEdge | EqualizerEdge;

/**
 * Generates React Flow nodes and edges for the skill tree visualization.
 *
 * @param user - User information for the central avatar node
 * @param achievements - Array of UIAchievements from the API
 * @param edgeDisplayMode - The selected edge style to preview
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
	edgeDisplayMode: EdgeDisplayMode = "achievement",
): {
	nodes: (AchievementNode | AvatarNode)[];
	edges: AnyAchievementEdge[];
} {
	const nodes: (AchievementNode | AvatarNode)[] = [];

	// Build lookup map for achievements by ID
	const achievementMap = new Map(achievements.map((a) => [a.id, a]));

	const avatarNode = {
		id: "avatar-node",
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
	const processedEdges: AnyAchievementEdge[] = [];

	// Helper to generate edge props with explicit type verification
	const getEdgeConfig = (isEnabled: boolean, mode: EdgeDisplayMode): Pick<AnyAchievementEdge, "type" | "data"> => {
		if (mode.startsWith("synthwave")) {
			const variant = mode.replace("synthwave-", "") as "neon" | "rarity";
			return {
				type: "synthwave",
				data: {
					isEnabled,
					variant,
				},
			};
		}

		if (mode.startsWith("equalizer")) {
			const parts = mode.replace("equalizer-", "").split("-");
			const variant = parts[0] as "traveling" | "static";
			const monochrome = parts.includes("mono");
			return {
				type: "equalizer",
				data: {
					isEnabled,
					variant,
					monochrome,
				},
			};
		}

		// Default to standard achievement
		return {
			type: "achievement",
			data: { isEnabled },
		};
	};

	for (const category of ACHIEVEMENT_CATEGORIES) {
		const categoryAchievements = achievements
			.filter((a) => a.category === category)
			.sort(compareByRarity);

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

			// Create edge based on parent relationship
			if (achievement.parent !== undefined) {
				const parent = achievementMap.get(achievement.parent);
				if (parent) {
					// Active only when both parent and child are unlocked
					const isActive = parent.status === "unlocked" && achievement.status === "unlocked";
					const config = getEdgeConfig(isActive, edgeDisplayMode);
					processedEdges.push({
						id: `${achievement.parent}-${achievement.id}-edge`,
						source: `${achievement.parent}-node`,
						target: `${achievement.id}-node`,
						...config,
					} as AnyAchievementEdge);
				}
			} else {
				// Root-level achievements connect to the central avatar
				const config = getEdgeConfig(achievement.status === "unlocked", edgeDisplayMode);
				processedEdges.push({
					id: `avatar-${achievement.id}-edge`,
					source: avatarNode.id,
					target: `${achievement.id}-node`,
					...config,
				} as AnyAchievementEdge);
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
