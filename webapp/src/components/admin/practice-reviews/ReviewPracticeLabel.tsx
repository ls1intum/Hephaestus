import type { ReviewPracticeArea } from "@/api/types.gen";
import { getAreaVisual } from "@/components/admin/practice-catalog/area-visuals";
import { cn } from "@/lib/utils";

export interface ReviewPracticeLabelProps {
	area: ReviewPracticeArea | undefined;
	practiceName: string;
	display?: "compact" | "full";
}

export function ReviewPracticeLabel({
	area,
	practiceName,
	display = "compact",
}: ReviewPracticeLabelProps) {
	if (!area) {
		return (
			<span className={cn("text-sm font-medium", display === "full" && "break-words")}>
				{practiceName}
			</span>
		);
	}
	const { Icon, pill } = getAreaVisual(area.slug, area.name, area.icon, area.color);
	return (
		<span className="flex min-w-0 items-start gap-2 text-sm">
			<span className={cn("flex size-6 shrink-0 items-center justify-center rounded-md", pill)}>
				<Icon className="size-3.5" aria-hidden />
			</span>
			<span className="min-w-0">
				<span
					className={cn("block font-medium", display === "compact" ? "truncate" : "break-words")}
				>
					{practiceName}
				</span>
				<span
					className={cn(
						"block text-xs text-muted-foreground",
						display === "compact" ? "truncate" : "break-words",
					)}
				>
					{area.name}
				</span>
			</span>
		</span>
	);
}
