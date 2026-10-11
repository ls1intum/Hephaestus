// Finds the added code that can discard or swallow an error, in all languages. Each lead is a
// candidate: the review decides if the error is really lost, with no report, log or recovery. An empty
// catch body often spans lines, so the patterns run over each block of consecutive added lines of a
// file, not over one line. To add a language, add a row.
import { noMatchDirections } from "../lib/bounded-text.ts";
import { isCommentLine } from "../lib/declarations.ts";
import { languageOf } from "../lib/languages.ts";
import { definePracticeScript, expectKind, type Lead } from "../lib/practice-script.ts";

const KINDS = {
	"try-discards-error": "A Swift `try?` that turns a thrown error into nil.",
	"empty-catch": "A catch block with an empty body.",
	"catch-with-only-print": "A Swift catch block whose only statement is a print call.",
	"error-ignored-if-let-error": "A Swift `if let error` block with an empty body.",
	"fallback-on-a-failable-conversion":
		"A `??` fallback on a failable conversion, which the criteria ask to classify.",
	"guard-else-return":
		"A Swift guard whose else branch only returns, which the criteria ask to classify.",
	"catch-noop": "A promise `.catch` whose handler has an empty body.",
	"catch-with-only-console": "A catch block whose only statement is a console call.",
	"except-pass": "A Python except clause whose body is only `pass`.",
	"bare-except": "A Python except clause that names no exception type.",
	"except-continue": "A Python except clause whose body is only `continue`.",
	"blank-identifier-assignment":
		"A Go assignment of a call result to the blank identifier, which can drop an error.",
	"empty-if-err-not-nil": "A Go `if err != nil` block with an empty body.",
	"catch-print-stack-trace-only": "A Java catch block whose only statement is printStackTrace.",
	"run-catching-get-or-null": "A Kotlin runCatching block that ends in getOrNull.",
	"ok-discards-err": "A Rust `.ok();` statement that drops the Err value.",
	"let-underscore-call": "A Rust `let _ =` binding of a call result.",
	"empty-if-let-err": "A Rust `if let Err(_)` block with an empty body.",
	"rescue-unused-binding": "A Ruby rescue that binds the error and ends the line.",
	"rescue-end": "A Ruby rescue that ends with no statement.",
};
type ErrorKind = keyof typeof KINDS;

