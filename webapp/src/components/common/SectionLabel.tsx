import type { ReactNode } from "react";

import { cn } from "cn";

export interface SectionLabelProps {
	id?: string;
	/** A heading when the label opens a section; a span when it captions a block inside one. */
	as?: "h2" | "h3" | "h4" | "span";
	children: ReactNode;
	className?: string;
}

/**
 * The small muted label over a block: "Current feedback", "Why it
 * matters". A section with a title and a sentence under it is `layout/Section` at `size="lg"`.
 */
export function SectionLabel({ id, as: Tag = "span", children, className }: SectionLabelProps) {
	return (
		<Tag
			data-slot="section-label"
			id={id}
			className={cn("text-sm font-semibold text-muted-foreground", className)}
		>
			{children}
		</Tag>
	);
}
