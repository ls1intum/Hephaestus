import type { AchievementCategory } from "@/components/achievements/types.ts";
import { categoryMeta, defaultCategoryIcons } from "./styles.ts";

export function CategoryLabels() {
	// Position labels around the outer edge of the skill tree
	const labelRadius = 850;

	const mainCategories = Object.entries(categoryMeta);

	return (
		<>
			{mainCategories
				.filter(([key]) => key !== "milestones")
				.map(([key, meta]) => {
					const category = key as AchievementCategory;
					const Icon = defaultCategoryIcons[category];
					const radians = (meta.angle * Math.PI) / 180;

					// Calculate position
					const x = Math.cos(radians) * labelRadius;
					const y = Math.sin(radians) * labelRadius;

					return (
						<div
							key={category}
							className="absolute transform -translate-x-1/2 -translate-y-1/2"
							style={{
								left: `${x}px`,
								top: `${y}px`,
							}}
						>
							<div className="flex items-center gap-2 px-4 py-2 rounded-full backdrop-blur-md border bg-card/80 border-primary/20 text-foreground shadow-[0_0_10px_rgba(var(--shadow-rgb),0.05)]">
								<Icon className="w-4 h-4" />
								<span className="text-sm font-semibold whitespace-nowrap">{meta.name}</span>
							</div>
						</div>
					);
				})}
		</>
	);
}
