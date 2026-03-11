import type { Achievement } from "@/api";
import type { AchievementEdge } from "@/components/achievements/AchievementEdge";
import type { AchievementNode } from "@/components/achievements/AchievementNode";
import type { AvatarNode } from "@/components/achievements/AvatarNode";
import type { CategoryLabelNode } from "@/components/achievements/CategoryLabels";
import rawCoordinatesData from "@/components/achievements/coordinates.json";
import { ACHIEVEMENT_REGISTRY } from "@/components/achievements/definitions";
import type { EqualizerEdge } from "@/components/achievements/EqualizerEdge";
import type { SynthwaveEdge } from "@/components/achievements/SynthwaveEdge";
import { categoryMeta } from "@/components/achievements/styles";
import {
	type AchievementCategory,
	rarityWeights,
	type UIAchievement,
} from "@/components/achievements/types";

const coordinatesData: Record<string, { x: number; y: number }> = rawCoordinatesData;

export const compareByRarity = (a: Achievement, b: Achievement) => {
	return rarityWeights[a.rarity] - rarityWeights[b.rarity];
};

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
	const achievementMap = new Map<string, UIAchievement>(achievements.map((a) => [a.id, a]));

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
		const cached = nodeDepths.get(id);
		if (cached !== undefined) return cached;
		if (visiting.has(id)) {
			nodeDepths.set(id, 0);
			return 0;
		}
		visiting.add(id);
		const ach = achievementMap.get(id);
		const parentId = ach?.parent;
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

	// Helper to build a fully-typed edge with routing and data properties.
	const buildEdge = (
		id: string,
		source: string,
		target: string,
		isEnabled: boolean,
		mode: EdgeDisplayMode,
		depth: number,
		maxDepth: number,
	): AnyAchievementEdge => {
		const base = { id, source, target };

		if (mode === "synthwave") {
			return { ...base, type: "synthwave", data: { isEnabled } } satisfies SynthwaveEdge;
		}

		if (mode.startsWith("equalizer")) {
			let variant: "traveling" | "static" = "traveling";
			if (mode === "equalizer-static") {
				variant = "static";
			} else if (mode === "equalizer-chain") {
				variant = "static";
			}

			return {
				...base,
				type: "equalizer",
				data: {
					isEnabled,
					variant,
					depth: mode === "equalizer-chain" ? depth : undefined,
					maxDepth: mode === "equalizer-chain" ? maxDepth : undefined,
				},
			} satisfies EqualizerEdge;
		}

		return { ...base, type: "achievement", data: { isEnabled } } satisfies AchievementEdge;
	};

	for (const category of ACHIEVEMENT_CATEGORIES) {
		const categoryAchievements = achievements
			.filter((a) => a.category === category)
			.sort(compareByRarity);

		if (category !== "milestones") {
			const labelNodeId = `label-${category}`;
			const meta = categoryMeta[category];
			const labelSavedCoords = coordinatesData[labelNodeId];
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
			const savedCoords = coordinatesData[achievement.id];

			// Support for mocked positions in Storybook (MockUIAchievement)
			const mockPos = achievement as UIAchievement & { x?: number; y?: number };
			const x = mockPos.x ?? savedCoords?.x ?? 0;
			const y = mockPos.y ?? savedCoords?.y ?? 0;

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
			const parentId = achievement.parent;

			if (parentId === achievement.id) {
				// Standalone achievement: No edges at all (not even to avatar)
			} else if (parentId !== undefined) {
				const parent = achievementMap.get(parentId);
				if (parent) {
					// Active only when both parent and child are unlocked
					const isActive = parent.status === "unlocked" && achievement.status === "unlocked";
					const edgeDepth = getNodeDepth(parentId) + 1;
					processedEdges.push(
						buildEdge(
							`${parentId}-${achievement.id}-edge`,
							`${parentId}-node`,
							`${achievement.id}-node`,
							isActive,
							edgeDisplayMode,
							edgeDepth,
							maxTreeDepth,
						),
					);
				}
			} else {
				// Root-level achievements connect to the central avatar
				processedEdges.push(
					buildEdge(
						`avatar-${achievement.id}-edge`,
						avatarNode.id,
						`${achievement.id}-node`,
						achievement.status === "unlocked",
						edgeDisplayMode,
						0,
						maxTreeDepth,
					),
				);
			}
		}
	}

	return { nodes, edges: processedEdges };
}

/**
 * Shared nodeColor callback for React Flow MiniMap.
 * Both SkillTree and SkillTreeDesigner use identical logic.
 */
export function getMiniMapNodeColor(
	node: { type?: string; data?: { achievement?: { status?: string } } },
	isDark: boolean,
): string {
	if (node.type === "categoryLabel") {
		return "rgba(0,0,0,0)";
	}
	if (node.type === "achievement") {
		const status = node.data?.achievement?.status;
		if (isDark) {
			switch (status) {
				case "unlocked":
					return "rgba(255, 255, 255, 0.9)";
				case "available":
					return "rgba(255, 255, 255, 0.4)";
				case "locked":
					return "rgba(255, 255, 255, 0.15)";
				default:
					return "rgba(0, 0, 0, 0)";
			}
		}
		switch (status) {
			case "unlocked":
				return "rgba(0, 0, 0, 0.85)";
			case "available":
				return "rgba(0, 0, 0, 0.5)";
			case "locked":
				return "rgba(0, 0, 0, 0.15)";
			default:
				return "rgba(0, 0, 0, 0)";
		}
	}
	if (node.type === "avatar") {
		return isDark ? "rgba(187,247,208,0.85)" : "rgba(21,128,61,0.85)";
	}
	return isDark ? "rgba(255,255,255,0.10)" : "rgba(0,0,0,0.12)";
}

/**
 * Calculates achievement statistics for the stats panel.
 *
 * @param achievementList - Array of achievements from the API
 * @returns Statistics including totals and per-category breakdowns
 */
export function calculateStats(achievementList: Achievement[]) {
	const total = achievementList.length;
	let unlocked = 0;
	let available = 0;

	// Initialize per-category counters
	const byCategory: Record<AchievementCategory, { total: number; unlocked: number }> = {
		pull_requests: { total: 0, unlocked: 0 },
		commits: { total: 0, unlocked: 0 },
		communication: { total: 0, unlocked: 0 },
		issues: { total: 0, unlocked: 0 },
		milestones: { total: 0, unlocked: 0 },
	};

	// Single pass over the array
	for (const a of achievementList) {
		if (a.status === "unlocked") unlocked++;
		else if (a.status === "available") available++;

		const cat = a.category ?? "milestones";
		const entry = byCategory[cat];
		if (entry) {
			entry.total++;
			if (a.status === "unlocked") entry.unlocked++;
		}
	}

	return {
		total,
		unlocked,
		available,
		percentage: total > 0 ? Math.round((unlocked / total) * 100) : 0,
		byCategory,
	};
}
