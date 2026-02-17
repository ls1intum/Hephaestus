import { BaseEdge, type Edge, type EdgeProps } from "@xyflow/react";

export type AchievementEdge = Edge<{ isEnabled: boolean }, "achievement">;

export function AchievementEdge(props: EdgeProps<AchievementEdge>) {
	const { sourceX, sourceY, targetX, targetY, data } = props;
	const isEnabled = data?.isEnabled ?? false;

	// Create a straight line path (cleaner for radial layout)
	const edgePath = `M ${sourceX} ${sourceY} L ${targetX} ${targetY}`;

	return (
		<>
			{/* Glow effect for active edges */}
			{isEnabled && (
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
					stroke: isEnabled ? "var(--edge-active)" : "var(--edge-inactive)",
					strokeWidth: isEnabled ? 1.5 : 1,
				}}
			/>
			{/* Animated particle for active edges */}
			{isEnabled && (
				<circle r="2.5" fill="var(--edge-active)">
					<animateMotion dur="3s" repeatCount="indefinite" path={edgePath} />
				</circle>
			)}
		</>
	);
}

export class SkillEdge {}
