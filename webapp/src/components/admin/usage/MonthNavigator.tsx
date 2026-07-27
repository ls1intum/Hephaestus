import { ChevronLeft, ChevronRight } from "lucide-react";
import { useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import { formatMonthLabel } from "./usage-utils";

export interface MonthNavigatorProps {
	/** ISO `yyyy-MM` month currently shown. */
	month: string;
	/** Whether stepping forward is allowed (false on the current month — no future months). */
	canGoNext: boolean;
	onPrevMonth: () => void;
	onNextMonth: () => void;
}

/** Prev/next month stepper shared by the workspace and instance AI usage pages. */
export function MonthNavigator({
	month,
	canGoNext,
	onPrevMonth,
	onNextMonth,
}: MonthNavigatorProps) {
	const label = formatMonthLabel(month);
	const prevRef = useRef<HTMLButtonElement>(null);
	// Stepping forward onto the current month disables the very control that was just activated, and
	// focus then falls to `<body>` with no visible indicator — the reader has to tab from the top of
	// the page to get back (WCAG SC 2.4.3, and SC 2.4.7 for the lost indicator). Hand it to the arrow
	// that can still act. Only after a press: `canGoNext` is already false on first paint.
	const steppedForward = useRef(false);
	useEffect(() => {
		if (steppedForward.current && !canGoNext) {
			prevRef.current?.focus();
		}
		steppedForward.current = false;
	}, [canGoNext]);
	return (
		<div className="flex items-center gap-1">
			<Button
				ref={prevRef}
				variant="outline"
				size="icon-sm"
				aria-label="Previous month"
				onClick={onPrevMonth}
			>
				<ChevronLeft />
			</Button>
			{/* Deliberately not a live region. Stepping a month is the reader's own action and reloads
			    the whole report — both cap meters, every row, the rate disclosure — so announcing the
			    month alone described the smallest thing that changed and implied the rest had not
			    (SC 4.1.3). A status message is for something the reader did not ask for. */}
			<span className="w-32 text-center text-sm font-medium tabular-nums">{label}</span>
			<Button
				variant="outline"
				size="icon-sm"
				aria-label="Next month"
				disabled={!canGoNext}
				onClick={() => {
					steppedForward.current = true;
					onNextMonth();
				}}
			>
				<ChevronRight />
			</Button>
		</div>
	);
}
