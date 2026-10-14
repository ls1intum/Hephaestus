import { CircleCheckIcon, CircleDashedIcon, CircleHelpIcon, WrenchIcon } from "lucide-react";
import type { ResultList } from "@/api/types.gen";
import type { StatusDefs } from "@/components/common/status-def";

export type Outcome = ResultList["outcome"];
export const OUTCOME_DEFS: StatusDefs<Outcome> = {
	MET: {
		label: "Met",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "The applicable practice standard is met in the reviewed evidence.",
	},
	NOT_MET: {
		label: "Not met",
		icon: WrenchIcon,
		badgeVariant: "destructive",
		description: "The applicable practice standard is not met in the reviewed evidence.",
	},
	DOES_NOT_APPLY: {
		label: "Does not apply",
		icon: CircleDashedIcon,
		badgeVariant: "secondary",
		description: "Nothing in this work calls for this practice.",
	},
	COULD_NOT_DETERMINE: {
		label: "Could not determine",
		icon: CircleHelpIcon,
		badgeVariant: "secondary",
		description:
			"The review read the evidence, and a question it names stayed open, so it did not decide whether the practice standard is met.",
	},
};

/** "3 not met results": a count of results with one outcome, as a list facet or a count cell says it. */
export function outcomeCountNoun(outcome: Outcome, count: number): string {
	const label = OUTCOME_DEFS[outcome].label.toLowerCase();
	return `${label} ${count === 1 ? "result" : "results"}`;
}

/**
 * "3 problems": the problems under a not met result. Every problem is at a place in the work, so
 * the count is the number of rows and needs no other word.
 */
export function problemCount(count: number): string {
	return `${count} ${count === 1 ? "problem" : "problems"}`;
}

/**
 * The outcome as a result's head line says it: "Not met · 3 problems" when its problems are known,
 * and the outcome's own word otherwise.
 */
export function resultVerdict(outcome: Outcome, problems: number | undefined): string {
	const { label } = OUTCOME_DEFS[outcome];
	return outcome === "NOT_MET" && problems !== undefined && problems > 0
		? `${label} · ${problemCount(problems)}`
		: label;
}
