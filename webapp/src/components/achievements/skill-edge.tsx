import { BaseEdge, type EdgeProps } from "@xyflow/react";
import { memo } from "react";

interface SkillEdgeData {
	active?: boolean;
}

function SkillEdgeComponent(props: EdgeProps) {
	const { sourceX, sourceY, targetX, targetY, data } = props;
	const edgeData = data as SkillEdgeData | undefined;
	const isActive = edgeData?.active ?? false;

	// Create a straight line path (cleaner for radial layout)
	const edgePath = `M ${sourceX} ${sourceY} L ${targetX} ${targetY}`;

	return (
		<>
			{/* Glow effect for active edges */}
			{isActive && (
				<BaseEdge
					path={edgePath}
					style={{
						stroke: "var(--edge-glow)",
						strokeWidth: 6,
						filter: "blur(4px)",
					}}
				/>
			)}
			{/* Main edge */}
			<BaseEdge
				path={edgePath}
				style={{
					stroke: isActive ? "var(--edge-active)" : "var(--edge-inactive)",
					strokeWidth: isActive ? 1.5 : 1,
				}}
			/>
			{/* Animated particle for active edges */}
			{isActive && (
				<circle r="2.5" fill="var(--edge-active)">
					<animateMotion dur="3s" repeatCount="indefinite" path={edgePath} />
				</circle>
			)}
		</>
	);
}

export const SkillEdge = memo(SkillEdgeComponent);
