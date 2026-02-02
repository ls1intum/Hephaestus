import { CircleDot, Eye, GitCommit, GitPullRequest, Layers, MessageSquare } from "lucide-react";
import type React from "react";
import { type AchievementCategory, categoryMeta } from "./data";

const categoryIcons: Record<AchievementCategory, React.ElementType> = {
	commits: GitCommit,
	pullRequests: GitPullRequest,
	reviews: Eye,
	issues: CircleDot,
	comments: MessageSquare,
	crossCategory: Layers,
};

export function CategoryLabels() {
	// Position labels around the outer edge of the skill tree
	const labelRadius = 850;

	// Filter out crossCategory as those achievements are positioned between main lines
	const mainCategories = Object.entries(categoryMeta).filter(([key]) => key !== "crossCategory");

	return (
		<div className="absolute inset-0 pointer-events-none">
			{mainCategories.map(([key, meta]) => {
				const category = key as AchievementCategory;
				const Icon = categoryIcons[category];
				const radians = (meta.angle * Math.PI) / 180;

				// Calculate position
				const x = 50 + (labelRadius / 20) * Math.cos(radians);
				const y = 50 + (labelRadius / 20) * Math.sin(radians);

				return (
					<div
						key={category}
						className="absolute transform -translate-x-1/2 -translate-y-1/2"
						style={{
							left: `${x}%`,
							top: `${y}%`,
						}}
					>
						<div className="flex items-center gap-2 px-4 py-2 rounded-full backdrop-blur-md border bg-card/80 border-primary/20 text-foreground shadow-[0_0_10px_rgba(var(--shadow-rgb),0.05)]">
							<Icon className="w-4 h-4" />
							<span className="text-sm font-semibold whitespace-nowrap">{meta.name}</span>
						</div>
					</div>
				);
			})}
		</div>
	);
}
