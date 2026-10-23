import { forgetSectionFold } from "@/components/layout/section-folds";

/**
 * The foldable sections of each record sheet. A fold is kept per sheet and section, not per record:
 * an admin who folds the Technical section of one review finds it folded on the next.
 */
export const LEVEL_FOLDS = {
	review: ["what", "state", "practices", "feedback-checks", "practice-scripts", "technical"],
	result: ["what", "state", "details", "technical"],
	problem: ["what", "why", "evidence", "next-step"],
	feedback: ["what", "state", "says", "will-send", "based-on", "checks", "technical"],
	practice: ["what", "state", "technical"],
	work: ["what", "state", "technical"],
} as const;

export type FoldingLevel = keyof typeof LEVEL_FOLDS;

/** The key a sheet's section keeps its fold under: "review-level:feedback-checks". */
export function levelFoldKey<Level extends FoldingLevel>(
	level: Level,
	section: (typeof LEVEL_FOLDS)[Level][number],
): string {
	return `${level}-level:${section}`;
}

/** Forgets every stored fold of one sheet, so a story starts with all its sections open. */
export function forgetLevelFolds(level: FoldingLevel) {
	for (const section of LEVEL_FOLDS[level]) {
		forgetSectionFold(`${level}-level:${section}`);
	}
}
