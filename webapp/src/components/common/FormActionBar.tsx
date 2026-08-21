import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export interface FormActionBarProps {
	/** Usually a Cancel link. Sits opposite the primary action. */
	secondary?: ReactNode;
	children: ReactNode;
	className?: string;
}

/**
 * A form's actions, pinned to the bottom of the viewport while the form scrolls under them.
 *
 * The practice form carries over 800px of fixed-minimum field height before anything wraps, so a
 * submit button in normal flow is below the fold from the moment the page loads — the reader has to
 * scroll to the end of a form they have not filled in to find out how to submit it. Pinning it costs
 * one line of chrome and removes that entirely.
 *
 * `sticky`, not `fixed`: it stays inside the form's own column, so it lines up with the fields
 * instead of spanning a page whose content is narrower than its viewport.
 */
export function FormActionBar({ secondary, children, className }: FormActionBarProps) {
	return (
		<div
			className={cn(
				"sticky bottom-0 z-10 -mx-4 flex items-center justify-between gap-3 border-t bg-background/95 px-4 py-3 supports-backdrop-filter:bg-background/80 supports-backdrop-filter:backdrop-blur",
				className,
			)}
		>
			{secondary ?? <span />}
			{children}
		</div>
	);
}
