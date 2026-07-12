import { getArea } from "@/components/practices-design/shared/area-identity";
import { StatusDot, TrendGlyph } from "@/components/practices-design/shared/status-language";
import type { AreaSummary, PracticeAreaId } from "@/components/practices-design/shared/types";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

export interface AreaStatusStripProps {
	summaries: readonly AreaSummary[];
	/** Currently selected area filter, or null for "everything". */
	selectedAreaId: PracticeAreaId | null;
	onSelectArea: (areaId: PracticeAreaId | null) => void;
}

/**
 * The compact per-area strip that sits above the activity feed. One pill per area with
 * signal: icon, name, status dot, trend glyph. Pills act as feed filters, so "where do I
 * stand" and "show me the activity behind it" are the same gesture. Areas without activity
 * collapse into a single quiet counter instead of taking up space.
 */
export function AreaStatusStrip({ summaries, selectedAreaId, onSelectArea }: AreaStatusStripProps) {
	const active = summaries.filter((summary) => summary.status !== "NO_ACTIVITY");
	const quiet = summaries.filter((summary) => summary.status === "NO_ACTIVITY");
	return (
		<div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="Practice areas">
			{active.map((summary) => {
				const area = getArea(summary.areaId);
				const Icon = area.icon;
				const selected = selectedAreaId === summary.areaId;
				return (
					<button
						key={summary.areaId}
						type="button"
						aria-pressed={selected}
						onClick={() => onSelectArea(selected ? null : summary.areaId)}
						className={cn(
							"flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors",
							selected ? "border-ring bg-accent" : "border-border bg-background hover:bg-accent/50",
						)}
					>
						<Icon className={cn("size-3.5", area.iconClassName)} aria-hidden="true" />
						<span>{area.name}</span>
						<StatusDot status={summary.status} className="size-2" />
						<TrendGlyph trend={summary.trend} />
					</button>
				);
			})}
			{quiet.length > 0 && (
				<Tooltip>
					<TooltipTrigger
						render={<span className="cursor-help px-1.5 text-xs text-muted-foreground" />}
					>
						{quiet.length} quiet {quiet.length === 1 ? "area" : "areas"}
					</TooltipTrigger>
					<TooltipContent>
						No activity yet in {quiet.map((summary) => getArea(summary.areaId).name).join(", ")}
					</TooltipContent>
				</Tooltip>
			)}
		</div>
	);
}
