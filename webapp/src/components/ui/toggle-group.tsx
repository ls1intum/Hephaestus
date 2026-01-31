"use client";

import { Toggle as TogglePrimitive } from "@base-ui/react/toggle";
import { ToggleGroup as ToggleGroupPrimitive } from "@base-ui/react/toggle-group";
import type { VariantProps } from "class-variance-authority";
import * as React from "react";
import { toggleVariants } from "@/components/ui/toggle";
import { cn } from "@/lib/utils";

const ToggleGroupContext = React.createContext<
	VariantProps<typeof toggleVariants> & {
		spacing?: number;
	}
>({
	size: "default",
	variant: "default",
	spacing: 0,
});

/**
 * ToggleGroup component - wraps Base UI ToggleGroup with simplified string value API.
 *
 * For single selection (multiple={false}, the default):
 * - `value` can be a string (the selected value) or undefined
 * - `onValueChange` receives the new selected value as a string, or undefined if deselected
 *
 * For multiple selection (multiple={true}):
 * - `value` should be an array of selected values
 * - `onValueChange` receives an array of all selected values
 */
function ToggleGroup({
	className,
	variant,
	size,
	spacing = 0,
	children,
	value,
	onValueChange,
	multiple = false,
	...props
}: Omit<React.ComponentProps<typeof ToggleGroupPrimitive>, "value" | "onValueChange"> &
	VariantProps<typeof toggleVariants> & {
		spacing?: number;
		// Allow either string (for single) or array (for multiple)
		value?: string | readonly string[];
		// Callback receives string for single, array for multiple
		onValueChange?: (value: string | string[]) => void;
		multiple?: boolean;
	}) {
	// Convert string value to array for Base UI
	const baseValue = React.useMemo(() => {
		if (value === undefined) return [];
		if (Array.isArray(value)) return value;
		return [value];
	}, [value]);

	// Handle value change from Base UI (array) and convert back to our API
	const handleValueChange = React.useCallback(
		(groupValue: unknown[]) => {
			if (!onValueChange) return;
			if (multiple) {
				// For multiple selection, pass the array as-is
				onValueChange(groupValue as string[]);
			} else {
				// For single selection, pass the first value or empty string
				onValueChange((groupValue[0] as string) || "");
			}
		},
		[onValueChange, multiple],
	);

	return (
		<ToggleGroupPrimitive
			data-slot="toggle-group"
			data-variant={variant}
			data-size={size}
			data-spacing={spacing}
			style={{ "--gap": spacing } as React.CSSProperties}
			className={cn(
				"group/toggle-group flex w-fit items-center gap-[--spacing(var(--gap))] rounded-md data-[spacing=default]:data-[variant=outline]:shadow-xs",
				className,
			)}
			value={baseValue}
			onValueChange={handleValueChange}
			multiple={multiple}
			{...props}
		>
			<ToggleGroupContext.Provider value={{ variant, size, spacing }}>
				{children}
			</ToggleGroupContext.Provider>
		</ToggleGroupPrimitive>
	);
}

function ToggleGroupItem({
	className,
	children,
	variant,
	size,
	...props
}: React.ComponentProps<typeof TogglePrimitive> & VariantProps<typeof toggleVariants>) {
	const context = React.useContext(ToggleGroupContext);

	return (
		<TogglePrimitive
			data-slot="toggle-group-item"
			data-variant={context.variant || variant}
			data-size={context.size || size}
			data-spacing={context.spacing}
			className={cn(
				toggleVariants({
					variant: context.variant || variant,
					size: context.size || size,
				}),
				"w-auto min-w-0 shrink-0 px-3 focus:z-10 focus-visible:z-10",
				"data-[spacing=0]:rounded-none data-[spacing=0]:shadow-none data-[spacing=0]:first:rounded-l-md data-[spacing=0]:last:rounded-r-md data-[spacing=0]:data-[variant=outline]:border-l-0 data-[spacing=0]:data-[variant=outline]:first:border-l",
				className,
			)}
			{...props}
		>
			{children}
		</TogglePrimitive>
	);
}

export { ToggleGroup, ToggleGroupItem };
