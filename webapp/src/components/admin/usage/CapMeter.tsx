import type { ReactNode } from "react";
import { formatCapUsd, formatCostUsd } from "@/components/admin/ai/job-utils";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import { BUDGET_WARN_PERCENT } from "./usage-utils";

/** Whether a cap is worth naming as a state, and which one. `null` means "just a number". */
export type CapState = "paused" | "near" | null;

/**
 * One place decides when a cap is a state at all.
 *
 * A past month is never a state: the caps are compared against *today's* limits, so a finished month
 * can be over one without anything being held back. Below {@link BUDGET_WARN_PERCENT} the meter
 * alone says enough.
 */
export function capState(
	percent: number | undefined,
	paused: boolean,
	isCurrentMonth: boolean,
): CapState {
	if (!isCurrentMonth) {
		return null;
	}
	if (paused) {
		return "paused";
	}
	return percent != null && percent >= BUDGET_WARN_PERCENT ? "near" : null;
}

/**
 * The words for each state, shared so the two consoles cannot drift into synonyms. The tone never
 * carries the state alone — every caller prints one of these beside the bar (WCAG SC 1.4.1).
 */
export const CAP_STATE_LABELS: Record<Exclude<CapState, null>, string> = {
	paused: "Paused",
	near: "Near cap",
};

export interface CapMeterProps {
	spendUsd: number;
	capUsd: number;
	/** Share of the cap consumed. May exceed 100; the bar clamps, the announcement does not. */
	percent: number;
	paused: boolean;
	/**
	 * Accessible name: "Shared-model budget used" / "Provider cap used by Acme". Distinct per meter,
	 * so a screen reader never has to guess which cap it is on.
	 */
	label: string;
}

/**
 * One cap's consumption, everywhere a cap is shown.
 *
 * Three tones, so the approach to a cap is legible before the cap is reached: normal, amber from
 * {@link BUDGET_WARN_PERCENT} — the same threshold that raises the pace warning — and destructive
 * once the cap is reached or the pause is live.
 *
 * The bar is all this owns. Layout and the caption underneath belong to the surface (a card has
 * room for "82% used · Near cap"; a table cell needs the amount in the same breath), but the tone
 * mapping, the clamping and the announcement grammar must never diverge between them.
 */
export function CapMeter({ spendUsd, capUsd, percent, paused, label }: CapMeterProps): ReactNode {
	const value = Math.min(Math.max(percent, 0), 100);
	const rounded = Math.round(percent);
	// Percent first: it is the answer to "how close am I", and the amounts qualify it. Comma, not an
	// em-dash — screen readers render an em-dash inconsistently, and some spell it out.
	const valueText = `${rounded}% used, ${formatCostUsd(spendUsd)} of ${formatCapUsd(capUsd)}`;
	// Styled through the kit's slots rather than by composing the primitive by hand: `Progress` owns
	// which parts exist and how they nest, and this only overrides the two declarations that carry
	// meaning here — the bar's height and its tone.
	const tone =
		paused || percent >= 100
			? "**:data-[slot=progress-indicator]:bg-destructive"
			: percent >= BUDGET_WARN_PERCENT
				? "**:data-[slot=progress-indicator]:bg-warning"
				: "**:data-[slot=progress-indicator]:bg-primary";

	return (
		<Progress
			value={value}
			aria-label={label}
			getAriaValueText={() => valueText}
			className={cn("w-full *:data-[slot=progress-track]:h-1.5", tone)}
		/>
	);
}
