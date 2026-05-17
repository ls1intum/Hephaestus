import type * as React from "react";
import * as ResizablePrimitive from "react-resizable-panels";

import { cn } from "@/lib/utils";

function ResizablePanelGroup({
	className,
	...props
}: React.ComponentProps<typeof ResizablePrimitive.Group>) {
	return (
		<ResizablePrimitive.Group
			data-slot="resizable-panel-group"
			className={cn(
				"flex h-full w-full data-[panel-group-orientation=vertical]:flex-col",
				className,
			)}
			{...props}
		/>
	);
}

function ResizablePanel({ ...props }: React.ComponentProps<typeof ResizablePrimitive.Panel>) {
	return <ResizablePrimitive.Panel data-slot="resizable-panel" {...props} />;
}

function ResizableHandle({
	withHandle,
	className,
	...props
}: React.ComponentProps<typeof ResizablePrimitive.Separator> & {
	withHandle?: boolean;
}) {
	return (
		<ResizablePrimitive.Separator
			data-slot="resizable-handle"
			className={cn(
				"bg-border focus-visible:ring-ring relative flex w-px items-center justify-center after:absolute after:inset-y-0 after:left-1/2 after:w-1 after:-translate-x-1/2 focus-visible:ring-1 focus-visible:ring-offset-1 focus-visible:outline-hidden data-[panel-group-orientation=vertical]:h-px data-[panel-group-orientation=vertical]:w-full data-[panel-group-orientation=vertical]:after:left-0 data-[panel-group-orientation=vertical]:after:h-1 data-[panel-group-orientation=vertical]:after:w-full data-[panel-group-orientation=vertical]:after:translate-x-0 data-[panel-group-orientation=vertical]:after:-translate-y-1/2 [&[data-panel-group-orientation=vertical]>div]:rotate-90",
				className,
			)}
			{...props}
		>
			{withHandle && <div className="bg-border h-6 w-1 rounded-lg z-10 flex shrink-0" />}
		</ResizablePrimitive.Separator>
	);
}

export { ResizableHandle, ResizablePanel, ResizablePanelGroup };
