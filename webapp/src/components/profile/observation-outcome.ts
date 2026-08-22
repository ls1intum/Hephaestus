export type ObservationPresence = "PRESENT" | "ABSENT" | "NOT_APPLICABLE" | "INCONCLUSIVE";
export type ObservationAssessment = "GOOD" | "BAD";

export interface ObservationOutcomeInput {
	presence: ObservationPresence;
	assessment?: ObservationAssessment;
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
		className: "text-success",
		barClassName: "bg-success",
		trendPolarity: 1,
	},
	ABSENT_GOOD: {
		label: "Risk avoided",
		className: "text-success",
		barClassName: "bg-success/45",
		trendPolarity: 1,
	},
	PRESENT_BAD: {
		label: "Problem observed",
		className: "text-destructive",
		barClassName: "bg-destructive",
		trendPolarity: -1,
	},
	ABSENT_BAD: {
		label: "Expected practice missing",
		className: "text-destructive",
		barClassName: "bg-destructive/45",
		trendPolarity: -1,
	},
	NOT_APPLICABLE: {
		label: "Not assessed",
		className: "text-muted-foreground",
		barClassName: "bg-muted-foreground/30",
		trendPolarity: null,
	},
	// A distinct silence from NOT_APPLICABLE: the reviewer looked at work that DID offer the opportunity
	// and could not claim either way. Collapsing the two would report "no opportunity" for a practice the
	// reviewer was simply unsure about.
	INCONCLUSIVE: {
		label: "Not certain enough to say",
		className: "text-muted-foreground",
		barClassName: "bg-muted-foreground/30",
		trendPolarity: null,
	},
} as const satisfies Record<
	ObservationOutcome,
	{ label: string; className: string; barClassName: string; trendPolarity: 1 | -1 | null }
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
