import { useViewport } from "@xyflow/react";
import { CategoryLabels } from "./CategoryLabels";

interface SkillTreeGraphBackgroundProps {
	/** If true, renders designer-mode axis lines through the origin. */
	showAxes?: boolean;
}

export function SkillTreeGraphBackground({ showAxes = false }: SkillTreeGraphBackgroundProps) {
	// Synchronize with the ReactFlow viewport transformations
	const { x, y, zoom } = useViewport();

	return (
		<div
			className="absolute inset-0 pointer-events-none z-[-1]"
			style={{
				transform: `translate(${x}px, ${y}px) scale(${zoom})`,
				transformOrigin: "0 0",
			}}
		>
			{/* Origin container aligned with the avatar root node (0,0) + 48px to offset to its exact geometric center */}
			<div className="absolute top-[48px] left-[48px] w-0 h-0 flex items-center justify-center">
				{/* X and Y Axes for Designer Mode – uses an SVG for perfectly centered, infinite-looking crosshair */}
				{showAxes && (
					<svg
						className="absolute"
						style={{
							width: "20000px",
							height: "20000px",
							left: "-10000px",
							top: "-10000px",
							overflow: "visible",
						}}
					>
						{/* X-axis (horizontal) */}
						<line
							x1="0"
							y1="10000"
							x2="20000"
							y2="10000"
							stroke="currentColor"
							strokeWidth="2"
							className="text-primary/30"
						/>
						{/* Y-axis (vertical) */}
						<line
							x1="10000"
							y1="0"
							x2="10000"
							y2="20000"
							stroke="currentColor"
							strokeWidth="2"
							className="text-primary/30"
						/>
						{/* Origin dot */}
						<circle
							cx="10000"
							cy="10000"
							r="4"
							fill="currentColor"
							className="text-primary/60"
						/>
					</svg>
				)}

				{/* Decorative rings scaling outwardly from origin (only in non-designer / regular view) */}
				{!showAxes && (
					<>
						<div className="w-[100px] h-[100px] rounded-full border border-primary/20 absolute -translate-x-1/2 -translate-y-1/2" />
						<div className="w-[150px] h-[150px] rounded-full border border-primary/20 absolute -translate-x-1/2 -translate-y-1/2" />
						<div className="w-[200px] h-[200px] rounded-full border border-primary/20 absolute -translate-x-1/2 -translate-y-1/2" />
						<div className="w-[250px] h-[250px] rounded-full border border-primary/10 absolute -translate-x-1/2 -translate-y-1/2" />
						<div className="w-[300px] h-[300px] rounded-full border border-primary/10 absolute -translate-x-1/2 -translate-y-1/2" />
						<div className="w-[375px] h-[375px] rounded-full border border-primary/10 absolute -translate-x-1/2 -translate-y-1/2" />
						<div className="w-[450px] h-[450px] rounded-full border border-primary/5 absolute -translate-x-1/2 -translate-y-1/2" />
						<div className="w-[525px] h-[525px] rounded-full border border-primary/5 absolute -translate-x-1/2 -translate-y-1/2" />
					</>
				)}

				{/* Category labels */}
				<CategoryLabels />
			</div>
		</div>
	);
}
