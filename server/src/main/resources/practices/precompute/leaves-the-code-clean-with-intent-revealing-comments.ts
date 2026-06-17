// Precompute HINTS for leaves-the-code-clean-with-intent-revealing-comments. Surfaces facts only — the LLM
// judges whether each is real residue. On a large diff the model under-scans length and misses obvious
// debug traces / leftover markers sitting right there; this points it at the exact added lines. General by
// design: a per-language debug-output token table keyed off file extension. Adding a language = a row.
// CANDIDATES, never a verdict.
import type { DiffFile, PullRequestMetadata, Hint } from "../lib/types";

// language key -> [human label, regex] of debug-output / residue constructs that, when ADDED, are worth a look.
const LANG_PATTERNS: Record<string, Array<[string, RegExp]>> = {
	swift: [
		["print(", /(^|[^.\w])print\s*\(/],
		["debugPrint(", /\bdebugPrint\s*\(/],
		["NSLog(", /\bNSLog\s*\(/],
		["dump(", /(^|[^.\w])dump\s*\(/],
	],
	ts: [
		["console.*", /\bconsole\.(log|debug|info|warn|error|trace)\s*\(/],
		["debugger", /\bdebugger\b/],
	],
	js: [
		["console.*", /\bconsole\.(log|debug|info|warn|error|trace)\s*\(/],
		["debugger", /\bdebugger\b/],
	],
	python: [
		["print(", /(^|[^.\w])print\s*\(/],
		["breakpoint(", /\bbreakpoint\s*\(/],
		["pprint(", /\bpprint\s*\(/],
	],
	java: [
		["System.out/err.print", /\bSystem\.(out|err)\.print/],
		["printStackTrace(", /\.printStackTrace\s*\(/],
	],
	kotlin: [["println(", /\bprintln\s*\(/]],
	go: [
		["fmt.Print*", /\bfmt\.Print[a-z]*\s*\(/],
		["println(", /(^|[^.\w])println\s*\(/],
	],
	ruby: [
		["puts/p/pp", /(^|[^.\w])(puts|pp?)\s+["'\d:@]/],
	],
	rust: [
		["println!/dbg!/eprintln!", /\b(println|eprintln|dbg|print|eprint)\s*!/],
	],
};

// A TODO/FIXME/XXX/HACK marker added in the diff — a residue signal across all languages.
const TODO_MARKER = /\b(TODO|FIXME|XXX|HACK)\b/;

const EXT_TO_LANG: Record<string, string> = {
	swift: "swift",
	ts: "ts",
	tsx: "ts",
	js: "js",
	jsx: "js",
	mjs: "js",
	py: "python",
	java: "java",
	kt: "kotlin",
	go: "go",
	rb: "ruby",
	rs: "rust",
};

function langFor(path: string): string | null {
	const ext = (path.split(".").pop() ?? "").toLowerCase();
	return EXT_TO_LANG[ext] ?? null;
}

export default async function (_repo: string, diffFiles: Map<string, DiffFile>, _m: PullRequestMetadata) {
	const hints: Hint[] = [];
	const byLang: Record<string, number> = {};
	let debugCandidates = 0;
	let todoCandidates = 0;

	for (const [path, df] of diffFiles) {
		const lang = langFor(path);
		const patterns = lang ? LANG_PATTERNS[lang] ?? [] : [];
		for (const [lineNum, text] of df.addedLines) {
			// Skip lines that are themselves comments — a debug call inside a comment is not live residue.
			const trimmed = text.trim();
			const isComment = /^(\/\/|#|\*|\/\*)/.test(trimmed);
			for (const [label, re] of patterns) {
				if (!isComment && re.test(text)) {
					hints.push({ file: path, line: lineNum, pattern: label, context: trimmed.slice(0, 160), inDiff: true, flags: { kind: "debug-output" } });
					debugCandidates++;
					if (lang) byLang[lang] = (byLang[lang] ?? 0) + 1;
				}
			}
			if (TODO_MARKER.test(text)) {
				hints.push({ file: path, line: lineNum, pattern: "TODO/FIXME marker", context: trimmed.slice(0, 160), inDiff: true, flags: { kind: "marker" } });
				todoCandidates++;
			}
		}
	}

	const directions: string[] = [];
	if (debugCandidates > 0) {
		directions.push(
			`Found ${debugCandidates} debug-output call(s) on added lines (print/console/log-style) — investigate whether each is leftover debug residue or a deliberate, intent-revealing log.`,
		);
	}
	if (todoCandidates > 0) {
		directions.push(
			`Found ${todoCandidates} TODO/FIXME/XXX/HACK marker(s) added — investigate whether each is actionable residue or an intentional, tracked note.`,
		);
	}
	return { hints, metrics: { debugCandidates, todoCandidates, ...byLang }, directions };
}
