import { z } from "zod";

import { hasText } from "@/lib/text";

import type { ReviewScope, ReviewScopeSearch } from "./review-scope";

export type ReviewSelection =
	| { kind: "item"; itemId: string }
	| { kind: "review"; reviewId: string }
	| { kind: "result"; resultId: string; reviewId?: string }
	| { kind: "feedback"; feedbackId: string; reviewId?: string }
	| { kind: "practice"; practiceSlug: string };

export type ReviewSelectionKind = ReviewSelection["kind"];

/** Accept decimal strings and numbers from external links, then normalize workspace ids to strings. */
export const positiveIdParam = z
	.union([z.string(), z.number().int()])
	.transform(String)
	.pipe(z.string().regex(/^[1-9]\d{0,18}$/u))
	.optional()
	.catch(undefined);

// Match the server's UUID parser with z.guid(); z.uuid() enforces a stricter RFC shape.
export const uuidParam = z.guid().optional().catch(undefined);

const selectionSearchSchema = z.object({
	reviewId: uuidParam,
	resultId: uuidParam,
	feedbackId: uuidParam,
	itemId: positiveIdParam,
	selectedPractice: z.string().min(1).optional().catch(undefined),
	selected: z.literal(true).optional().catch(undefined),
});

export function parseSelectionSearch(search: unknown): Partial<ReviewScopeSearch> {
	const parsed = selectionSearchSchema.safeParse(search);
	return parsed.success ? parsed.data : {};
}

type RecordScope = Pick<
	ReviewScope,
	"reviewId" | "resultId" | "feedbackId" | "selectedPractice" | "itemId" | "selected"
>;

/** The selection the scope holds: its record, when a crosshair picked it (`selected`). */
export function readSelection(scope: RecordScope): ReviewSelection | undefined {
	return scope.selected === true ? readScopedRecord(scope) : undefined;
}

export function readScopedRecord(scope: RecordScope): ReviewSelection | undefined {
	if (hasText(scope.feedbackId)) {
		return { kind: "feedback", feedbackId: scope.feedbackId, reviewId: scope.reviewId };
	}
	if (hasText(scope.resultId)) {
		return { kind: "result", resultId: scope.resultId, reviewId: scope.reviewId };
	}
	if (hasText(scope.reviewId)) {
		return { kind: "review", reviewId: scope.reviewId };
	}
	if (hasText(scope.itemId)) {
		return { kind: "item", itemId: scope.itemId };
	}
	if (hasText(scope.selectedPractice)) {
		return { kind: "practice", practiceSlug: scope.selectedPractice };
	}
	return undefined;
}

/** The search that makes `selection` the only one; `undefined` clears it. */
export function selectionSearch(selection: ReviewSelection | undefined): {
	[K in keyof Required<RecordScope>]: Required<RecordScope>[K] | undefined;
} {
	return {
		selected: selection === undefined ? undefined : true,
		reviewId:
			selection?.kind === "review" || selection?.kind === "result" || selection?.kind === "feedback"
				? selection.reviewId
				: undefined,
		resultId: selection?.kind === "result" ? selection.resultId : undefined,
		feedbackId: selection?.kind === "feedback" ? selection.feedbackId : undefined,
		selectedPractice: selection?.kind === "practice" ? selection.practiceSlug : undefined,
		itemId: selection?.kind === "item" ? selection.itemId : undefined,
	};
}

export function isSameSelection(
	current: ReviewSelection | undefined,
	candidate: ReviewSelection,
): boolean {
	if (current === undefined || current.kind !== candidate.kind) {
		return false;
	}
	switch (candidate.kind) {
		case "item": {
			return current.kind === "item" && current.itemId === candidate.itemId;
		}
		case "review": {
			return current.kind === "review" && current.reviewId === candidate.reviewId;
		}
		case "result": {
			return current.kind === "result" && current.resultId === candidate.resultId;
		}
		case "feedback": {
			return current.kind === "feedback" && current.feedbackId === candidate.feedbackId;
		}
		case "practice": {
			return current.kind === "practice" && current.practiceSlug === candidate.practiceSlug;
		}
	}
}
