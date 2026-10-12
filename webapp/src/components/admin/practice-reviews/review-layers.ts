import type { KnownArtifactKind } from "@/lib/artifact-kinds";
import { hasText } from "@/lib/text";

import { parseWorkLevel, type PracticeReviewLevel, workLevel } from "./review-levels";
import type { ReviewSelection } from "./review-selection";

/**
 * How Practice reviews is layered. Layer 0 is the tables. Layer 1 is one record's sheet, opened from
 * its row, or the trace of one record, opened from the row's Trace action. Layer 2 is a record's
 * sheet opened from a step of the trace, over it. A record's sheet has no link to another record,
 * so nothing opens deeper than that.
 *
 * In the address, a sheet is `?detail=<kind>:<id>` and a trace `?detail=trace:<kind>:<id>`, with the
 * sheet opened from it as a second `detail`.
 */
export type RecordKind = "review" | "result" | "feedback" | "work" | "practice";

/** One record of Practice reviews. A piece of work is `pull-request:42`, as `workLevel` writes it. */
export interface RecordRef {
	kind: RecordKind;
	id: string;
}

const RECORD_KINDS: readonly RecordKind[] = ["review", "result", "feedback", "work", "practice"];

function isRecordKind(kind: string): kind is RecordKind {
	return (RECORD_KINDS as readonly string[]).includes(kind);
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
		return hasText(level.id) ? { kind: level.kind, id: level.id } : undefined;
	}
	const separator = level.id.indexOf(":");
	const kind = level.id.slice(0, separator);
	const id = level.id.slice(separator + 1);
	return separator > 0 && isRecordKind(kind) && hasText(id) ? { kind, id } : undefined;
}

/** A piece of work as a record, or undefined for a kind this build cannot name in an address. */
export function workRecord(
	artifactKind: string,
	artifactId: number | string,
): RecordRef | undefined {
	const level = workLevel(artifactKind, artifactId);
	return level === undefined ? undefined : { kind: "work", id: level.id };
}

/** The piece of work a work record names. */
export function workOfRecord(
	record: RecordRef,
): { artifactKind: KnownArtifactKind; artifactId: number } | undefined {
	return record.kind === "work" ? parseWorkLevel(record.id) : undefined;
}

/**
 * What tracking a record selects: the record, with the review it came from where the row knows it,
 * so every list narrows to the same review at once.
 */
export function recordSelection(
	record: RecordRef,
	agentJobId?: string,
): ReviewSelection | undefined {
	switch (record.kind) {
		case "review": {
			return { kind: "review", agentJobId: record.id };
		}
		case "result": {
			return { kind: "result", practiceResultId: record.id, agentJobId };
		}
		case "feedback": {
			return { kind: "feedback", feedbackId: record.id, agentJobId };
		}
		case "practice": {
			return { kind: "practice", practiceSlug: record.id };
		}
		case "work": {
			const work = parseWorkLevel(record.id);
			return work === undefined ? undefined : { kind: "work", ...work };
		}
	}
}

/** The record a selection tracks, which its trace stands on. */
export function selectionRecord(selection: ReviewSelection): RecordRef | undefined {
	switch (selection.kind) {
		case "review": {
			return { kind: "review", id: selection.agentJobId };
		}
		case "result": {
			return { kind: "result", id: selection.practiceResultId };
		}
		case "feedback": {
			return { kind: "feedback", id: selection.feedbackId };
		}
		case "practice": {
			return { kind: "practice", id: selection.practiceSlug };
		}
		case "work": {
			return workRecord(selection.artifactKind, selection.artifactId);
		}
	}
}

/**
 * The levels the address may hold (`review-layers.ts`): one record's sheet, a trace, or a trace
 * with one record's sheet over it. Anything else is an address from before, when a level could open
 * over another: its front level is what it showed, kept as that record's sheet, and a trace keeps
 * the last level over it.
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
