"use client";

import { Combobox as ComboboxPrimitive } from "@base-ui/react/combobox";
import { CheckIcon, SearchIcon } from "lucide-react";
import type * as React from "react";
import { InputGroup, InputGroupAddon } from "@/components/ui/input-group";
import { cn } from "@/lib/utils";

const Combobox = ComboboxPrimitive.Root;
const ComboboxCollection = ComboboxPrimitive.Collection;
const ComboboxPortal = ComboboxPrimitive.Portal;

/**
 * `Intl.Collator`-backed matcher for the root's `filter` prop, so accented and cased text match the
 * way the reader expects rather than the way `toLowerCase()` does.
 */
const useComboboxFilter = ComboboxPrimitive.useFilter;

function ComboboxValue({ ...props }: ComboboxPrimitive.Value.Props) {
	return <ComboboxPrimitive.Value {...props} />;
}

function ComboboxIcon({ className, ...props }: ComboboxPrimitive.Icon.Props) {
	return (
		<ComboboxPrimitive.Icon
			data-slot="combobox-icon"
			className={cn("text-muted-foreground pointer-events-none shrink-0", className)}
			{...props}
		/>
	);
}

/**
 * The button that opens the popup. Mirrors `SelectTrigger` so a combobox and a select sitting in the
 * same form are indistinguishable until opened.
 */
function ComboboxTrigger({
	className,
	size = "default",
	children,
	...props
}: ComboboxPrimitive.Trigger.Props & {
	size?: "sm" | "default";
}) {
	return (
		<ComboboxPrimitive.Trigger
			data-slot="combobox-trigger"
			data-size={size}
			className={cn(
				"border-input data-[placeholder]:text-muted-foreground dark:bg-input/30 dark:hover:bg-input/50 focus-visible:border-ring focus-visible:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive dark:aria-invalid:border-destructive/50 gap-1.5 rounded-lg border bg-transparent py-2 pr-2 pl-2.5 text-sm transition-colors select-none focus-visible:ring-[3px] aria-invalid:ring-[3px] data-[size=default]:h-8 data-[size=sm]:h-7 data-[size=sm]:rounded-[min(var(--radius-md),10px)] *:data-[slot=combobox-value]:flex *:data-[slot=combobox-value]:gap-1.5 [&_svg:not([class*='size-'])]:size-4 flex w-fit items-center justify-between whitespace-nowrap outline-none disabled:cursor-not-allowed disabled:opacity-50 *:data-[slot=combobox-value]:line-clamp-1 *:data-[slot=combobox-value]:flex *:data-[slot=combobox-value]:items-center [&_svg]:pointer-events-none [&_svg]:shrink-0",
				className,
			)}
			{...props}
		>
			{children}
		</ComboboxPrimitive.Trigger>
	);
}

/** Portalled, anchored popup. Mirrors `SelectContent`. */
function ComboboxContent({
	className,
	children,
	side = "bottom",
	sideOffset = 4,
	align = "center",
	alignOffset = 0,
	...props
}: ComboboxPrimitive.Popup.Props &
	Pick<ComboboxPrimitive.Positioner.Props, "align" | "alignOffset" | "side" | "sideOffset">) {
	return (
		<ComboboxPrimitive.Portal>
			<ComboboxPrimitive.Positioner
				side={side}
				sideOffset={sideOffset}
				align={align}
				alignOffset={alignOffset}
				className="isolate z-50"
			>
				<ComboboxPrimitive.Popup
					data-slot="combobox-content"
					className={cn(
						"bg-popover text-popover-foreground data-open:animate-in data-closed:animate-out data-closed:fade-out-0 data-open:fade-in-0 data-closed:zoom-out-95 data-open:zoom-in-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 ring-foreground/10 min-w-36 rounded-lg p-1 shadow-md ring-1 duration-100 data-[side=inline-start]:slide-in-from-right-2 data-[side=inline-end]:slide-in-from-left-2 relative isolate z-50 flex max-h-(--available-height) w-(--anchor-width) origin-(--transform-origin) flex-col overflow-hidden",
						className,
					)}
					{...props}
				>
					{children}
				</ComboboxPrimitive.Popup>
			</ComboboxPrimitive.Positioner>
		</ComboboxPrimitive.Portal>
	);
}

/** The bare text input. Use `ComboboxSearchInput` for the magnifier-prefixed filter field. */
function ComboboxInput({ className, ...props }: ComboboxPrimitive.Input.Props) {
	return (
		<ComboboxPrimitive.Input
			data-slot="combobox-input"
			className={cn(
				"w-full text-sm outline-hidden disabled:cursor-not-allowed disabled:opacity-50",
				className,
			)}
			{...props}
		/>
	);
}

