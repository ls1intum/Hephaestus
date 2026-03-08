import { useViewport } from "@xyflow/react";

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
			{/* Origin container aligned with the avatar center (0,0) — nodeOrigin [0.5, 0.5] ensures this */}
			<div className="absolute top-0 left-0 w-0 h-0 flex items-center justify-center">
				{/* X and Y Axes for Designer Mode – uses an SVG for perfectly centered, infinite-looking crosshair */}
				{showAxes && (
					<svg
						className="absolute"
						aria-hidden="true"
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
						<circle cx="10000" cy="10000" r="4" fill="currentColor" className="text-primary/60" />
					</svg>
				)}

				{/* Decorative rings scaling outwardly from origin (only in non-designer / regular view) */}
				{!showAxes && (
					<>
						<div className="w-20 h-20 rounded-full border border-primary/20 absolute" />
						<div className="w-40 h-40 rounded-full border border-primary/20 absolute" />
						<div className="w-60 h-60 rounded-full border border-primary/20 absolute" />
						<div className="w-80 h-80 rounded-full border border-primary/10 absolute" />
						<div className="w-100 h-100 rounded-full border border-primary/10 absolute" />
						<div className="w-120 h-120 rounded-full border border-primary/10 absolute" />
						<div className="w-140 h-140 rounded-full border border-primary/5 absolute" />
						<div className="w-160 h-160 rounded-full border border-primary/5 absolute" />
					</>
				)}
			</div>
		</div>
	);
}
