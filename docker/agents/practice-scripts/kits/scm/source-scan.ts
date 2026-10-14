import type { PracticeInputs } from "../../lib/core/contract.ts";
import type { Files, FileTree } from "./index.ts";
import {
	occurrences,
	distinctOccurrences,
	placeOccurrences,
	selectedSources,
	selectedOccurrences,
} from "./occurrences.ts";
/** Shared source-line scanning and declaration location; pattern matches are hints, not verdicts. */

import {
	type Declaration,
	declarationsOf,
	enclosingDeclaration,
	hasDeclarationSyntax,
	isCommentLine,
} from "./declarations.ts";
import { isTestPath, languageOf } from "./languages.ts";
import type { DiffFile } from "./types.ts";

export type SourcePattern = [label: string, test: RegExp];

/** One added line that a pattern matched, with where it sits. */
export interface ScanHint {
	file: string;
	line: number;
	/** The label of the pattern that matched. */
	pattern: string;
	/** The trimmed line, at most 160 characters. */
	context: string;
	flags: Record<string, boolean | number | string>;
}

export interface location {
	/** `struct EventList`, `file scope`, or `unknown` when the checkout or the language row is missing. */
	enclosing: string;
	supertypes: string;
	/** Whether the enclosing declaration's supertypes match the scan's `scope`; null when unknown. */
	inScope: boolean | null;
}

interface SourceScan {
	/** The first `maxHints` matching lines; `matched` and `countLabel` count every one. */
	hints: ScanHint[];
	/** Every matching line, shown or not. */
	matched: number;
	/** Matching lines by pattern label, over every line scanned. */
	counts: Map<string, number>;
	/** Source files of the change in the languages named, tests excluded unless included. */
	filesScanned: number;
	/** Added source lines (comments excluded) in the languages named. */
	linesAdded: number;
	/** Of those, inside a declaration the scope matched. */
	linesInScope: number;
	/** Source files of the change whose declarations could not be read from the checkout. */
	filesWithUnknownLocation: number;
}

interface SourceScanOptions {
	languages: readonly string[];
	patterns: readonly SourcePattern[];
	/** Declarations whose supertypes match are "in scope" — `/\bView\b/` for SwiftUI views. */
	scope?: RegExp;
	/** Report only lines inside a matching declaration (or of unknown placing). */
	onlyInScope?: boolean;
	includeTests?: boolean;
	maxHints?: number;
}

function place(decls: Declaration[] | null, line: number, scope: RegExp | undefined): location {
	if (decls === null) {
		return { enclosing: "unknown", supertypes: "", inScope: null };
	}
	const decl = enclosingDeclaration(decls, line);
	if (!decl) {
		return { enclosing: "file scope", supertypes: "", inScope: false };
	}
	return {
		enclosing: `${decl.kind} ${decl.name}`,
		supertypes: decl.supertypes,
		inScope: scope ? scope.test(decl.supertypes) : false,
	};
}

/** Scan the lines the change adds, placing each in its declaration as the head tree holds it. */
export async function scanAddedLines(
	tree: Pick<FileTree, "read"> | null,
	diffFiles: ReadonlyMap<string, DiffFile>,
	options: SourceScanOptions,
): Promise<SourceScan> {
	const scan: SourceScan = {
		hints: [],
		matched: 0,
		counts: new Map(),
		filesScanned: 0,
		linesAdded: 0,
		linesInScope: 0,
		filesWithUnknownLocation: 0,
	};
	for (const [path, df] of diffFiles) {
		const language = languageOf(path);
		if (language === null || !options.languages.includes(language)) {
			continue;
		}
		if (options.includeTests !== true && isTestPath(path)) {
			continue;
		}
		scan.filesScanned += 1;
		const decls = hasDeclarationSyntax(language)
			? await declarationsOf(tree, path, language)
			: null;
		if (decls === null) {
			scan.filesWithUnknownLocation += 1;
		}
		for (const [line, content] of df.addedLines) {
			if (isCommentLine(content, language)) {
				continue;
			}
			scan.linesAdded += 1;
			const location = place(decls, line, options.scope);
			if (location.inScope === true) {
				scan.linesInScope += 1;
			}
			if (options.onlyInScope === true && location.inScope === false) {
				continue;
			}
			for (const [label, re] of options.patterns) {
				if (!re.test(content)) {
					continue;
				}
				scan.matched += 1;
				scan.counts.set(label, (scan.counts.get(label) ?? 0) + 1);
				if (scan.hints.length >= (options.maxHints ?? 40)) {
					break;
				}
				scan.hints.push({
					file: path,
					line,
					pattern: label,
					context: content.trim().slice(0, 160),
					flags: {
						enclosing: location.enclosing,
						supertypes: location.supertypes,
						inScope: location.inScope ?? "unknown",
					},
				});
				break;
			}
		}
	}
	return scan;
}

/** Matching lines of one pattern label, shown or not. */
export function countLabel(scan: { counts: ReadonlyMap<string, number> }, label: string): number {
	return scan.counts.get(label) ?? 0;
}

/**
 * Says that the rows are a sample when they are, so that nothing read off them — a location, a
 * missing kind — is taken for the whole change.
 */
export function sampleNote(scan: { matched: number; hints: readonly unknown[] }): string[] {
	const omitted = scan.matched - scan.hints.length;
	return omitted > 0
		? [
				`${String(scan.matched)} matching occurrence(s); the first ${String(scan.hints.length)} are listed and ${String(omitted)} are not. Counts by kind cover every line; anything read off the listed rows alone covers only them.`,
			]
		: [];
}

/** Search the assigned source files in full, then apply the review's occurrence selection. */
export async function scanOccurrences(
	inputs: PracticeInputs,
	files: Files | null,
	options: SourceScanOptions,
) {
	const scan = {
		hints: [] as (ScanHint & { endLine: number })[],
		matched: 0,
		counts: new Map<string, number>(),
		filesScanned: 0,
		linesSelected: 0,
		linesInScope: 0,
		filesWithUnknownLocation: 0,
	};
	for (const file of await selectedSources(inputs, files)) {
		if (file.language === null || !options.languages.includes(file.language)) {
			continue;
		}
		if (options.includeTests !== true && isTestPath(file.path)) {
			continue;
		}
		scan.filesScanned += 1;
		const selected = selectedOccurrences(
			inputs,
			files,
			distinctOccurrences(
				occurrences(
					file,
					options.patterns.map(([label, test]) => ({ label, test })),
				),
			),
		);
		const placed = placeOccurrences(file, selected, options);
		scan.linesSelected +=
			inputs.scope.kind === "SCAN"
				? file.text.split("\n").length
				: (files?.change?.files.get(file.path)?.addedLines.size ?? 0);
		if (placed.some((row) => row.facts.enclosing === "unknown")) {
			scan.filesWithUnknownLocation += 1;
		}
		for (const row of placed) {
			scan.matched += 1;
			scan.counts.set(row.label, (scan.counts.get(row.label) ?? 0) + 1);
			if (row.facts.inScope === true) {
				scan.linesInScope += 1;
			}
			if (scan.hints.length < (options.maxHints ?? 40)) {
				scan.hints.push({
					file: row.path,
					line: row.startLine,
					endLine: row.endLine,
					pattern: row.label,
					context: file.text.split("\n")[row.startLine - 1]?.trim().slice(0, 160) ?? "",
					flags: { ...row.facts },
				});
			}
		}
	}
	return scan;
}
