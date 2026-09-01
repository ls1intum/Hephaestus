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
	INCONCLUSIVE: {
		label: "Not certain enough to say",
		icon: CircleHelpIcon,
		className: "text-muted-foreground",
	},
} as const satisfies Record<
	ObservationOutcome,
	{ label: string; icon: LucideIcon; className: string }
>;
export function observationOutcome(observation: ObservationOutcomeInput): ObservationOutcome {
	if (observation.presence === "INCONCLUSIVE") {
		return "INCONCLUSIVE";
	}
	if (observation.presence === "NOT_APPLICABLE" || !observation.assessment) {
		return "NOT_APPLICABLE";
	}
	return `${observation.presence}_${observation.assessment}`;
}
