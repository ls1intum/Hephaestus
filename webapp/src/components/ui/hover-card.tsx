import { PreviewCard as PreviewCardPrimitive } from "@base-ui/react/preview-card";

import { cn } from "@/lib/utils";

function HoverCard({ ...props }: PreviewCardPrimitive.Root.Props) {
	return <PreviewCardPrimitive.Root data-slot="hover-card" {...props} />;
}

function HoverCardTrigger({ ...props }: PreviewCardPrimitive.Trigger.Props) {
	return <PreviewCardPrimitive.Trigger data-slot="hover-card-trigger" {...props} />;
}

function HoverCardContent({
	className,
	side = "bottom",
	sideOffset = 4,
	align = "center",
	alignOffset = 4,
	...props
}: PreviewCardPrimitive.Popup.Props &
	Pick<PreviewCardPrimitive.Positioner.Props, "align" | "alignOffset" | "side" | "sideOffset">) {
	return (
		<PreviewCardPrimitive.Portal data-slot="hover-card-portal">
			{/*
			 * `positionMethod="fixed"`, not Base UI's default `absolute`. An absolutely positioned
			 * positioner is laid out in the document at `left: 0` and then moved by a transform, so its
			 * own box is as wide as its containing block and the transform pushes the *document* that much
			 * wider: an open card measured 320px viewport, 320px card, and a `documentElement.scrollWidth`
			 * of 325 — the whole page could be dragged sideways while the card was open (WCAG 2.2 SC
			 * 1.4.10). A fixed positioner is out of flow relative to the viewport and adds nothing to the
			 * document's scroll width. The popup is portalled to `body`, so there is no transformed
			 * ancestor for `fixed` to be captured by.
			 */}
			<PreviewCardPrimitive.Positioner
				align={align}
				alignOffset={alignOffset}
				positionMethod="fixed"
				side={side}
				sideOffset={sideOffset}
				className="isolate z-50"
			>
				<PreviewCardPrimitive.Popup
					data-slot="hover-card-content"
					className={cn(
						"data-open:animate-in data-closed:animate-out data-closed:fade-out-0 data-open:fade-in-0 data-closed:zoom-out-95 data-open:zoom-in-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 ring-foreground/10 bg-popover text-popover-foreground w-64 rounded-lg p-2.5 text-sm shadow-md ring-1 duration-100 data-[side=inline-start]:slide-in-from-right-2 data-[side=inline-end]:slide-in-from-left-2 z-50 origin-(--transform-origin) outline-hidden",
						className,
					)}
					{...props}
				/>
			</PreviewCardPrimitive.Positioner>
		</PreviewCardPrimitive.Portal>
	);
}

export { HoverCard, HoverCardContent, HoverCardTrigger };
