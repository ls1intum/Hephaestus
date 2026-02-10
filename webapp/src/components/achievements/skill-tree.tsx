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
import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { Achievement } from "@/api/types.gen";
import { AchievementNode } from "./achievement-node";
import { AvatarNode } from "./avatar-node";
import { type AchievementNodeData, generateSkillTreeData } from "./data";
import { SkillEdge } from "./skill-edge";

const nodeTypes: NodeTypes = {
	achievement: AchievementNode,
	avatar: AvatarNode,
};

const edgeTypes: EdgeTypes = {
	skill: SkillEdge,
};

// Theme detection for MiniMap colors (React Flow requires computed color strings)
function subscribeToTheme(callback: () => void) {
	const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
	// Also observe class changes on document.documentElement for manual theme toggle
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

export interface SkillTreeProps {
	user?: {
		name?: string;
		avatarUrl?: string;
		level?: number;
		leaguePoints?: number;
	};
	achievements?: Achievement[];
}

export function SkillTree({ user, achievements = [] }: SkillTreeProps) {
	const { nodes: initialNodes, edges: initialEdges } = useMemo(
		() => generateSkillTreeData(user, achievements),
		[user, achievements],
	);

	const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
	const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

	// Update nodes when user or achievements props change
	useEffect(() => {
		const { nodes: newNodes, edges: newEdges } = generateSkillTreeData(user, achievements);
		setNodes(newNodes);
		setEdges(newEdges);
	}, [user, achievements, setNodes, setEdges]);

	const isDark = useSyncExternalStore(subscribeToTheme, getIsDarkMode, () => true);

	return (
		<div className="w-full h-full">
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
				disableKeyboardA11y={true}
			>
				{/* Subtle dot grid background */}
				<Background gap={40} size={1} color="var(--border)" className="opacity-20" />

				{/* Controls */}
				<Controls
					className="bg-card! border-border! rounded-lg! overflow-hidden [&>button]:bg-card! [&>button]:border-border! [&>button]:text-foreground! [&>button:hover]:bg-secondary!"
					showInteractive={false}
				/>

				{/* Mini map */}
				<MiniMap
					nodeColor={(node) => {
						const data = node.data as AchievementNodeData;
						if (isDark) {
							// Dark mode: white nodes on dark bg
							if (data.status === "unlocked") return "rgba(255, 255, 255, 0.9)";
							if (data.status === "available") return "rgba(255, 255, 255, 0.4)";
							return "rgba(255, 255, 255, 0.15)";
						}
						// Light mode: dark nodes on light bg
						if (data.status === "unlocked") return "rgba(0, 0, 0, 0.85)";
						if (data.status === "available") return "rgba(0, 0, 0, 0.5)";
						return "rgba(0, 0, 0, 0.15)";
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
