// Finds the added lines that spell a crash operator that the author wrote, in all languages: a force
// unwrap, `try!`, a force cast or an abort. Each lead tells if the line is inside a preview block or in
// a test file. A lead matches spelling on one line. It does not know the type of the operand, where the
// value came from, or what bounds it, so the review decides which leads are lapses. The input practice
// owns implicit traps, such as a subscript or an integer division. To add a language, add a row.
import { DIRECTION_CHARS, listWithin, oneLine } from "../lib/bounded-text.ts";
import { blockExtents, codeLines, isCommentLine } from "../lib/declarations.ts";
import { isTestPath, languageOf } from "../lib/languages.ts";
import {
	definePracticeScript,
	expectKind,
	requireChange,
	type DiffFile,
	type FileTree,
	type ReviewLead,
} from "../lib/practice-script.ts";

export const LABELS = {
	"crash-operator":
		"An added line that spells a crash operator; its facts name the language, the operators, and if it runs in a preview block or a test file.",
};

// language key -> [label, regex] of crash operators the author writes, matched against the line's code.
const LANG_PATTERNS: Record<string, [string, RegExp][]> = {
	swift: [
		["try!", /\btry!/u],
		["fatalError", /\bfatalError\s*\(/u],
		["force cast as!", /\bas!\s/u],
		[
			"precondition or assert",
			/\b(?:precondition|preconditionFailure|assert|assertionFailure)\s*\(/u,
		],
		["exit or abort", /\b(?:exit|abort)\s*\(/u],
		// `try!` and `as!` have their own rows.
		["force unwrap", /[A-Za-z0-9_)\]](?<!\btry|\bas)!(?:[.\s),\]]|$)/u],
	],
	typescript: [
		["process.exit", /\bprocess\.exit\s*\(/u],
		["non-null assertion", /[A-Za-z0-9_)\]]!(?:[.;)\s,\]]|$)/u],
	],
	javascript: [["process.exit", /\bprocess\.exit\s*\(/u]],
	python: [
		["sys.exit", /\bsys\.exit\s*\(/u],
		["os._exit", /\bos\._exit\s*\(/u],
		["raise SystemExit", /\braise\s+SystemExit\b/u],
		["bare assert in code", /^\s*assert\s+/u],
	],
	go: [
		["panic(", /\bpanic\s*\(/u],
		["log.Fatal", /\blog\.Fatal[a-z]*\s*\(/u],
		["os.Exit", /\bos\.Exit\s*\(/u],
	],
	java: [
		["System.exit", /\bSystem\.exit\s*\(/u],
		["throw AssertionError", /\bthrow\s+new\s+AssertionError\b/u],
		["Optional.get()", /\bOptional[^;]*\.get\s*\(\s*\)/u],
	],
	kotlin: [
		["!! force non-null", /!!(?:[.\s)]|$)/u],
		["error(", /(?:^|[^.\w])error\s*\(/u],
		["TODO(", /\bTODO\s*\(/u],
	],
	rust: [
		[".unwrap()", /\.unwrap\s*\(\s*\)/u],
		[".expect(", /\.expect\s*\(/u],
		["panic!", /\bpanic!\s*\(/u],
		["unreachable!", /\bunreachable!\s*\(/u],
		["unimplemented!/todo!", /\b(?:unimplemented|todo)!\s*\(/u],
	],
	ruby: [
		["exit!", /\bexit!/u],
		["abort", /\babort\b/u],
	],
	c: [
		["abort(", /\babort\s*\(/u],
		["exit(", /\bexit\s*\(/u],
		["assert(", /\bassert\s*\(/u],
	],
	// Objective-C is C with Cocoa's assertion macros, not Swift: no bang operators to find.
	"objective-c": [
		["abort(", /\babort\s*\(/u],
		["exit(", /\bexit\s*\(/u],
		["NSAssert", /\bNS(?:C)?Assert\s*\(/u],
	],
	csharp: [
		["Environment.Exit", /\bEnvironment\.Exit\s*\(/u],
		["Environment.FailFast", /\bEnvironment\.FailFast\s*\(/u],
		["Debug.Assert", /\bDebug\.Assert\s*\(/u],
		["null-forgiving !", /[A-Za-z0-9_)\]]!\.(?=[A-Za-z_])/u],
	],
};

// Code that runs only when Xcode renders a preview, never on a user's device.
const PREVIEW_OPENER = /^\s*(?:#Preview\b|(?:\w+\s+)*struct\s+\w+\s*:\s*PreviewProvider\b)/u;

/** The new side as far as the hunks show it: context and added lines at their numbers, gaps empty. */
function newSideOfHunks(file: DiffFile): string {
	const lines: string[] = [];
	for (const hunk of file.hunks) {
		let number = hunk.newStart;
		for (const line of hunk.lines) {
			if (line.startsWith("-")) {
				continue;
			}
			while (lines.length < number - 1) {
				lines.push("");
			}
			lines[number - 1] = line.slice(1);
			number += 1;
		}
	}
	return lines.join("\n");
}

/** The text of the pinned file, or null when the tree is unknown or does not hold the file as text. */
async function pinnedText(tree: FileTree | null, path: string): Promise<string | null> {
	if (tree === null) {
		return null;
	}
	try {
		const file = await tree.read(path);
		return file.status === "FOUND" ? file.text : null;
	} catch {
		return null;
	}
}

/** The lines inside a preview block: read from the pinned file, else from what the hunks show. */
async function previewLines(
	tree: FileTree | null,
	file: DiffFile,
	language: string,
): Promise<Set<number>> {
	const source = (await pinnedText(tree, file.path)) ?? newSideOfHunks(file);
	const lines = new Set<number>();
	for (const { start, end } of blockExtents(language, source, PREVIEW_OPENER) ?? []) {
		for (let line = start; line <= end; line += 1) {
			lines.add(line);
		}
	}
	return lines;
}

/** One added line that spells a crash operator, with where it runs. */
interface CrashLine {
	path: string;
	line: number;
	language: string;
	operators: string;
	inPreview: boolean;
	testFile: boolean;
}

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const rows: CrashLine[] = [];
		const byKind: Record<string, number> = {};
		for (const [path, df] of requireChange(ctx.files)) {
			const lang = languageOf(path);
			const patterns = lang === null ? undefined : LANG_PATTERNS[lang];
			if (lang === null || !patterns) {
				continue;
			}
			const code = codeLines(lang, df.addedLines);
			const preview =
				lang === "swift"
					? await previewLines(ctx.files?.head ?? null, df, lang)
					: new Set<number>();
			const testFile = isTestPath(path);
			for (const [line, content] of df.addedLines) {
				if (isCommentLine(content, lang)) {
					continue;
				}
				const labels = patterns
					.filter(([, re]) => re.test(code.get(line) ?? ""))
					.map(([name]) => name);
				if (labels.length === 0) {
					continue;
				}
				for (const label of labels) {
					byKind[label] = (byKind[label] ?? 0) + 1;
				}
				rows.push({
					path,
					line,
					language: lang,
					operators: labels.join(" + "),
					inPreview: preview.has(line),
					testFile,
				});
			}
		}
		// Rows on an ordinary path first, so preview and test rows cannot push them out of the leads.
		rows.sort((a, b) => Number(a.inPreview || a.testFile) - Number(b.inPreview || b.testFile));
		const inPreview = rows.filter((row) => row.inPreview).length;
		const inTests = rows.filter((row) => row.testFile).length;
		const kinds = Object.entries(byKind).map(([kind, count]) => `${String(count)} ${kind}`);
		const directions =
			rows.length > 0
				? [
						oneLine(
							`${rows.length} added line(s) spell a crash operator: ${listWithin(kinds, DIRECTION_CHARS - 60)}.`,
							DIRECTION_CHARS,
						),
						`${inPreview} sit inside a preview block and ${inTests} in test files. String-literal text and comments were not scanned.`,
						"A lead matches spelling on one line: it does not know the operand's type, where the value came from, or what bounds it.",
					]
				: [];
		const leads: ReviewLead[] = rows.slice(0, 40).map((row) => ({
			at: { change: row.path, line: row.line },
			label: LABELS["crash-operator"],
			facts: {
				language: row.language,
				operators: row.operators,
				inPreview: row.inPreview,
				testFile: row.testFile,
			},
		}));
		return ctx.review({
			leads,
			facts: { crashOperatorLines: rows.length, inPreview, inTests },
			directions,
		});
	},
});
