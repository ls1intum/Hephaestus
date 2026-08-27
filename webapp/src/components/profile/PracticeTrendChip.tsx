import type { PracticeTrend, TrendSupport } from "@/api/types.gen";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { formatTrendProvenance, PRACTICE_TREND_PRESENTATION } from "./practice-trend-presentation";

const TONE_CLASS = {
	positive: "text-success",
	negative: "text-destructive",
	neutral: "text-muted-foreground",
	muted: "text-muted-foreground",
} as const;

export interface PracticeTrendChipProps {
	direction: PracticeTrend["direction"];
	support: TrendSupport;
	className?: string;
}

/** Keyboard-reachable compact direction; provenance remains supplementary in its tooltip. */
export function PracticeTrendChip({ direction, support, className }: PracticeTrendChipProps) {
	const presentation = PRACTICE_TREND_PRESENTATION[direction];
	return (
		<Tooltip>
			<TooltipTrigger
				render={
					<button
						type="button"
						className={cn(
							"relative z-20 flex w-fit cursor-help flex-wrap items-center gap-x-1 rounded-sm text-sm leading-5 outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring",
							TONE_CLASS[presentation.tone],
							className,
						)}
					/>
				}
			>
				<presentation.Icon className="size-4 shrink-0" aria-hidden />
				<span>{presentation.label}</span>
			</TooltipTrigger>
			<TooltipContent className="max-w-80 space-y-1">
				<p>{formatTrendProvenance(support)}</p>
				<p>This describes recent evidence, not your overall ability.</p>
			</TooltipContent>
		</Tooltip>
	);
}
