import { ScrollArea as ScrollAreaPrimitive } from "@base-ui/react/scroll-area";
import type { Ref } from "react";

import { cn } from "@/lib/utils";

function ScrollArea({
	className,
	viewportClassName,
	children,
	viewportRef,
	...props
}: ScrollAreaPrimitive.Root.Props & {
	/** Ref to the scrolling Viewport (the element that actually overflows) — needed for scroll-position
	 * logic like auto-scroll/scroll-to-bottom. A ref on Root would never observe scroll events. */
	viewportRef?: Ref<HTMLDivElement>;
	/**
	 * Classes for the Viewport — the element that actually overflows. This is where a bounding height
	 * belongs: the Viewport defaults to `size-full` (height: 100%), which only resolves against an
	 * ancestor with a *definite* height, so a `max-h-*` on the Root (or a `max-h` flex column around it)
	 * never clips and the content renders full-height. A `max-h-*` here caps the Viewport's own box, so
	 * it grows to content and then scrolls — no fixed-height ancestor required.
	 */
	viewportClassName?: string;
}) {
	return (
		<ScrollAreaPrimitive.Root
			data-slot="scroll-area"
			className={cn("relative", className)}
			{...props}
		>
			<ScrollAreaPrimitive.Viewport
				ref={viewportRef}
				data-slot="scroll-area-viewport"
				className={cn(
					"focus-visible:ring-ring/50 size-full rounded-[inherit] transition-[color,box-shadow] outline-none focus-visible:ring-[3px] focus-visible:outline-1",
					viewportClassName,
				)}
			>
				{children}
			</ScrollAreaPrimitive.Viewport>
			<ScrollBar />
			<ScrollAreaPrimitive.Corner />
		</ScrollAreaPrimitive.Root>
	);
}

function ScrollBar({
	className,
	orientation = "vertical",
	...props
}: ScrollAreaPrimitive.Scrollbar.Props) {
	return (
		<ScrollAreaPrimitive.Scrollbar
			data-slot="scroll-area-scrollbar"
			data-orientation={orientation}
			orientation={orientation}
			className={cn(
				"data-horizontal:h-2.5 data-horizontal:flex-col data-horizontal:border-t data-horizontal:border-t-transparent data-vertical:h-full data-vertical:w-2.5 data-vertical:border-l data-vertical:border-l-transparent flex touch-none p-px transition-colors select-none",
				className,
			)}
			{...props}
		>
			<ScrollAreaPrimitive.Thumb
				data-slot="scroll-area-thumb"
				className="rounded-full bg-border relative flex-1"
			/>
		</ScrollAreaPrimitive.Scrollbar>
	);
}

export { ScrollArea, ScrollBar };
