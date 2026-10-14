import { z } from "zod";

import { hasText } from "@/lib/text";

import type { ReviewScope, ReviewScopeSearch } from "./review-scope";

/**
 * The one record the reader tracks with a row's crosshair, which every tab then narrows to: an
 * item, a review, a result or a piece of feedback with the review that recorded it, or a practice.
 * It lives in the shared scope's URL names plus `selected=true`. The same names without `selected`
 * are a plain filter, as a "See all" link writes them: the lists narrow the same way, and the
 * toolbar shows a filter pill instead of the selection bar.
 */
export type ReviewSelection =
	| { kind: "item"; itemId: string }
	| { kind: "review"; reviewId: string }
	| { kind: "result"; resultId: string; reviewId?: string }
	| { kind: "feedback"; feedbackId: string; reviewId?: string }
	| { kind: "practice"; practiceSlug: string };

export type ReviewSelectionKind = ReviewSelection["kind"];

/**
 * A workspace item or account id: a positive decimal, never a provider's own number. A link written
 * outside the app, such as the browser extension's, carries it bare, which the router reads as a
 * number; either way it reads back as the decimal string.
 */
export const positiveIdParam = z
	.union([z.string(), z.number().int()])
	.transform(String)
	.pipe(z.string().regex(/^[1-9]\d{0,18}$/u))
	.optional()
	.catch(undefined);

/**
 * A review, result, problem or feedback id. Shaped as a UUID, as the server parses one: any
 * version, so `z.guid()` rather than the RFC-strict `z.uuid()`.
 */
export const uuidParam = z.guid().optional().catch(undefined);

/**
 * The selection's part of any route's search, read loosely: a row reads it from whichever route
 * renders it. A malformed id reads as none, so a bad address closes nothing it should not.
 */
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

/**
 * The one record the scope narrows to, picked or filtered. A result names its review too, and a
 * review the item it read, so the most specific part is the record.
 */
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
