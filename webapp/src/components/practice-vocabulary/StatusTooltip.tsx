import { type ReactElement, type ReactNode, useId } from "react";

import { cn } from "cn";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

import { FOCUS_RING, HIT_AREA_24 } from "@/components/common/focus";
import { type StatusDef, statusToneClass } from "@/components/common/status-def";
import { TipPopover } from "@/components/common/TipPopover";

export interface StatusTooltipProps {
	/** The registry entry the trigger stands for; the tooltip says its words and its sentence. */
	def: Pick<StatusDef, "label" | "description">;
	/**
	 * The trigger element. A `<button>` when the icon stands alone, so a keyboard reaches the
	 * sentence; a `<span>` when it sits inside a control that is already a button, where the
	 * words are visible beside it and the sentence is the pointer's bonus.
	 */
	render: ReactElement;
	/** Classes for the trigger; with `openOnTap` the `render` element carries its own instead. */
	className?: string;
	/** What the trigger shows; left out when the rendered element draws itself, as a badge does. */
	children?: ReactNode;
	/**
	 * A tap opens and closes the sentence too: it is drawn as a popover ({@link TipPopover}), for a
	 * trigger a touch screen must explain. Left off where the trigger sits inside another control
	 * that a tap belongs to; there it stays a tooltip.
	 */
	openOnTap?: boolean;
	/** The panel says the sentence alone, where the trigger already shows the label. */
	bare?: boolean;
}

/**
 * The registry's label and one-line description over a status icon, so an icon that only its
 * colour and shape distinguish explains itself on hover or focus, and with `openOnTap` on a tap
 * too. A screen reader hears the sentence as the trigger's description: the popup is portalled.
 */
export function StatusTooltip({
	def,
	render,
	className,
	children,
	openOnTap = false,
	bare = false,
}: StatusTooltipProps) {
	const descriptionId = useId();
	// A span trigger sits inside another control; text beside it would join that control's name.
	const describe = render.type !== "span";
	const sentence = bare ? (
		def.description
	) : (
		<>
			<span className="font-semibold">{def.label}</span> · {def.description}
		</>
	);
	return (
		<>
			{openOnTap ? (
				<TipPopover
					label={def.label}
					trigger={render}
					aria-describedby={descriptionId}
					content={<p>{sentence}</p>}
				>
					{children}
				</TipPopover>
			) : (
				<Tooltip>
					<TooltipTrigger
						render={render}
						className={className}
						aria-describedby={describe ? descriptionId : undefined}
					>
						{children}
					</TooltipTrigger>
					<TooltipContent className="max-w-xs">{sentence}</TooltipContent>
				</Tooltip>
			)}
			{describe && (
				<span id={descriptionId} className="sr-only">
					{def.description}
				</span>
			)}
		</>
	);
}

export interface StatusIconProps {
	/** The registry entry the icon stands for: its glyph, in its tone, named by its label. */
	def: StatusDef;
	className?: string;
}

/**
 * A registry entry's icon standing alone as the tooltip's trigger: a button with no chrome of its
 * own, so a keyboard reaches the sentence, and named by the entry's label, since the glyph is all
 * that tells one value from another.
 */
export function StatusIcon({ def, className }: StatusIconProps) {
	const Icon = def.icon;
	return (
		<StatusTooltip
			// The glyph alone tells the values apart, so a touch screen must reach the sentence too.
			openOnTap
			def={def}
			render={
				<button
					type="button"
					aria-label={def.label}
					// The icon is 14 px, so the hit area is widened a step beyond the constant's.
					className={cn(
						HIT_AREA_24,
						"inline-flex cursor-help items-center before:-inset-1.5",
						FOCUS_RING,
						className,
					)}
				/>
			}
		>
			<Icon className={cn("size-3.5 shrink-0", statusToneClass(def.badgeVariant))} aria-hidden />
		</StatusTooltip>
	);
}

/**
 * A registry entry's icon as plain content, where the status is read and not explored: no focus
 * stop, so a row keeps one control. A screen reader hears the label; a pointer gets it as a native
 * title.
 */
export function StatusGlyph({ def, className }: StatusIconProps) {
	const Icon = def.icon;
	return (
		<span title={def.label} className={cn("inline-flex items-center", className)}>
			<Icon className={cn("size-3.5 shrink-0", statusToneClass(def.badgeVariant))} aria-hidden />
			<span className="sr-only">{def.label}</span>
		</span>
	);
}

/** A registry entry's icon and its words as plain text, for a status that is not an exception. */
export function StatusLabel({ def, className }: StatusIconProps) {
	const Icon = def.icon;
	return (
		<span className={cn("inline-flex min-w-0 items-center gap-1.5 text-sm", className)}>
			<Icon className={cn("size-3.5 shrink-0", statusToneClass(def.badgeVariant))} aria-hidden />
			<span className="min-w-0 break-words">{def.label}</span>
		</span>
	);
}
