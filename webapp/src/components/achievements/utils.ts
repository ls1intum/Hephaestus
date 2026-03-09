import type { Achievement } from "@/api";
import type { AchievementEdge } from "@/components/achievements/AchievementEdge.tsx";
import type { AchievementNode } from "@/components/achievements/AchievementNode.tsx";
import type { AvatarNode } from "@/components/achievements/AvatarNode.tsx";
import type { CategoryLabelNode } from "@/components/achievements/CategoryLabels.tsx";
import { ACHIEVEMENT_REGISTRY } from "@/components/achievements/definitions.ts";
import type { EqualizerEdge } from "@/components/achievements/EqualizerEdge.tsx";
import type { SynthwaveEdge } from "@/components/achievements/SynthwaveEdge.tsx";
import { categoryMeta } from "@/components/achievements/styles.ts";
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
	| "synthwave"
	| "equalizer-traveling"
	| "equalizer-static"
	| "equalizer-chain";

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
	edgeDisplayMode: EdgeDisplayMode = "equalizer-static",
): {
	nodes: (AchievementNode | AvatarNode | CategoryLabelNode)[];
	edges: AnyAchievementEdge[];
} {
	const nodes: (AchievementNode | AvatarNode | CategoryLabelNode)[] = [];

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

	// 1. First, pre-calculate all node depths and the tree's absolute maximum depth.
	// This ensures that all edges, even those created early in the loop, use the correct global maxDepth!
	const nodeDepths = new Map<string, number>();
	let maxTreeDepth = 0;

	const getNodeDepth = (id: string, visiting = new Set<string>()): number => {
		if (nodeDepths.has(id)) return nodeDepths.get(id)!;
		if (visiting.has(id)) {
			nodeDepths.set(id, 0);
			return 0;
		}
		visiting.add(id);
		const ach = achievementMap.get(id as any);
		const parentId = (ach as any)?.parentId ?? ach?.parent;
		if (!ach || parentId === undefined || parentId === id) {
			nodeDepths.set(id, 0);
			visiting.delete(id);
			return 0;
		}
		const depth = getNodeDepth(parentId, visiting) + 1;
		nodeDepths.set(id, depth);
		if (depth > maxTreeDepth) maxTreeDepth = depth;
		visiting.delete(id);
		return depth;
	};

	achievements.forEach((a) => getNodeDepth(a.id));

	// Helper to generate edge props with explicit type verification
	const getEdgeConfig = (
		isEnabled: boolean,
		mode: EdgeDisplayMode,
		depth: number,
		maxDepth: number,
	): Pick<AnyAchievementEdge, "type" | "data"> => {
		if (mode === "synthwave") {
			return {
				type: "synthwave",
				data: { isEnabled },
			};
		}

		if (mode.startsWith("equalizer")) {
			// Extract variant from mode
			let variant: "traveling" | "static" = "traveling";
			if (mode === "equalizer-static") {
				variant = "static";
			} else if (mode === "equalizer-chain") {
				variant = "static"; // Chain now uses the static outbursts but with depth-based timing
			}

			return {
				type: "equalizer",
				data: {
					isEnabled,
					variant,
					depth: mode === "equalizer-chain" ? depth : undefined,
					maxDepth: mode === "equalizer-chain" ? maxDepth : undefined,
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

		if (category !== "milestones") {
			const labelNodeId = `label-${category}`;
			const meta = categoryMeta[category];
			const labelSavedCoords = (coordinatesData as Record<string, { x: number; y: number }>)[
				labelNodeId
			];
			const labelRadius = 900;
			let x = labelSavedCoords?.x;
			let y = labelSavedCoords?.y;

			if (x === undefined || y === undefined) {
				const radians = (meta.angle * Math.PI) / 180;
				x = Math.round(Math.cos(radians) * labelRadius);
				y = Math.round(Math.sin(radians) * labelRadius);
			}

			nodes.push({
				id: labelNodeId,
				position: {
					x: x,
					y: y,
				},
				data: {
					category: category,
					name: meta.name,
				},
				type: "categoryLabel",
				zIndex: 5,
			} satisfies CategoryLabelNode);
		}

		for (const achievement of categoryAchievements) {
			const savedCoords = (coordinatesData as Record<string, { x: number; y: number }>)[
				achievement.id
			];

			// Support for mocked positions in Storybook (MockUIAchievement)
			const mockX = (achievement as { x?: number }).x;
			const mockY = (achievement as { y?: number }).y;

			const x = mockX ?? savedCoords?.x ?? 0;
			const y = mockY ?? savedCoords?.y ?? 0;

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
			const parentId = (achievement as any).parentId ?? achievement.parent;

			if (parentId === achievement.id) {
				// Standalone achievement: No edges at all (not even to avatar)
			} else if (parentId !== undefined) {
				const parent = achievementMap.get(parentId);
				if (parent) {
					// Active only when both parent and child are unlocked
					const isActive = parent.status === "unlocked" && achievement.status === "unlocked";
					const edgeDepth = getNodeDepth(parentId) + 1;
					const config = getEdgeConfig(isActive, edgeDisplayMode, edgeDepth, maxTreeDepth);
					processedEdges.push({
						id: `${parentId}-${achievement.id}-edge`,
						source: `${parentId}-node`,
						target: `${achievement.id}-node`,
						...config,
					} as AnyAchievementEdge);
				}
			} else {
				// Root-level achievements connect to the central avatar
				const config = getEdgeConfig(
					achievement.status === "unlocked",
					edgeDisplayMode,
					0,
					maxTreeDepth,
				);
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
