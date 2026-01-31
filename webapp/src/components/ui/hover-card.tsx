import { PreviewCard as HoverCardPrimitive } from "@base-ui/react/preview-card";
import type * as React from "react";

import { cn } from "@/lib/utils";

function HoverCard({ ...props }: React.ComponentProps<typeof HoverCardPrimitive.Root>) {
	return <HoverCardPrimitive.Root data-slot="hover-card" {...props} />;
}

function HoverCardTrigger({ ...props }: React.ComponentProps<typeof HoverCardPrimitive.Trigger>) {
	return <HoverCardPrimitive.Trigger data-slot="hover-card-trigger" {...props} />;
}

function HoverCardContent({
	className,
	align = "center",
	alignOffset = 4,
	side = "bottom",
	sideOffset = 4,
	children,
	...props
}: React.ComponentProps<typeof HoverCardPrimitive.Popup> & {
	align?: "start" | "center" | "end";
	alignOffset?: number;
	side?: "top" | "right" | "bottom" | "left";
	sideOffset?: number;
}) {
	return (
		<HoverCardPrimitive.Portal data-slot="hover-card-portal">
			<HoverCardPrimitive.Positioner
				align={align}
				alignOffset={alignOffset}
				side={side}
				sideOffset={sideOffset}
				className="isolate z-50"
			>
				<HoverCardPrimitive.Popup
					data-slot="hover-card-content"
					className={cn(
						"bg-popover text-popover-foreground data-[open]:animate-in data-[closed]:animate-out data-[closed]:fade-out-0 data-[open]:fade-in-0 data-[closed]:zoom-out-95 data-[open]:zoom-in-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 z-50 w-64 origin-(--transform-origin) rounded-md border p-4 shadow-md outline-hidden",
						className,
					)}
					{...props}
				>
					{children}
				</HoverCardPrimitive.Popup>
			</HoverCardPrimitive.Positioner>
		</HoverCardPrimitive.Portal>
	);
}

export { HoverCard, HoverCardTrigger, HoverCardContent };
