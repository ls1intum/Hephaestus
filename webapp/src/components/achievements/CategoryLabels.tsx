import type { Node, NodeProps } from "@xyflow/react";
import { defaultCategoryIcons } from "@/components/achievements/styles";

export type CategoryLabelNodeData = {
	category: keyof typeof defaultCategoryIcons;
	name: string;
};

export type CategoryLabelNode = Node<CategoryLabelNodeData, "categoryLabel">;

export function CategoryLabelNode({ data }: NodeProps<CategoryLabelNode>) {
	const Icon = defaultCategoryIcons[data.category];

	return (
		<div className="flex items-center gap-2 px-4 py-2 rounded-full backdrop-blur-md border bg-card/80 border-primary/20 text-foreground shadow-[0_0_10px_rgba(var(--shadow-rgb),0.05)] cursor-grab active:cursor-grabbing">
			{Icon && <Icon className="w-4 h-4" />}
			<span className="text-sm font-semibold whitespace-nowrap">{data.name}</span>
		</div>
	);
}
