import type {
	PracticeItemTypeOption,
	PracticeReadSource,
	ReadSourceOption,
	ScanDefinition,
} from "@/api/types.gen";

import { readsProblem } from "./review-settings";

export const MAX_SCAN_PATTERNS = 64;
export const MAX_SCAN_PATTERN_LENGTH = 512;

/** Match server path rules: relative paths, forward slashes, and no traversal, .git, NUL, or brace expansion. */
export function pathPatternProblem(pattern: string): string | undefined {
	if (pattern.length > MAX_SCAN_PATTERN_LENGTH) {
		return `Use at most ${MAX_SCAN_PATTERN_LENGTH} characters in a pattern.`;
	}
	if (pattern.startsWith("/") || /^[A-Za-z]:/u.test(pattern)) {
		return `Use a path relative to the repository root, not “${pattern}”.`;
	}
	if (pattern.includes("\\")) {
		return `Use forward slashes in “${pattern}”.`;
	}
	if (pattern.includes("\0") || pattern.includes("{") || pattern.includes("}")) {
		return `Remove the brace expansion or control character from “${pattern}”.`;
	}
	const components = pattern.split("/");
	if (components.some((component) => component === "" || component === ".")) {
		return `Remove the empty or “.” segment from “${pattern}”.`;
	}
	if (components.includes("..")) {
		return `A pattern cannot leave the repository: remove “..” from “${pattern}”.`;
	}
	if (components.includes(".git")) {
		return `A scan never reads the .git folder: remove it from “${pattern}”.`;
	}
	return undefined;
}

export type ScanPatternField = "include" | "exclude" | "context";

/**
 * A scan while it is being edited: the patterns as the author typed them, one per line, so a blank
 * line being written is not taken away under the caret.
 */
export interface ScanDraft {
	itemKind: string;
	include: string;
	exclude: string;
	context: string;
	reads: PracticeReadSource[];
}

export function patternsOf(text: string): string[] {
	return [
		...new Set(
			text
				.split("\n")
				.map((line) => line.trim())
				.filter((line) => line !== ""),
		),
	];
}

export function scanDraftOf(scan: ScanDefinition): ScanDraft {
	return {
		itemKind: scan.itemKind,
		include: scan.include.join("\n"),
		exclude: scan.exclude.join("\n"),
		context: scan.context.join("\n"),
		reads: scan.reads.sources,
	};
}

export function emptyScanDraft(itemType: PracticeItemTypeOption): ScanDraft {
	return {
		itemKind: itemType.scanItemKinds[0] ?? "",
		include: "",
		exclude: "",
		context: "",
		reads: [],
	};
}

export function scanOf(draft: ScanDraft, contract: string): ScanDefinition {
	return {
		itemKind: draft.itemKind,
		include: patternsOf(draft.include),
		exclude: patternsOf(draft.exclude),
		context: patternsOf(draft.context),
		reads: { contract, sources: draft.reads },
	};
}

export function scanFieldId(field: ScanPatternField | "itemKind" | "reads"): string {
	return `practice-scan-${field}`;
}

export interface ScanProblem {
	field: ScanPatternField | "itemKind" | "reads";
	message: string;
}

const FIELD_NAMES: Record<ScanPatternField, string> = {
	include: "Include",
	exclude: "Exclude",
	context: "Context",
};

/** Validate reads against the scanned item kind; it can differ from the practice's own kind. */
export function scanProblem(
	draft: ScanDraft,
	itemType: PracticeItemTypeOption,
	readSources: readonly ReadSourceOption[],
): ScanProblem | undefined {
	if (!itemType.scanItemKinds.includes(draft.itemKind)) {
		return { field: "itemKind", message: "Choose what the scan reviews." };
	}
	for (const field of ["include", "exclude", "context"] as const) {
		const patterns = patternsOf(draft[field]);
		if (field === "include" && patterns.length === 0) {
			return { field, message: "Include at least one path pattern, such as **/*.ts." };
		}
		if (patterns.length > MAX_SCAN_PATTERNS) {
			return {
				field,
				message: `Use at most ${MAX_SCAN_PATTERNS} patterns in ${FIELD_NAMES[field]}.`,
			};
		}
		for (const pattern of patterns) {
			const message = pathPatternProblem(pattern);
			if (message !== undefined) {
				return { field, message };
			}
		}
	}
	const message = readsProblem(draft.reads, readSources);
	return message === undefined ? undefined : { field: "reads", message };
}
