import type { PracticeDefinition } from "@/api/types.gen";
import { hasText } from "@/lib/text";

import { REVIEWED_BY_DESCRIPTIONS, REVIEWED_BY_LABELS } from "./reviewed-by";

export function scriptLabel(script: string | null): string {
	return hasText(script) ? "Custom script" : "Built-in";
}

export function lineNotesSentence(lineNotes: boolean): string {
	return lineNotes
		? "Feedback appears beside the lines it is about."
		: "Feedback stays in the summary, not on a changed line.";
}

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
