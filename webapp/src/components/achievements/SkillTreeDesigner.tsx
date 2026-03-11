import {
	Background,
	BackgroundVariant,
	Controls,
	MiniMap,
	type NodeChange,
	ReactFlow,
	useEdgesState,
	useNodesState,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useEffect, useState, useSyncExternalStore } from "react";
import { toast } from "sonner";
import type { AchievementId } from "@/api";
import type { Achievement } from "@/api/types.gen";
import type { AchievementNode } from "@/components/achievements/AchievementNode";
import type { AvatarNode } from "@/components/achievements/AvatarNode";
import type { CategoryLabelNode } from "@/components/achievements/CategoryLabels";
import { DesignerToolbar, type SnapGridSize } from "@/components/achievements/DesignerToolbar";
import { ACHIEVEMENT_REGISTRY } from "@/components/achievements/definitions";
import { SkillTreeGraphBackground } from "@/components/achievements/SkillTreeGraphBackground";
import {
	edgeTypes,
	getIsDarkMode,
	NODE_ORIGIN,
	nodeTypes,
	subscribeToTheme,
} from "@/components/achievements/skill-tree-shared";
import type { UIAchievement } from "@/components/achievements/types";
import {
	type AnyAchievementEdge,
	type EdgeDisplayMode,
	enhanceAchievements,
	generateSkillTreeData,
	getMiniMapNodeColor,
} from "@/components/achievements/utils";

const FIT_VIEW_OPTIONS = { padding: 0.15 } as const;
const PRO_OPTIONS = { hideAttribution: true } as const;

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
	const { name, avatarUrl, level, leaguePoints } = user;
	const [nodes, setNodes, onNodesChangeRef] = useNodesState<
		AchievementNode | AvatarNode | CategoryLabelNode
	>([]);
	const [edges, setEdges, onEdgesChange] = useEdgesState<AnyAchievementEdge>([]);
	const [isDraggable, setIsDraggable] = useState(true);
	const [isSnapping, setIsSnapping] = useState(true);
	const [showTooltips, setShowTooltips] = useState(true);
	const [edgeDisplayMode, setEdgeDisplayMode] = useState<EdgeDisplayMode>("equalizer-static");

	const [snapSize, setSnapSize] = useState<SnapGridSize>(24);

	function handleNodesChange(
		changes: NodeChange<AchievementNode | AvatarNode | CategoryLabelNode>[],
	) {
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
	}

	async function saveLayout() {
		const layoutMap = nodes.reduce<Record<string, { x: number; y: number }>>((coords, node) => {
			if (node.type === "avatar") coords.avatar = { x: 0, y: 0 };
			if (node.type === "categoryLabel") {
				coords[node.id] = {
					x: Math.round(node.position.x),
					y: Math.round(node.position.y),
				};
			}
			if (node.type === "achievement") {
				const rawId = node.data.achievement.id;
				coords[rawId] = {
					x: Math.round(node.position.x),
					y: Math.round(node.position.y),
				};
			}
			return coords;
		}, {});

		// Dev-only: saves layout to coordinates.json via Vite dev server plugin
		if (import.meta.env.DEV) {
			try {
				const res = await fetch("/__save-coordinates", {
					method: "POST",
					body: JSON.stringify(layoutMap, null, 2),
				});
				if (!res.ok) {
					toast.error(`Failed to save layout: ${res.status} ${res.statusText}`);
					return;
				}
				toast.success("Layout saved to coordinates.json!");
			} catch (err) {
				toast.error(
					`Failed to save layout: ${err instanceof Error ? err.message : "Network error"}`,
				);
			}
		}
	}

	useEffect(() => {
		let displayAchievements: UIAchievement[];

		if (allDefinitions && allDefinitions.length > 0) {
			displayAchievements = enhanceAchievements(allDefinitions).map((a) => ({
				...a,
				status: "unlocked" as const,
			}));
		} else {
			displayAchievements = Object.entries(ACHIEVEMENT_REGISTRY).map(
				([id, def]) =>
					({
						...def,
						id: id as AchievementId,
						status: "unlocked" as const,
						category: "milestones" as const,
						rarity: "common" as const,
						isHidden: false,
						progressData: { type: "BinaryAchievementProgress" as const, unlocked: true },
						unlockedAt: new Date(),
					}) satisfies UIAchievement,
			);
		}

		const { nodes: newNodes, edges: newEdges } = generateSkillTreeData(
			{ name, avatarUrl, level, leaguePoints },
			displayAchievements,
			edgeDisplayMode,
		);

		setNodes(newNodes);
		setEdges(newEdges);
	}, [name, avatarUrl, level, leaguePoints, allDefinitions, setNodes, setEdges, edgeDisplayMode]);

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
				nodes={nodes.map((n) => {
					if (n.type === "categoryLabel") return n;
					return {
						...n,
						data: { ...n.data, showTooltips },
					} as AchievementNode | AvatarNode;
				})}
				edges={edges}
				onNodesChange={handleNodesChange}
				onEdgesChange={onEdgesChange}
				nodeTypes={nodeTypes}
				edgeTypes={edgeTypes}
				onInit={(instance) => instance.fitView(FIT_VIEW_OPTIONS)}
				fitView={true}
				fitViewOptions={FIT_VIEW_OPTIONS}
				minZoom={0.15}
				maxZoom={2.5}
				nodeOrigin={NODE_ORIGIN}
				proOptions={PRO_OPTIONS}
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
					nodeColor={(node) => getMiniMapNodeColor(node, isDark)}
					maskColor={isDark ? "rgba(0, 0, 0, 0.85)" : "rgba(255, 255, 255, 0.85)"}
					className="bg-card/80! border-border! rounded-lg!"
					pannable={true}
					zoomable={true}
				/>
			</ReactFlow>
		</div>
	);
}