// language key -> [kind, regex] of constructs in ADDED code that discard or swallow an error. The `\s`
// in each regex spans the newlines joined into a window, so multi-line empty bodies match.
const LANG_PATTERNS: Record<string, [ErrorKind, RegExp][]> = {
	swift: [
		["try-discards-error", /\btry\?\s/u],
		["empty-catch", /\bcatch\s*\{\s*\}/u],
		["catch-with-only-print", /\bcatch\s*\{\s*print\s*\([^)]*\)\s*\}/u],
		["error-ignored-if-let-error", /\bif\s+let\s+error\b[^{]*\{\s*\}/u],
		// Constructs the criteria ask to classify, not swallows in themselves: a fallback on a failable
		// conversion and a bare early return are where a failure is most often dropped quietly.
		[
			"fallback-on-a-failable-conversion",
			/\b(?:Int|Double|Float|Decimal|Bool|URL|UUID)\s*\([^)]*\)\s*\?\?/u,
		],
		["guard-else-return", /\bguard\b[^{]*\belse\s*\{\s*return\s*\}/u],
	],
	typescript: [
		["empty-catch", /\bcatch\s*(?:\([^)]*\))?\s*\{\s*\}/u],
		["catch-noop", /\.catch\s*\(\s*\(\s*[^)]*\)\s*=>\s*\{\s*\}\s*\)/u],
		[
			"catch-with-only-console",
			/\bcatch\s*(?:\([^)]*\))?\s*\{\s*console\.[a-z]+\([^)]*\)\s*;?\s*\}/u,
		],
	],
	javascript: [
		["empty-catch", /\bcatch\s*(?:\([^)]*\))?\s*\{\s*\}/u],
		["catch-noop", /\.catch\s*\(\s*\(\s*[^)]*\)\s*=>\s*\{\s*\}\s*\)/u],
		[
			"catch-with-only-console",
			/\bcatch\s*(?:\([^)]*\))?\s*\{\s*console\.[a-z]+\([^)]*\)\s*;?\s*\}/u,
		],
	],
	python: [
		["except-pass", /\bexcept[^:]*:\s*pass\b/u],
		["bare-except", /^\s*except\s*:/mu],
		["except-continue", /\bexcept[^:]*:\s*continue\b/u],
	],
	go: [
		[
			"blank-identifier-assignment",
			/\b_\s*(?:,\s*[a-zA-Z0-9_]+)?\s*:?=\s*[a-zA-Z_][a-zA-Z0-9_.]*\s*\(/u,
		],
		["empty-if-err-not-nil", /\bif\s+err\s*!=\s*nil\s*\{\s*\}/u],
	],
	java: [
		["empty-catch", /\bcatch\s*\([^)]*\)\s*\{\s*\}/u],
		[
			"catch-print-stack-trace-only",
			/\bcatch\s*\([^)]*\)\s*\{\s*[a-zA-Z0-9_.]*\.printStackTrace\s*\(\s*\)\s*;?\s*\}/u,
		],
	],
	kotlin: [
		["empty-catch", /\bcatch\s*\([^)]*\)\s*\{\s*\}/u],
		["run-catching-get-or-null", /runCatching\s*\{[\s\S]*?\}\s*\.getOrNull\s*\(\s*\)/u],
	],
	rust: [
		["ok-discards-err", /\.ok\s*\(\s*\)\s*;/u],
		["let-underscore-call", /\blet\s+_\s*=\s*[a-zA-Z_][a-zA-Z0-9_.:]*\s*\(/u],
		["empty-if-let-err", /\bif\s+let\s+Err\s*\(\s*_\s*\)[^{]*\{\s*\}/u],
	],
	ruby: [
		["rescue-unused-binding", /\brescue\s*=>\s*[a-z_]+\s*$/mu],
		["rescue-end", /\brescue\b[^;\n]*;\s*end\b/u],
	],
};

// Group a file's added lines into windows of CONSECUTIVE line numbers so a multi-line construct
// (e.g. `catch {` then `}`) is one searchable text block without falsely joining distant additions.
function consecutiveWindows(added: Map<number, string>): { start: number; lines: string[] }[] {
	const windows: { start: number; lines: string[] }[] = [];
	// The open window carries its own first and last line number, so "is there a window" and "what does
	// it end at" are one nullable value rather than length checks against a scratch array.
	let open: { start: number; end: number; lines: string[] } | null = null;
	for (const n of [...added.keys()].toSorted((a, b) => a - b)) {
		if (open !== null && n !== open.end + 1) {
			windows.push({ start: open.start, lines: open.lines });
			open = null;
		}
		open ??= { start: n, end: n, lines: [] };
		open.end = n;
		open.lines.push(added.get(n) ?? "");
	}
	if (open !== null) {
		windows.push({ start: open.start, lines: open.lines });
	}
	return windows;
}

export default definePracticeScript({
	meta: { version: 1, kinds: KINDS },
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const leads: Lead[] = [];
		const byLang: Record<string, number> = {};
		let filesScanned = 0;
		let linesAdded = 0;
		for (const [path, df] of ctx.change) {
			const lang = languageOf(path);
			if (lang === null) {
				continue;
			}
			const patterns = LANG_PATTERNS[lang];
			if (!patterns) {
				continue;
			}
			filesScanned += 1;
			linesAdded += df.addedLines.size;
			for (const w of consecutiveWindows(df.addedLines)) {
				const text = w.lines.join("\n");
				for (const [kind, re] of patterns) {
					const match = text.match(re);
					if (!match || match.index === undefined) {
						continue;
					}
					const offset = text.slice(0, match.index).split("\n").length - 1;
					const lineNum = w.start + offset;
					const lineContent = w.lines[offset] ?? "";
					if (isCommentLine(lineContent, lang)) {
						continue;
					}
					leads.push({ at: { change: path, line: lineNum }, kind, facts: { language: lang } });
					byLang[lang] = (byLang[lang] ?? 0) + 1;
					// One lead for each window: the review reads the full diff for the rest.
					break;
				}
			}
		}
		const directions =
			leads.length > 0
				? [
						`Found ${leads.length} possible error-discard constructs in ${Object.keys(byLang).length} languages. First establish whether each call can fail. For each failure, inspect whether it stays visible.`,
						"A valid fixed URL has no runtime-dependent parse failure. A later empty-value check does not swallow an error already recorded by its handler.",
					]
				: noMatchDirections(linesAdded, filesScanned);
		return {
			leads: leads.slice(0, 40),
			facts: { errorSwallowCandidates: leads.length, filesScanned, linesAdded, ...byLang },
			directions,
		};
	},
});
