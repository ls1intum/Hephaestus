import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatMonthLabel } from "./usageUtils";

export interface MonthNavigatorProps {
	/** ISO `yyyy-MM` month currently shown. */
	month: string;
	/** Whether stepping forward is allowed (false on the current month — no future months). */
	canGoNext: boolean;
	onPrevMonth: () => void;
	onNextMonth: () => void;
}

/** Prev/next month stepper shared by the workspace and instance LLM usage pages. */
export function MonthNavigator({
	month,
	canGoNext,
	onPrevMonth,
	onNextMonth,
}: MonthNavigatorProps) {
	return (
		<div className="flex items-center gap-1">
			<Button variant="outline" size="icon-sm" aria-label="Previous month" onClick={onPrevMonth}>
				<ChevronLeft />
			</Button>
			<span className="w-32 text-center text-sm font-medium tabular-nums" aria-live="polite">
				{formatMonthLabel(month)}
			</span>
			<Button
				variant="outline"
				size="icon-sm"
				aria-label="Next month"
				disabled={!canGoNext}
				onClick={onNextMonth}
			>
				<ChevronRight />
			</Button>
		</div>
	);
}
