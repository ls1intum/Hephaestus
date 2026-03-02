import { useViewport } from "@xyflow/react";
import { CategoryLabels } from "./CategoryLabels";

export function SkillTreeGraphBackground() {
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
				{/* Decorative rings scaling outwardly from origin */}
				<div className="w-[100px] h-[100px] rounded-full border border-primary/20 absolute -translate-x-1/2 -translate-y-1/2" />
				<div className="w-[150px] h-[150px] rounded-full border border-primary/20 absolute -translate-x-1/2 -translate-y-1/2" />
				<div className="w-[200px] h-[200px] rounded-full border border-primary/20 absolute -translate-x-1/2 -translate-y-1/2" />
				<div className="w-[250px] h-[250px] rounded-full border border-primary/10 absolute -translate-x-1/2 -translate-y-1/2" />
				<div className="w-[300px] h-[300px] rounded-full border border-primary/10 absolute -translate-x-1/2 -translate-y-1/2" />
				<div className="w-[375px] h-[375px] rounded-full border border-primary/10 absolute -translate-x-1/2 -translate-y-1/2" />
				<div className="w-[450px] h-[450px] rounded-full border border-primary/5 absolute -translate-x-1/2 -translate-y-1/2" />
				<div className="w-[525px] h-[525px] rounded-full border border-primary/5 absolute -translate-x-1/2 -translate-y-1/2" />

				{/* Category labels - Need a minor adjustment as they previously assumed a 100% wrapper container.
            We pass the relative context downwards now, or adjust CategoryLabels.tsx to be viewport aware. */}
				<CategoryLabels />
			</div>
		</div>
	);
}
