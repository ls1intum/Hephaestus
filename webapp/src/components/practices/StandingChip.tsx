import type { PracticeReportCard } from "@/api/types.gen";
import { cn } from "@/lib/utils";

export type Standing = PracticeReportCard["standing"];

// A standing describes where a developer stands on a practice against the criteria — it is NOT a
// rank against other people and carries no number. STRENGTH reads positive, DEVELOPING reads as a
// gentle attention tone, MIXED stays neutral. Single source of truth for the self-view card and the
// mentor roster so their chips never drift.
export const STANDING_META: Record<Standing, { label: string; className: string }> = {
	STRENGTH: {
		label: "Strength",
		className: "bg-provider-done/15 text-provider-done-foreground",
	},
	DEVELOPING: {
		label: "Developing",
		className: "bg-provider-attention/15 text-provider-attention-foreground",
	},
	MIXED: {
		label: "Mixed",
		className: "bg-muted text-muted-foreground",
	},
};

export function StandingChip({ standing }: { standing: Standing }) {
	const meta = STANDING_META[standing];
	return (
		<span
			className={cn(
				"inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium",
				meta.className,
			)}
		>
			{meta.label}
		</span>
	);
}
