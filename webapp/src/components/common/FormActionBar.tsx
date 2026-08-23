import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export interface FormActionBarProps {
	secondary?: ReactNode;
	children: ReactNode;
	className?: string;
}

/**
 * `sticky`, not `fixed`: it stays inside the form's own column, so it lines up with the fields
 * rather than spanning a page wider than them.
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
