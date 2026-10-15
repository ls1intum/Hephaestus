import type { PracticeInputs } from "../../lib/core/contract.ts";
import type { Files } from "./index.ts";
import {
	occurrences,
	distinctOccurrences,
	placeOccurrences,
	selectedSources,
	selectedOccurrences,
} from "./occurrences.ts";

import { isTestPath } from "./languages.ts";

export type SourcePattern = [label: string, test: RegExp];

/** One added line that a pattern matched, with where it sits. */
export interface ScanHint {
	file: string;
	line: number;
	pattern: string;
	/** The trimmed line, at most 160 characters. */
	context: string;
	flags: Record<string, boolean | number | string>;
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

export function countLabel(scan: { counts: ReadonlyMap<string, number> }, label: string): number {
	return scan.counts.get(label) ?? 0;
}

// Label sampled rows so readers cannot mistake them for the whole change.
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
	const sources = await selectedSources(
		inputs,
		files,
		(file) =>
			file.language !== null &&
			options.languages.includes(file.language) &&
			(options.includeTests === true || !isTestPath(file.path)),
	);
	for (const file of sources) {
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
