import type { PracticeDefinition } from "@/api/types.gen";
import { hasText } from "@/lib/text";

import { REVIEWED_BY_DESCRIPTIONS, REVIEWED_BY_LABELS } from "./reviewed-by";

/** What reviews a practice that Hephaestus reviews: the built-in review, or the author's script. */
export function scriptLabel(script: string | null): string {
	return hasText(script) ? "Custom script" : "Built-in";
}

/** Where a practice's feedback sits on the work, as a sentence. */
export function lineNotesSentence(lineNotes: boolean): string {
	return lineNotes
		? "Feedback appears beside the lines it is about."
		: "Feedback stays in the summary, not on a changed line.";
}

/**
 * Reviewed by in words: the choice, then what it means for this practice. People carry their saved
 * reason; Nobody says the practice offers guidance only.
 */
export function reviewedBySentence(reviewedBy: PracticeDefinition["reviewedBy"]): {
	label: string;
	detail: string | null;
} {
	switch (reviewedBy.by) {
		case "HEPHAESTUS": {
			return { label: REVIEWED_BY_LABELS.HEPHAESTUS, detail: null };
		}
		case "PEOPLE": {
			return {
				label: REVIEWED_BY_LABELS.PEOPLE,
				detail: reviewedBy.humanReason?.description ?? null,
			};
		}
		case "NOBODY": {
			return { label: REVIEWED_BY_LABELS.NOBODY, detail: REVIEWED_BY_DESCRIPTIONS.NOBODY };
		}
	}
}
