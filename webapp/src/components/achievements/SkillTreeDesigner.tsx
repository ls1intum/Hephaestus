import {
	Background,
	BackgroundVariant,
	Controls,
	type EdgeTypes,
	MiniMap,
	type NodeTypes,
	ReactFlow,
	useEdgesState,
	useNodesState,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import type { Achievement } from "@/api/types.gen";
import { ACHIEVEMENT_REGISTRY } from "@/components/achievements/definitions.ts";
import type { UIAchievement } from "@/components/achievements/types";
import {
	type AnyAchievementEdge,
	type EdgeDisplayMode,
	generateSkillTreeData,
} from "@/components/achievements/utils";
import { AchievementEdge } from "./AchievementEdge.tsx";
import { AchievementNode } from "./AchievementNode.tsx";
import { AvatarNode } from "./AvatarNode.tsx";
import { DesignerToolbar, type SnapGridSize } from "./DesignerToolbar.tsx";
import { EqualizerEdge } from "./EqualizerEdge.tsx";
import { SkillTreeGraphBackground } from "./SkillTreeGraphBackground.tsx";
import { SynthwaveEdge } from "./SynthwaveEdge.tsx";

const nodeTypes: NodeTypes = {
	achievement: AchievementNode,
	avatar: AvatarNode,
};

const edgeTypes: EdgeTypes = {
	achievement: AchievementEdge,
	synthwave: SynthwaveEdge,
	equalizer: EqualizerEdge,
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
	const [nodes, setNodes, onNodesChangeRef] = useNodesState<AchievementNode | AvatarNode>([]);
	const [edges, setEdges, onEdgesChange] = useEdgesState<AnyAchievementEdge>([]);
	const [isDraggable, setIsDraggable] = useState(true);
	const [isSnapping, setIsSnapping] = useState(true);
	const [showTooltips, setShowTooltips] = useState(true);
	const [edgeDisplayMode, setEdgeDisplayMode] = useState<EdgeDisplayMode>("equalizer-static");

	const [snapSize, setSnapSize] = useState<SnapGridSize>(24);

	const handleNodesChange = useCallback(
		(changes: any[]) => {
			if (isSnapping) {
				const snappedChanges = changes.map((change) => {
					if (change.type === "position" && change.position) {
						// Custom Snapping: Since nodeOrigin=[0.5, 0.5], the position reflects the center of the node!
						// By snapping this center directly here, we guarantee absolute perfect alignment
						// regardless of node width/height.
						return {
							...change,
							position: {
								x: Math.round(change.position.x / snapSize) * snapSize,
								y: Math.round(change.position.y / snapSize) * snapSize,
							},
						};
					}
					return change;
				});
				onNodesChangeRef(snappedChanges);
			} else {
				onNodesChangeRef(changes);
			}
		},
		[isSnapping, snapSize, onNodesChangeRef],
	);

	const saveLayout = useCallback(async () => {
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
	}, [nodes]);

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

		const { nodes: newNodes, edges: newEdges } = generateSkillTreeData(
			user,
			displayAchievements,
			edgeDisplayMode,
		);

		setNodes(newNodes);
		setEdges(newEdges);
	}, [user, allDefinitions, setNodes, setEdges, edgeDisplayMode]);

	const isDark = useSyncExternalStore(subscribeToTheme, getIsDarkMode, () => true);

	return (
		<div className="w-full h-full relative">
			{/* Designer Toolbar */}
			<DesignerToolbar
				isDraggable={isDraggable}
				onDraggableChange={setIsDraggable}
				isSnapping={isSnapping}
				onSnappingChange={setIsSnapping}
				snapSize={snapSize}
				onSnapSizeChange={setSnapSize}
				showTooltips={showTooltips}
				onShowTooltipsChange={setShowTooltips}
				onSave={saveLayout}
				edgeDisplayMode={edgeDisplayMode}
				onEdgeDisplayModeChange={setEdgeDisplayMode}
			/>

			<ReactFlow
				nodes={nodes.map(
					(n) =>
						({
							...n,
							data: { ...n.data, showTooltips },
						}) as AchievementNode | AvatarNode,
				)}
				edges={edges}
				onNodesChange={handleNodesChange}
				onEdgesChange={onEdgesChange}
				nodeTypes={nodeTypes}
				edgeTypes={edgeTypes}
				onInit={(instance) => instance.fitView({ padding: 0.15 })}
				fitView={true}
				fitViewOptions={{ padding: 0.15 }}
				minZoom={0.15}
				maxZoom={2.5}
				nodeOrigin={[0.5, 0.5]}
				proOptions={{ hideAttribution: true }}
				className="bg-background"
				elementsSelectable={true}
				nodesDraggable={isDraggable}
				nodesConnectable={false}
				// Disable native snap to prevent it snapping the exact top-left corner
				snapToGrid={false}
				deleteKeyCode={null}
				selectionKeyCode={null}
				multiSelectionKeyCode={null}
				zoomActivationKeyCode={null}
				panActivationKeyCode={null}
				disableKeyboardA11y={true}
			>
				{/* Custom origin-synchronized background (axes + category labels) */}
				<SkillTreeGraphBackground showAxes={true} />

				{/* Fine subdivided dot grid */}
				<Background
					id="designer-grid-fine"
					gap={isSnapping ? snapSize : 24}
					size={1}
					className="opacity-60"
					variant={BackgroundVariant.Dots}
				/>
				{/* Major grid lines – lock to 96px for visual consistency */}
				<Background
					id="designer-grid-major"
					gap={isSnapping && snapSize > 96 ? snapSize : 96}
					lineWidth={1}
					color="var(--border)"
					className="opacity-80"
					variant={BackgroundVariant.Lines}
				/>

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
