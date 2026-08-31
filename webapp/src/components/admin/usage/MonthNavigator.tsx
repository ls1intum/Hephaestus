import { ChevronLeft, ChevronRight } from "lucide-react";
import { type ComponentProps, type ReactElement, useEffect, useRef, useState } from "react";

import { Button, buttonVariants } from "@/components/ui/button";

import { addMonths, formatMonthLabel } from "./usage-utils";

export interface MonthNavigatorProps {
	month: string;
	canGoNext: boolean;
	renderMonthLink: (month: string, props: ComponentProps<"a">) => ReactElement;
}

export function MonthNavigator({ month, canGoNext, renderMonthLink }: MonthNavigatorProps) {
	const label = formatMonthLabel(month);
	const nextMonth = addMonths(month, 1);
	const prevRef = useRef<HTMLAnchorElement>(null);

	// Stepping onto the newest month unmounts the clicked link — it becomes the disabled button — so
	// focus would otherwise fall to the document.
	const [focusPreviousOnMonth, setFocusPreviousOnMonth] = useState<string | undefined>(undefined);
	useEffect(() => {
		if (month === focusPreviousOnMonth && !canGoNext) {
			prevRef.current?.focus();
		}
	}, [month, canGoNext, focusPreviousOnMonth]);

	return (
		<div className="flex items-center gap-1">
			{renderMonthLink(addMonths(month, -1), {
				ref: prevRef,
				"aria-label": "Previous month",
				className: buttonVariants({ variant: "outline", size: "icon-sm" }),
				children: <ChevronLeft />,
			})}
			<span className="w-32 text-center text-sm font-medium tabular-nums">{label}</span>
			{canGoNext ? (
				renderMonthLink(nextMonth, {
					"aria-label": "Next month",
					className: buttonVariants({ variant: "outline", size: "icon-sm" }),
					children: <ChevronRight />,
					onClick: () => {
						setFocusPreviousOnMonth(nextMonth);
					},
				})
			) : (
				<Button variant="outline" size="icon-sm" aria-label="Next month" disabled>
					<ChevronRight />
				</Button>
			)}
		</div>
	);
}
