import { Label } from "@/components/ui/label";
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
	/** `full` spells out what each rung adds and draws the cumulative rail. */
	variant?: "full" | "compact";
	/** Marks the value as inherited: shown, but somebody else's decision, so it recedes. */
	muted?: boolean;
	className?: string;
}

/**
 * Radio semantics rather than a toggle group: the rungs are mutually exclusive states of one setting,
 * which is what `role="radiogroup"` means, and it carries arrow-key movement along the axis. A toggle
 * group's items are `aria-pressed` buttons, which say "the others are off" rather than "this is
 * chosen".
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
	const selectedIndex = REVIEW_TIER_ORDER.indexOf(value);

	return (
		<div className={cn("min-w-0", className)}>
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
					"flex w-full flex-col gap-1 sm:flex-row sm:gap-0",
					full && "sm:gap-2",
					muted && "opacity-70",
				)}
			>
				{REVIEW_TIER_ORDER.map((tier, index) => {
					const selected = tier === value;
					const locked = disabled;
					return (
						// Base UI's Radio renders a span plus a hidden input beside it, so the rung is a label
						// wrapping the control rather than one pointed at it by `htmlFor`: the id it would have
						// to name belongs to the input, and Base UI generates that itself.
						<Label
							key={tier}
							className={cn(
								"flex min-w-0 flex-1 cursor-pointer items-start gap-2 border border-input bg-background p-2 font-normal transition-colors",
								"first:rounded-t-md last:rounded-b-md sm:first:rounded-l-md sm:first:rounded-r-none sm:last:rounded-r-md sm:last:rounded-l-none",
								"not-first:-mt-px sm:not-first:mt-0 sm:not-first:-ml-px",
								// `flex-col` at every width, not scoped to `sm:`. Laid out as a row, the untruncatable
								// description takes the space and squeezes the label's `truncate` down to one glyph
								// — and the `aria-label` still carries the real word, so every role query and the
								// axe gate pass over a visibly unreadable control.
								full ? "flex-col gap-1.5 sm:rounded-md sm:not-first:ml-0" : "items-center",
								selected && "z-10 border-primary bg-primary/5",
								// Every rung below the chosen one is included in it, so the run is tinted rather
								// than just the endpoint.
								!selected && index < selectedIndex && "bg-muted/60",
								locked && "cursor-not-allowed opacity-60",
							)}
						>
							<span className="flex min-w-0 items-center gap-2">
								{/* The name is exactly the visible word (WCAG 2.2 SC 2.5.3). */}
								<RadioGroupItem
									value={tier}
									aria-label={REVIEW_TIER_LABELS[tier]}
									disabled={locked}
								/>
								<span
									className={cn("truncate text-sm", selected && "font-medium")}
									aria-hidden="true"
								>
									{REVIEW_TIER_LABELS[tier]}
								</span>
							</span>
							{full && (
								// `ps-6` aligns the sentence with the label above it rather than with the rung's own
								// edge: the radio plus its gap is what the word is inset by.
								<span className="ps-6 text-muted-foreground text-xs" aria-hidden="true">
									{REVIEW_TIER_ADDS[tier]}
								</span>
							)}
						</Label>
					);
				})}
			</RadioGroup>
			{full && <CumulativeRail selectedIndex={selectedIndex} />}
		</div>
	);
}

/**
 * Decoration, and `aria-hidden` on purpose: the radio group above already announces which tier is
 * chosen, and a screen reader reading "progress, 33%" over a setting that is not progress is worse
 * than silence.
 */
function CumulativeRail({ selectedIndex }: { selectedIndex: number }) {
	const filled = selectedIndex <= 0 ? 0 : (selectedIndex / (REVIEW_TIER_ORDER.length - 1)) * 100;
	return (
		<div aria-hidden="true" className="mt-2 hidden h-0.5 rounded-full bg-muted sm:block">
			<div
				className="h-full rounded-full bg-primary/60 transition-all"
				style={{ width: `${filled}%` }}
			/>
		</div>
	);
}
