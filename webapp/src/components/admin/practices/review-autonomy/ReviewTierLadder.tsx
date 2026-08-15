import { useId } from "react";
import { FieldLabel } from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
	REVIEW_TIER_ADDS,
	REVIEW_TIER_LABELS,
	REVIEW_TIER_ORDER,
	type ReviewTier,
} from "@/lib/review-tiers";
import { cn } from "@/lib/utils";

export interface ReviewTierLadderProps {
	/**
	 * Required, because a `role="radiogroup"` takes no accessible name from a surrounding legend or
	 * table cell, and a screen renders one of these per area and per practice — an unnamed group is
	 * "Off, Propose, Deliver" with no subject.
	 */
	label: string;
	value: ReviewTier;
	onChange: (tier: ReviewTier) => void;
	disabled?: boolean;
	/** `full` spells out what each rung adds to the one before it. */
	variant?: "full" | "compact";
	/** Marks the value as inherited: shown, but somebody else's decision, so it recedes. */
	muted?: boolean;
	className?: string;
}

/**
 * Controlled, always: the tier in force is the server's, and every change is a write. There is no
 * `defaultValue` — a ladder that kept its own value would show a rung the workspace never accepted.
 *
 * Radio semantics rather than a toggle group: the rungs are mutually exclusive states of one setting,
 * which is what `role="radiogroup"` means, and it carries arrow-key movement along the axis. A toggle
 * group's items are `aria-pressed` buttons, which say "the others are off" rather than "this is
 * chosen".
 *
 * **The joined look belongs to the group, not to the rungs.** The group draws the one border, the one
 * radius and the lines between (`divide-*`); a rung is a plain row inside it that owns no geometry at
 * all. The previous build rounded each rung's outer corners by ordinal (`first:`/`last:`) and pulled
 * every rung a pixel over its neighbour, which can only be right along one axis: stacked below `sm`
 * those classes rounded the wrong sides, so the control arrived as three mismatched fragments — "the
 * parts are not connecting". Nothing here is breakpoint-coupled except the axis itself, and both axes
 * are drawn by the same two utilities.
 *
 * What went with it: a progress rail under the rungs, and a tint on every rung below the chosen one.
 * The rail was `hidden` below `sm`, so at the width the ladder most needed something joining the rungs
 * it drew nothing; and the tint claimed each rung is contained in the one above it, which is false at
 * the first rung — Off is not part of Propose. Containment is a claim the sentences make, one rung at a
 * time, and only where there is room to read them.
 *
 * Nothing here filters {@link REVIEW_TIER_ORDER} — every rung the vocabulary lists can be moved to.
 */
export function ReviewTierLadder({
	label,
	value,
	onChange,
	disabled = false,
	variant = "compact",
	muted = false,
	className,
}: ReviewTierLadderProps) {
	const full = variant === "full";
	const rungIdPrefix = useId();

	return (
		<RadioGroup
			aria-label={label}
			value={value}
			disabled={disabled}
			onValueChange={(next) => {
				const tier = next as ReviewTier;
				// Re-selecting the rung already in force is a change to nothing that the server would
				// still accept and record.
				if (tier && tier !== value) onChange(tier);
			}}
			className={cn(
				"flex min-w-0 flex-col gap-0 divide-y overflow-hidden rounded-md border bg-background",
				// Ascending autonomy runs left to right once there is room for it, which is the direction
				// the surrounding copy points ("everything to its left"), and top to bottom when there is not.
				"sm:flex-row sm:divide-x sm:divide-y-0",
				muted && "opacity-70",
				className,
			)}
		>
			{REVIEW_TIER_ORDER.map((tier) => {
				const titleId = `${rungIdPrefix}-${tier}`;
				const addsId = `${rungIdPrefix}-${tier}-adds`;
				const selected = tier === value;
				return (
					// Base UI's Radio renders a span plus a hidden input beside it, so the rung is a label
					// wrapping the control rather than one pointed at it by `htmlFor`: the id it would have
					// to name belongs to the input, and Base UI generates that itself. `FieldLabel` over a
					// bare `Label` for the kit's own selected treatment — it reads the primitive's
					// `data-checked`, so the tint is not a second copy of "which one is chosen".
					<FieldLabel
						key={tier}
						className={cn(
							"w-full min-w-0 cursor-pointer items-center gap-2 p-2 font-normal transition-colors sm:flex-1",
							// Guarded on the primitive's own `data-unchecked` so hover cannot repaint the
							// chosen rung in the colour of the ones beside it.
							"has-data-unchecked:hover:bg-muted/50",
							"has-data-disabled:cursor-not-allowed has-data-disabled:opacity-60",
							// A column at every width, not scoped to `sm:`. Laid out as a row, the untruncatable
							// sentence takes the space and squeezes the label's `truncate` down to one glyph —
							// and the accessible name still carries the real word, so every role query and the
							// axe gate pass over a visibly unreadable control.
							full && "flex-col items-start gap-1.5 p-3",
						)}
					>
						<span className="flex min-w-0 items-center gap-2">
							{/*
							  Named by the visible word alone (WCAG 2.2 SC 2.5.3), and described by the
							  sentence rather than hidden from it: pointing at both beats the older
							  `aria-label` + `aria-hidden` pair, which left a screen reader with the word and
							  no idea what the rung does.
							*/}
							<RadioGroupItem
								value={tier}
								aria-labelledby={titleId}
								aria-describedby={full ? addsId : undefined}
							/>
							<span id={titleId} className={cn("truncate text-sm", selected && "font-medium")}>
								{REVIEW_TIER_LABELS[tier]}
							</span>
						</span>
						{full && (
							// `ps-6` aligns the sentence with the word above it rather than with the rung's own
							// edge: the radio plus its gap is what the word is inset by.
							<span id={addsId} className="ps-6 text-muted-foreground text-xs">
								{REVIEW_TIER_ADDS[tier]}
							</span>
						)}
					</FieldLabel>
				);
			})}
		</RadioGroup>
	);
}
