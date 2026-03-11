import {
	Background,
	Controls,
	MiniMap,
	ReactFlow,
	useEdgesState,
	useNodesState,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useEffect, useSyncExternalStore } from "react";
import type { AchievementNode } from "@/components/achievements/AchievementNode";
import type { AvatarNode } from "@/components/achievements/AvatarNode";
import type { CategoryLabelNode } from "@/components/achievements/CategoryLabels";
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
	generateSkillTreeData,
	getMiniMapNodeColor,
} from "@/components/achievements/utils";

const FIT_VIEW_OPTIONS = { padding: 0.15 } as const;
const PRO_OPTIONS = { hideAttribution: true } as const;

export interface SkillTreeProps {
	user: {
		name: string;
		avatarUrl: string;
		level: number;
		leaguePoints: number;
	};
	achievements: UIAchievement[];
}

export function SkillTree({ user, achievements }: SkillTreeProps) {
	const { name, avatarUrl, level, leaguePoints } = user;

	const [nodes, setNodes, onNodesChange] = useNodesState<
		AchievementNode | AvatarNode | CategoryLabelNode
	>([]);
	const [edges, setEdges, onEdgesChange] = useEdgesState<AnyAchievementEdge>([]);

	// Generate nodes/edges when user or achievements props change.
	// Depend on individual primitive fields instead of the `user` object reference
	// to avoid infinite re-renders when the parent creates a new object each render.
	useEffect(() => {
		const { nodes: newNodes, edges: newEdges } = generateSkillTreeData(
			{ name, avatarUrl, level, leaguePoints },
			achievements,
		);

		setNodes(newNodes);
		setEdges(newEdges);
	}, [name, avatarUrl, level, leaguePoints, achievements, setNodes, setEdges]);

	const isDark = useSyncExternalStore(subscribeToTheme, getIsDarkMode, () => true);

	return (
		<div className="w-full h-full relative">
			<ReactFlow
				nodes={nodes}
				edges={edges}
				onNodesChange={onNodesChange}
				onEdgesChange={onEdgesChange}
				nodeTypes={nodeTypes}
				edgeTypes={edgeTypes}
				fitView={true}
				fitViewOptions={FIT_VIEW_OPTIONS}
				minZoom={0.15}
				maxZoom={2.5}
				nodeOrigin={NODE_ORIGIN}
				proOptions={PRO_OPTIONS}
				className="bg-background"
				aria-label="Achievement skill tree"
				// Nodes should be not accessible besides selection for tooltip
				elementsSelectable={true}
				nodesDraggable={false}
				nodesConnectable={false}
				// Disable all keyboard props
				deleteKeyCode={null}
				selectionKeyCode={null}
				multiSelectionKeyCode={null}
				zoomActivationKeyCode={null}
				panActivationKeyCode={null}
			>
				<SkillTreeGraphBackground />
				{/* Subtle dot grid background */}
				<Background gap={40} size={1} color="var(--border)" className="opacity-20" />

				{/* Controls */}
				<Controls
					className="bg-card! border-border! rounded-lg! overflow-hidden [&>button]:bg-card! [&>button]:border-border! [&>button]:text-foreground! [&>button:hover]:bg-secondary!"
					showInteractive={false}
				/>

				{/* Mini map */}
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
