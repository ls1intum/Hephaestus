import { ChevronLeft, ChevronRight } from "lucide-react";
import type { ComponentProps, ReactElement } from "react";
import { useEffect, useRef } from "react";
import { Button, buttonVariants } from "@/components/ui/button";
import { addMonths, formatMonthLabel } from "./usage-utils";

export interface MonthNavigatorProps {
	month: string;
	canGoNext: boolean;
	renderMonthLink: (month: string, props: ComponentProps<"a">) => ReactElement;
}

export function MonthNavigator({ month, canGoNext, renderMonthLink }: MonthNavigatorProps) {
	const label = formatMonthLabel(month);
	const prevRef = useRef<HTMLAnchorElement>(null);
	const steppedForward = useRef(false);
	useEffect(() => {
		if (steppedForward.current && !canGoNext) {
			prevRef.current?.focus();
		}
		steppedForward.current = false;
	}, [canGoNext]);
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
				renderMonthLink(addMonths(month, 1), {
					"aria-label": "Next month",
					className: buttonVariants({ variant: "outline", size: "icon-sm" }),
					children: <ChevronRight />,
					onClick: () => {
						steppedForward.current = true;
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
