import {
	CircleAlertIcon,
	CircleCheckIcon,
	CircleDashedIcon,
	CircleHelpIcon,
	CircleXIcon,
	type LucideIcon,
	ShieldCheckIcon,
} from "lucide-react";

import type { Assessment } from "@/components/practice-vocabulary/assessment-defs";
import type { Presence } from "@/components/practice-vocabulary/presence-defs";

/**
 * Taken from the registries that own the two enums rather than spelled out again: a value the server
 * adds then fails the build here instead of falling through to a silent default.
 */
export interface ObservationOutcomeInput {
	presence: Presence;
	assessment?: Assessment;
}

export type ObservationOutcome =
	| "PRESENT_GOOD"
	| "ABSENT_GOOD"
	| "PRESENT_BAD"
	| "ABSENT_BAD"
	| "NOT_APPLICABLE"
	| "INCONCLUSIVE";

export const OBSERVATION_OUTCOME_PRESENTATION = {
	PRESENT_GOOD: {
		label: "Strength shown",
		icon: CircleCheckIcon,
		className: "text-success",
	},
	ABSENT_GOOD: {
		label: "Risk avoided",
		icon: ShieldCheckIcon,
		className: "text-success",
	},
	PRESENT_BAD: {
		label: "Problem observed",
		icon: CircleAlertIcon,
		className: "text-destructive",
	},
	ABSENT_BAD: {
		label: "Expected practice missing",
		icon: CircleXIcon,
		className: "text-destructive",
	},
	NOT_APPLICABLE: {
		label: "Not assessed",
		icon: CircleDashedIcon,
		className: "text-muted-foreground",
	},
	// A distinct silence from NOT_APPLICABLE: the reviewer looked at work that DID offer the opportunity
	// and could not claim either way. Collapsing the two would report "no opportunity" for a practice the
	// reviewer was simply unsure about.
	INCONCLUSIVE: {
		label: "Not certain enough to say",
		icon: CircleHelpIcon,
		className: "text-muted-foreground",
	},
} as const satisfies Record<
	ObservationOutcome,
	{ label: string; icon: LucideIcon; className: string }
>;

/**
 * Derives the complete 2×2 observation state plus the two verdict-less states. The API guarantees no
 * assessment accompanies NOT_APPLICABLE or INCONCLUSIVE, so each is returned on its own before the 2×2 —
 * an unassessed presence that is neither falls back to NOT_APPLICABLE rather than inventing a verdict.
 */
export function observationOutcome(observation: ObservationOutcomeInput): ObservationOutcome {
	if (observation.presence === "INCONCLUSIVE") {
		return "INCONCLUSIVE";
	}
	if (observation.presence === "NOT_APPLICABLE" || !observation.assessment) {
		return "NOT_APPLICABLE";
	}
	return `${observation.presence}_${observation.assessment}`;
}
