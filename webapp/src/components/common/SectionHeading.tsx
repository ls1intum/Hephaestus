import type { ReactNode } from "react";

export interface SectionHeadingProps {
	children: ReactNode;
}

export function SectionHeading({ children }: SectionHeadingProps) {
	return <h3 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">{children}</h3>;
}