/**
 * The filter field: the same input wrapped in an `InputGroup` with a magnifier, for use inside a
 * popup or dialog where the input's only job is to narrow the list.
 */
function ComboboxSearchInput({
	className,
	containerClassName,
	...props
}: ComboboxPrimitive.Input.Props & { containerClassName?: string }) {
	return (
		<div data-slot="combobox-search-input-wrapper" className={cn("p-1 pb-0", containerClassName)}>
			<InputGroup className="bg-input/30 border-input/30 h-8! rounded-lg! shadow-none! *:data-[slot=input-group-addon]:pl-2!">
				<ComboboxInput className={className} {...props} />
				<InputGroupAddon>
					<SearchIcon className="size-4 shrink-0 opacity-50" />
				</InputGroupAddon>
			</InputGroup>
		</div>
	);
}

function ComboboxList({ className, ...props }: ComboboxPrimitive.List.Props) {
	return (
		<ComboboxPrimitive.List
			data-slot="combobox-list"
			className={cn(
				"no-scrollbar max-h-72 scroll-py-1 p-1 outline-none overflow-x-hidden overflow-y-auto",
				className,
			)}
			{...props}
		/>
	);
}

/** Rendered only when the filtered list is empty. Requires `items` on the root. */
function ComboboxEmpty({ className, ...props }: ComboboxPrimitive.Empty.Props) {
	return (
		<ComboboxPrimitive.Empty
			data-slot="combobox-empty"
			className={cn("text-muted-foreground py-6 text-center text-sm empty:hidden", className)}
			{...props}
		/>
	);
}

/** A polite live region for out-of-band status (e.g. "Loading…"). Keep it mounted. */
function ComboboxStatus({ className, ...props }: ComboboxPrimitive.Status.Props) {
	return (
		<ComboboxPrimitive.Status
			data-slot="combobox-status"
			className={cn("text-muted-foreground py-6 text-center text-sm empty:hidden", className)}
			{...props}
		/>
	);
}

function ComboboxGroup({ className, ...props }: ComboboxPrimitive.Group.Props) {
	return (
		<ComboboxPrimitive.Group
			data-slot="combobox-group"
			className={cn("scroll-my-1", className)}
			{...props}
		/>
	);
}

function ComboboxLabel({ className, ...props }: ComboboxPrimitive.GroupLabel.Props) {
	return (
		<ComboboxPrimitive.GroupLabel
			data-slot="combobox-label"
			className={cn("text-muted-foreground px-1.5 py-1 text-xs", className)}
			{...props}
		/>
	);
}

/**
 * An option. Base UI drives the roving highlight through `data-highlighted`, which it omits entirely
 * when the item is not highlighted — so the presence-form variant below marks exactly one item.
 */
function ComboboxItem({ className, children, ...props }: ComboboxPrimitive.Item.Props) {
	return (
		<ComboboxPrimitive.Item
			data-slot="combobox-item"
			className={cn(
				"data-highlighted:bg-accent data-highlighted:text-accent-foreground data-highlighted:**:text-accent-foreground gap-1.5 rounded-md py-1 pr-8 pl-1.5 text-sm [&_svg:not([class*='size-'])]:size-4 relative flex w-full cursor-default items-center outline-hidden select-none data-[disabled]:pointer-events-none data-[disabled]:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0",
				className,
			)}
			{...props}
		>
			{children}
		</ComboboxPrimitive.Item>
	);
}

/** The tick shown on the selected item. Renders nothing unless the item is selected. */
function ComboboxItemIndicator({ className, ...props }: ComboboxPrimitive.ItemIndicator.Props) {
	return (
		<ComboboxPrimitive.ItemIndicator
			data-slot="combobox-item-indicator"
			render={
				<span
					className={cn(
						"pointer-events-none absolute right-2 flex size-4 items-center justify-center",
						className,
					)}
				/>
			}
			{...props}
		>
			<CheckIcon className="pointer-events-none" />
		</ComboboxPrimitive.ItemIndicator>
	);
}

function ComboboxSeparator({ className, ...props }: React.ComponentProps<"div">) {
	return (
		<div
			data-slot="combobox-separator"
			role="separator"
			className={cn("bg-border -mx-1 my-1 h-px pointer-events-none", className)}
			{...props}
		/>
	);
}

export {
	Combobox,
	ComboboxCollection,
	ComboboxContent,
	ComboboxEmpty,
	ComboboxGroup,
	ComboboxIcon,
	ComboboxInput,
	ComboboxItem,
	ComboboxItemIndicator,
	ComboboxLabel,
	ComboboxList,
	ComboboxPortal,
	ComboboxSearchInput,
	ComboboxSeparator,
	ComboboxStatus,
	ComboboxTrigger,
	ComboboxValue,
	useComboboxFilter,
};
