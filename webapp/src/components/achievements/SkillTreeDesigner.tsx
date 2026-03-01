import {
	Background,
	Controls,
	type EdgeTypes,
	MiniMap,
	type NodeTypes,
	ReactFlow,
	useEdgesState,
	useNodesState,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useEffect, useState, useSyncExternalStore } from "react";
import type { Achievement } from "@/api/types.gen";
import { ACHIEVEMENT_REGISTRY } from "@/components/achievements/definitions.ts";
import type { UIAchievement } from "@/components/achievements/types";
import { generateSkillTreeData } from "@/components/achievements/utils";
import { Label } from "@/components/ui/label.tsx";
import { Switch } from "@/components/ui/switch.tsx";
import { AchievementEdge } from "./AchievementEdge.tsx";
import { AchievementNode } from "./AchievementNode.tsx";
import { AvatarNode } from "./AvatarNode.tsx";

const nodeTypes: NodeTypes = {
	achievement: AchievementNode,
	avatar: AvatarNode,
};

const edgeTypes: EdgeTypes = {
	achievement: AchievementEdge,
};

function subscribeToTheme(callback: () => void) {
	const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
	const observer = new MutationObserver(callback);
	observer.observe(document.documentElement, {
		attributes: true,
		attributeFilter: ["class"],
	});
	mediaQuery.addEventListener("change", callback);
	return () => {
		mediaQuery.removeEventListener("change", callback);
		observer.disconnect();
	};
}

function getIsDarkMode() {
	return document.documentElement.classList.contains("dark");
}

export interface SkillTreeDesignerProps {
	user: {
		name: string;
		avatarUrl: string;
		level: number;
		leaguePoints: number;
	};
	allDefinitions?: Achievement[];
}

export function SkillTreeDesigner({ user, allDefinitions }: SkillTreeDesignerProps) {
	const [nodes, setNodes, onNodesChange] = useNodesState<(AchievementNode | AvatarNode)>([]);
	const [edges, setEdges, onEdgesChange] = useEdgesState<AchievementEdge>([]);
	const [isDesignerMode, setIsDesignerMode] = useState(true);

	const saveLayout = async () => {
		const layoutMap = nodes.reduce(
			(coords, node) => {
				if (node.type === "avatar") coords.avatar = { x: 0, y: 0 };
				if (node.type === "achievement") {
					const rawId = node.data.achievement.id;
					coords[rawId] = {
						x: Math.round(node.position.x),
						y: Math.round(node.position.y),
					};
				}
				return coords;
			},
			{} as Record<string, { x: number; y: number }>,
		);

		await fetch("/__save-coordinates", {
			method: "POST",
			body: JSON.stringify(layoutMap, null, 2),
		});
		alert("Layout saved to coordinates.json!");
	};

	useEffect(() => {
		let displayAchievements: UIAchievement[] = [];

		if (allDefinitions && allDefinitions.length > 0) {
			displayAchievements = allDefinitions.map((def) => {
				const registryItem = ACHIEVEMENT_REGISTRY[def.id as keyof typeof ACHIEVEMENT_REGISTRY];
				return {
					...def,
					status: "unlocked" as const,
					...registryItem,
				} as unknown as UIAchievement;
			});
		} else {
			displayAchievements = Object.entries(ACHIEVEMENT_REGISTRY).map(
				([id, def]) =>
					({
						...def,
						id,
						status: "unlocked",
					}) as unknown as UIAchievement,
			);
		}

		const { nodes: newNodes, edges: newEdges } = generateSkillTreeData(user, displayAchievements);

		setNodes(newNodes);
		setEdges(newEdges);
	}, [user, allDefinitions, setNodes, setEdges]);

	const isDark = useSyncExternalStore(subscribeToTheme, getIsDarkMode, () => true);

	return (
		<div className="w-full h-full relative">
			<div className="absolute top-4 right-4 z-50 flex items-center gap-4 bg-background p-3 rounded-lg shadow-lg border border-border">
				<div className="flex items-center space-x-2">
					<Switch
						id="designer-mode"
						checked={isDesignerMode}
						onCheckedChange={setIsDesignerMode}
					/>
					<Label htmlFor="designer-mode" className="cursor-pointer">
						Drag Nodes Enabled
					</Label>
				</div>

				<button
					type="button"
					onClick={saveLayout}
					className="px-3 py-1.5 bg-green-600 text-white text-sm font-semibold rounded shadow hover:bg-green-700 transition-colors"
				>
					Save Layout
				</button>
			</div>

			<ReactFlow
				nodes={nodes}
				edges={edges}
				onNodesChange={onNodesChange}
				onEdgesChange={onEdgesChange}
				nodeTypes={nodeTypes}
				edgeTypes={edgeTypes}
				onInit={(instance) => instance.fitView({ padding: 0.15 })}
				fitView={true}
				fitViewOptions={{ padding: 0.15 }}
				minZoom={0.15}
				maxZoom={2.5}
				proOptions={{ hideAttribution: true }}
				className="bg-background"
				elementsSelectable={true}
				nodesDraggable={isDesignerMode}
				nodesConnectable={false}
				deleteKeyCode={null}
				selectionKeyCode={null}
				multiSelectionKeyCode={null}
				zoomActivationKeyCode={null}
				panActivationKeyCode={null}
				disableKeyboardA11y={true}
			>
				<Background gap={40} size={1} color="var(--border)" className="opacity-20" />
				<Controls
					className="bg-card! border-border! rounded-lg! overflow-hidden [&>button]:bg-card! [&>button]:border-border! [&>button]:text-foreground! [&>button:hover]:bg-secondary!"
					showInteractive={false}
				/>
				<MiniMap
					nodeColor={(node: AchievementNode | AvatarNode) => {
						if (node.type === "achievement") {
							const status = node.data.achievement.status;
							if (isDark) {
								switch (status) {
									case "unlocked":
										return "rgba(255, 255, 255, 0.9)";
									case "available":
										return "rgba(255, 255, 255, 0.4)";
									case "locked":
										return "rgba(255, 255, 255, 0.15)";
									case "hidden":
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
								case "hidden":
									return "rgba(0, 0, 0, 0)";
							}
						}
						if (node.type === "avatar") {
							return isDark ? "rgba(187,247,208,0.85)" : "rgba(21,128,61,0.85)";
						}
						return isDark ? "rgba(255,255,255,0.10)" : "rgba(0,0,0,0.12)";
					}}
					maskColor={isDark ? "rgba(0, 0, 0, 0.85)" : "rgba(255, 255, 255, 0.85)"}
					className="bg-card/80! border-border! rounded-lg!"
					pannable={true}
					zoomable={true}
				/>
			</ReactFlow>
		</div>
	);
}
