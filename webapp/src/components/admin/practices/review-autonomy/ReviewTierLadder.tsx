import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
	REVIEW_TIER_ADDS,
	REVIEW_TIER_LABELS,
	REVIEW_TIER_ORDER,
	REVIEW_TIER_SELECTABLE,
	type ReviewTier,
} from "@/lib/review-tiers";
import { cn } from "@/lib/utils";

export interface ReviewTierLadderProps {
	/**
	 * Names the group for a screen reader. A `role="radiogroup"` takes no name from a surrounding
	 * legend or table cell, and at twenty-five areas on one screen "Off, Observe, Deliver" repeated
	 * with no owner is the difference between a usable list and an unnavigable one.
	 */
	label: string;
	value: ReviewTier;
	onChange: (tier: ReviewTier) => void;
	disabled?: boolean;
	/**
	 * `full` spells out what each rung adds and draws the cumulative rail; `compact` is the row-level
	 * read-out, where the same sentences twenty-nine times over would bury the list they annotate.
	 */
	variant?: "full" | "compact";
	/** Inherited values are shown, not hidden — but they are somebody else's decision, so they recede. */
	muted?: boolean;
	className?: string;
}

/**
 * The autonomy tier as the ordered axis it is.
 *
 * <p>A dropdown presents four unrelated options and hides three of them until you open it; this is one
 * axis where every stop contains the one before it, and the whole point of the screen is that an admin
 * can see where their workspace sits on it. So: a segmented control, laid out left to right in
 * {@link REVIEW_TIER_ORDER}.
 *
 * <p>Real radio semantics rather than a toggle group. The rungs are mutually exclusive states of one
 * setting, which is what `role="radiogroup"` means; a toggle group's items are `aria-pressed` buttons,
 * which says "three of these are off" rather than "this one is chosen". It also buys arrow-key movement
 * along the axis for free, which is the interaction the shape is promising.
 *
 * <p>Propose is rendered and cannot be moved to. It stays on the ladder because removing it leaves a gap
 * between "records it" and "says it unasked" that the remaining words cannot describe, and because a
 * workspace whose data already holds it — the enum and the DB CHECK both admit it — must be able to see
 * that rather than have the control silently show something else. It is disabled *except* when it is the
 * current value, so a keyboard can still land on the rung that is in force.
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
					// Re-selecting the rung already in force is the only way to "choose" Propose, and the
					// server treats it as a no-op. Dropping it here keeps a stray click from spending a
					// request, and keeps a disabled-but-checked rung from looking like a refusal.
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
					const locked = disabled || (!REVIEW_TIER_SELECTABLE[tier] && !selected);
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
								full ? "sm:rounded-md sm:not-first:ml-0 sm:flex-col sm:gap-1.5" : "items-center",
								selected && "z-10 border-primary bg-primary/5",
								// Everything at or below the chosen rung is included in it. Tinting the run rather
								// than only the endpoint is what makes "each adds to the previous" visible without a
								// second widget to read.
								!selected && index < selectedIndex && "bg-muted/60",
								locked && "cursor-not-allowed opacity-60",
							)}
						>
							<span className="flex min-w-0 items-center gap-2">
								{/* The name is the visible word, exactly — a voice-control user says "Observe" and
								    means this rung (WCAG 2.2 SC 2.5.3). */}
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
								<span className="text-muted-foreground text-xs" aria-hidden="true">
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
 * A rail filled from the left edge to the rung in force.
 *
 * <p>Decoration, and marked as such: the radio group above already says which tier is chosen, and a
 * screen reader reading "progress, 33%" over a setting that is not progress would be worse than
 * silence. What it adds for a sighted reader is the direction of the axis — Off leaves it empty, which
 * is the honest picture of a workspace that reviews nothing.
 */
function CumulativeRail({ selectedIndex }: { selectedIndex: number }) {
	const filled = selectedIndex <= 0 ? 0 : (selectedIndex / (REVIEW_TIER_ORDER.length - 1)) * 100;
	return (
		<div aria-hidden="true" className="mt-2 hidden h-1 rounded-full bg-muted sm:block">
			<div
				className="h-full rounded-full bg-primary transition-all"
				style={{ width: `${filled}%` }}
			/>
		</div>
	);
}
