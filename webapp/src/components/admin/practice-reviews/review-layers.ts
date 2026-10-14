import { z } from "zod";

import { hasText } from "@/lib/text";

import type { PracticeReviewLevel } from "./review-levels";
import type { ReviewSelection } from "./review-selection";

/**
 * How Practice reviews is layered. Layer 0 is the tables. Layer 1 is one record's sheet, opened from
 * its row, or the trace of one record, opened from the row's Trace action. Layer 2 is a record's
 * sheet opened from a step of the trace, over it. A problem's sheet links its parent result, which
 * opens in its place, so nothing opens deeper than that.
 *
 * In the address, a sheet is `?detail=<kind>:<id>` and a trace `?detail=trace:<kind>:<id>`, with the
 * sheet opened from it as a second `detail`. A `trace` describes the record it stands on; it is no
 * record of its own.
 */
export type RecordKind = "review" | "result" | "problem" | "feedback" | "item" | "practice";

/** One record of Practice reviews. An item is its workspace item id. */
export interface RecordRef {
	kind: RecordKind;
	id: string;
}

const uuid = z.guid();
const itemId = z.string().regex(/^[1-9]\d{0,18}$/u);

/**
 * Whether `id` is a well-formed id of `kind`: a UUID for a review, result, problem or feedback, a
 * positive item id, and any slug for a practice. A malformed level closes instead of loading.
 */
function isRecordId(kind: RecordKind, id: string): boolean {
	switch (kind) {
		case "item": {
			return itemId.safeParse(id).success;
		}
		case "practice": {
			return hasText(id);
		}
		case "review":
		case "result":
		case "problem":
		case "feedback": {
			return uuid.safeParse(id).success;
		}
	}
}

const RECORD_KINDS: readonly RecordKind[] = [
	"review",
	"result",
	"problem",
	"feedback",
	"item",
	"practice",
];

function isRecordKind(kind: string): kind is RecordKind {
	return RECORD_KINDS.some((known) => known === kind);
}

/** The sheet of one record. */
export function recordLevel(record: RecordRef): PracticeReviewLevel {
	return { kind: record.kind, id: record.id };
}

/** The trace of one record. */
export function traceLevel(record: RecordRef): PracticeReviewLevel {
	return { kind: "trace", id: `${record.kind}:${record.id}` };
}

/** The record a level shows, or a trace stands on. Undefined for an id hand-edited into nonsense. */
export function levelRecord(level: PracticeReviewLevel): RecordRef | undefined {
	if (level.kind !== "trace") {
		return isRecordId(level.kind, level.id) ? { kind: level.kind, id: level.id } : undefined;
	}
	const separator = level.id.indexOf(":");
	const kind = level.id.slice(0, separator);
	const id = level.id.slice(separator + 1);
	return separator > 0 && isRecordKind(kind) && isRecordId(kind, id) ? { kind, id } : undefined;
}

/**
 * What tracking a record selects: the record, with the review it came from where the row knows it,
 * so every list narrows to the same review at once. A problem selects its parent result, which is
 * what the lists filter by.
 */
export function recordSelection(
	record: RecordRef,
	context: { reviewId?: string; resultId?: string } = {},
): ReviewSelection | undefined {
	switch (record.kind) {
		case "review": {
			return { kind: "review", reviewId: record.id };
		}
		case "result": {
			return { kind: "result", resultId: record.id, reviewId: context.reviewId };
		}
		case "problem": {
			return context.resultId === undefined
				? undefined
				: { kind: "result", resultId: context.resultId, reviewId: context.reviewId };
		}
		case "feedback": {
			return { kind: "feedback", feedbackId: record.id, reviewId: context.reviewId };
		}
		case "practice": {
			return { kind: "practice", practiceSlug: record.id };
		}
		case "item": {
			return { kind: "item", itemId: record.id };
		}
	}
}

/** The record a selection tracks, which its trace stands on. */
export function selectionRecord(selection: ReviewSelection): RecordRef {
	switch (selection.kind) {
		case "review": {
			return { kind: "review", id: selection.reviewId };
		}
		case "result": {
			return { kind: "result", id: selection.resultId };
		}
		case "feedback": {
			return { kind: "feedback", id: selection.feedbackId };
		}
		case "practice": {
			return { kind: "practice", id: selection.practiceSlug };
		}
		case "item": {
			return { kind: "item", id: selection.itemId };
		}
	}
}

/**
 * The levels the address may hold: one record's sheet, a trace, or a trace with one record's sheet
 * over it. Anything else keeps its front level as that record's sheet, and a trace keeps the last
 * level over it.
 */
export function layeredStack(stack: readonly PracticeReviewLevel[]): PracticeReviewLevel[] {
	const at = stack.map((entry) => entry.kind).lastIndexOf("trace");
	const front = stack.at(-1);
	const trace = stack[at];
	if (trace === undefined) {
		return front === undefined ? [] : [front];
	}
	const over = stack.slice(at + 1).at(-1);
	return over === undefined ? [trace] : [trace, over];
}
