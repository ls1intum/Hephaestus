import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export interface MetaRowProps {
	/** Caption-weight facts, joined by separators. */
	captions?: ReactNode[];
	/** Chips reporting a state. Rendered after the captions, at their own rhythm. */
	badges?: ReactNode;
	className?: string;
}

/**
 * The line of facts under a row's title.
 *
 * Captions and badges are two different kinds of thing and were rendered as one flat run at one gap,
 * so "Pull or merge request", a chip, and "Follows the workspace default" arrived as a single
 * run-on sentence. Separators between the captions and a wider gap before the chips say which is
 * which without adding a word.
 */
export function MetaRow({ captions = [], badges, className }: MetaRowProps) {
	const shown = captions.filter(Boolean);
	return (
		<span className={cn("flex flex-wrap items-center gap-x-3 gap-y-1.5", className)}>
			{shown.length > 0 && (
				<span className="flex flex-wrap items-center gap-x-2 gap-y-1.5">
					{shown.map((caption, index) => (
						// biome-ignore lint/suspicious/noArrayIndexKey: captions are positional, not identified.
						<span key={index} className="flex items-center gap-x-2">
							{index > 0 && (
								<span aria-hidden className="text-muted-foreground/50">
									·
								</span>
							)}
							{caption}
						</span>
					))}
				</span>
			)}
			{badges && <span className="flex flex-wrap items-center gap-1.5">{badges}</span>}
		</span>
	);
}
