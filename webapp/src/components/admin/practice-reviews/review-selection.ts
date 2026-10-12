import { z } from "zod";

import { ARTIFACT_KIND_VALUES, type KnownArtifactKind } from "@/lib/artifact-kinds";
import { hasText } from "@/lib/text";

import type { ReviewScope, ReviewScopeSearch } from "./review-scope";

/**
 * The one record the reader tracks with a row's crosshair, which every tab then narrows to: a piece
 * of work, a review, a result or a piece of feedback with the review that recorded it, or a
 * practice. It lives in the shared scope's URL names plus `selected=true`. The same names without `selected` are a plain filter,
 * as a "See all" link or an address from before the selection writes them: the lists narrow the
 * same way, and the toolbar shows a filter pill instead of the selection bar.
 */
export type ReviewSelection =
	| { kind: "work"; artifactKind: KnownArtifactKind; artifactId: number }
	| { kind: "review"; agentJobId: string }
	| { kind: "result"; practiceResultId: string; agentJobId?: string }
	| { kind: "feedback"; feedbackId: string; agentJobId?: string }
	| { kind: "practice"; practiceSlug: string };

export type ReviewSelectionKind = ReviewSelection["kind"];

/**
 * The selection's part of any route's search, read loosely: a row reads it from whichever route
 * renders it. Each tab's own schema has already dropped an id it cannot filter by.
 */
const selectionSearchSchema = z.object({
	agentJobId: z.string().min(1).optional().catch(undefined),
	practiceResultId: z.string().min(1).optional().catch(undefined),
	feedbackId: z.string().min(1).optional().catch(undefined),
	selectedPractice: z.string().min(1).optional().catch(undefined),
	artifactKind: z.enum(ARTIFACT_KIND_VALUES).optional().catch(undefined),
	artifactId: z.coerce.number().int().positive().optional().catch(undefined),
	selected: z.literal(true).optional().catch(undefined),
});

export function parseSelectionSearch(search: unknown): Partial<ReviewScopeSearch> {
	const parsed = selectionSearchSchema.safeParse(search);
	return parsed.success ? parsed.data : {};
}

type RecordScope = Pick<
	ReviewScope,
	| "agentJobId"
	| "practiceResultId"
	| "feedbackId"
	| "selectedPractice"
	| "artifactKind"
	| "artifactId"
	| "selected"
>;

/** The selection the scope holds: its record, when a crosshair picked it (`selected`). */
export function readSelection(scope: RecordScope): ReviewSelection | undefined {
	return scope.selected === true ? readScopedRecord(scope) : undefined;
}

/**
 * The one record the scope narrows to, picked or filtered. A result names its review too, and
 * a review the work it read, so the most specific part is the record.
 */
export function readScopedRecord(scope: RecordScope): ReviewSelection | undefined {
	if (hasText(scope.feedbackId)) {
		return { kind: "feedback", feedbackId: scope.feedbackId, agentJobId: scope.agentJobId };
	}
	if (hasText(scope.practiceResultId)) {
		return {
			kind: "result",
			practiceResultId: scope.practiceResultId,
			agentJobId: scope.agentJobId,
		};
	}
	if (hasText(scope.agentJobId)) {
		return { kind: "review", agentJobId: scope.agentJobId };
	}
	if (scope.artifactKind !== undefined && scope.artifactId !== undefined) {
		return { kind: "work", artifactKind: scope.artifactKind, artifactId: scope.artifactId };
	}
	if (hasText(scope.selectedPractice)) {
		return { kind: "practice", practiceSlug: scope.selectedPractice };
	}
	return undefined;
}

/** The search that makes `selection` the only one; `undefined` clears it. */
export function selectionSearch(
	selection: ReviewSelection | undefined,
): Pick<
	ReviewScopeSearch,
	| "agentJobId"
	| "practiceResultId"
	| "feedbackId"
	| "selectedPractice"
	| "artifactKind"
	| "artifactId"
	| "selected"
> {
	return {
		selected: selection === undefined ? undefined : true,
		agentJobId:
			selection?.kind === "review" || selection?.kind === "result" || selection?.kind === "feedback"
				? selection.agentJobId
				: undefined,
		practiceResultId: selection?.kind === "result" ? selection.practiceResultId : undefined,
		feedbackId: selection?.kind === "feedback" ? selection.feedbackId : undefined,
		selectedPractice: selection?.kind === "practice" ? selection.practiceSlug : undefined,
		artifactKind: selection?.kind === "work" ? selection.artifactKind : undefined,
		artifactId: selection?.kind === "work" ? selection.artifactId : undefined,
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
		case "work": {
			return (
				current.kind === "work" &&
				current.artifactKind === candidate.artifactKind &&
				current.artifactId === candidate.artifactId
			);
		}
		case "review": {
			return current.kind === "review" && current.agentJobId === candidate.agentJobId;
		}
		case "result": {
			return current.kind === "result" && current.practiceResultId === candidate.practiceResultId;
		}
		case "feedback": {
			return current.kind === "feedback" && current.feedbackId === candidate.feedbackId;
		}
		case "practice": {
			return current.kind === "practice" && current.practiceSlug === candidate.practiceSlug;
		}
	}
}
