import type { ComponentProps } from "react";

import { cn } from "cn";

/**
 * The sections of a record sheet in the detail drawer, below its header, with one thin line between
 * each two and the same room above and below every line. The line belongs to the stack, not to a
 * section, so none sits above the first child or inside a card, and a section that renders nothing
 * leaves none behind.
 */
export function SectionStack({ className, ...props }: ComponentProps<"div">) {
	return (
		<div
			data-slot="section-stack"
			className={cn(
				"flex flex-col gap-8 [&>*+*]:border-t [&>*+*]:border-border [&>*+*]:pt-8",
				className,
			)}
			{...props}
		/>
	);
}
