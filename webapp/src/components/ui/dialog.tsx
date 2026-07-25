"use client";

import { Dialog as DialogPrimitive } from "@base-ui/react/dialog";
import { XIcon } from "lucide-react";
import type * as React from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

function Dialog({ ...props }: DialogPrimitive.Root.Props) {
	return <DialogPrimitive.Root data-slot="dialog" {...props} />;
}

function DialogTrigger({ ...props }: DialogPrimitive.Trigger.Props) {
	return <DialogPrimitive.Trigger data-slot="dialog-trigger" {...props} />;
}

function DialogPortal({ ...props }: DialogPrimitive.Portal.Props) {
	return <DialogPrimitive.Portal data-slot="dialog-portal" {...props} />;
}

function DialogClose({ ...props }: DialogPrimitive.Close.Props) {
	return <DialogPrimitive.Close data-slot="dialog-close" {...props} />;
}

function DialogOverlay({ className, ...props }: DialogPrimitive.Backdrop.Props) {
	return (
		<DialogPrimitive.Backdrop
			data-slot="dialog-overlay"
			className={cn(
				"data-open:animate-in data-closed:animate-out data-closed:fade-out-0 data-open:fade-in-0 bg-black/10 duration-100 supports-backdrop-filter:backdrop-blur-xs fixed inset-0 isolate z-50",
				className,
			)}
			{...props}
		/>
	);
}

function DialogContent({
	className,
	children,
	showCloseButton = true,
	...props
}: DialogPrimitive.Popup.Props & {
	showCloseButton?: boolean;
}) {
	return (
		<DialogPortal>
			<DialogOverlay />
			<DialogPrimitive.Popup
				data-slot="dialog-content"
				className={cn(
					"bg-background data-open:animate-in data-closed:animate-out data-closed:fade-out-0 data-open:fade-in-0 data-closed:zoom-out-95 data-open:zoom-in-95 ring-foreground/10 grid max-w-[calc(100%-2rem)] gap-4 rounded-xl p-4 text-sm ring-1 duration-100 sm:max-w-sm fixed top-1/2 left-1/2 z-50 w-full -translate-x-1/2 -translate-y-1/2 outline-none",
					// The popup is `fixed` and centred on the viewport, so anything taller than the
					// viewport used to hang off *both* edges with no way to reach it — the page cannot
					// scroll a fixed element back into view, which clipped the title and the submit
					// button on short/mobile viewports. Bound it and let it scroll (WCAG 2.2 SC 1.4.10:
					// content must reflow to 320 CSS px without two-dimensional scrolling).
					// `svh` (smallest viewport height) rather than `dvh`: mobile browser chrome expands
					// and collapses while scrolling, and `dvh` would resize the dialog underneath the
					// user's finger. `svh` is the stable "always fits" height.
					"max-h-[calc(100svh-2rem)] overflow-y-auto overscroll-contain",
					// When the caller opts into `DialogBody`, the body owns the scroll instead and the
					// popup becomes a bounded column: header and footer stay put, the close button stays
					// pinned, and only the middle moves. Same shape shadcn uses for its own tall dialogs.
					// (`has-*` wins on specificity, so it overrides the `grid`/`overflow-y-auto` above.)
					"has-data-[slot=dialog-body]:flex has-data-[slot=dialog-body]:flex-col has-data-[slot=dialog-body]:overflow-hidden",
					className,
				)}
				{...props}
			>
				{children}
				{showCloseButton && (
					<DialogPrimitive.Close
						data-slot="dialog-close"
						render={<Button variant="ghost" className="absolute top-2 right-2" size="icon-sm" />}
					>
						<XIcon />
						<span className="sr-only">Close</span>
					</DialogPrimitive.Close>
				)}
			</DialogPrimitive.Popup>
		</DialogPortal>
	);
}

function DialogHeader({ className, ...props }: React.ComponentProps<"div">) {
	return (
		// `shrink-0`: `DialogBody` is `flex-1` off a zero basis, so it contributes nothing to shrink
		// distribution — without this the header would be the part that gets squashed.
		<div
			data-slot="dialog-header"
			className={cn("gap-2 flex shrink-0 flex-col", className)}
			{...props}
		/>
	);
}

/**
 * The scrollable middle of a tall dialog: everything between {@link DialogHeader} and
 * {@link DialogFooter} that may outgrow the viewport.
 *
 * Presence of this slot is what switches {@link DialogContent} from "the whole popup scrolls" to
 * "only the body scrolls", so the header, the footer, and the close button stay reachable at every
 * scroll position. The negative inline margin lets content sit flush against the scrollbar while
 * keeping the popup's own padding — the same bleed trick {@link DialogFooter} uses.
 *
 * `min-h-0` is load-bearing: a flex item's automatic minimum size is its content, so without it the
 * body refuses to shrink and the popup overflows again.
 */
function DialogBody({ className, ...props }: React.ComponentProps<"div">) {
	return (
		<div
			data-slot="dialog-body"
			className={cn("-mx-4 min-h-0 flex-1 overflow-y-auto overscroll-contain px-4", className)}
			{...props}
		/>
	);
}

function DialogFooter({
	className,
	showCloseButton = false,
	children,
	...props
}: React.ComponentProps<"div"> & {
	showCloseButton?: boolean;
}) {
	return (
		<div
			data-slot="dialog-footer"
			className={cn(
				// `shrink-0` for the same reason as `DialogHeader`: the actions are the one thing that
				// must never be squeezed out of reach when the dialog is height-bound.
				"bg-muted/50 -mx-4 -mb-4 rounded-b-xl border-t p-4 flex shrink-0 flex-col-reverse gap-2 sm:flex-row sm:justify-end",
				className,
			)}
			{...props}
		>
			{children}
			{showCloseButton && (
				<DialogPrimitive.Close render={<Button variant="outline" />}>Close</DialogPrimitive.Close>
			)}
		</div>
	);
}

function DialogTitle({ className, ...props }: DialogPrimitive.Title.Props) {
	return (
		<DialogPrimitive.Title
			data-slot="dialog-title"
			className={cn("text-base leading-none font-medium", className)}
			{...props}
		/>
	);
}

function DialogDescription({ className, ...props }: DialogPrimitive.Description.Props) {
	return (
		<DialogPrimitive.Description
			data-slot="dialog-description"
			className={cn(
				"text-muted-foreground *:[a]:hover:text-foreground text-sm *:[a]:underline *:[a]:underline-offset-3",
				className,
			)}
			{...props}
		/>
	);
}

export {
	Dialog,
	DialogBody,
	DialogClose,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogOverlay,
	DialogPortal,
	DialogTitle,
	DialogTrigger,
};
