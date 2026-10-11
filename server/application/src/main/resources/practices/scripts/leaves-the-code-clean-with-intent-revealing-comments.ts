// Finds residue that the change adds: debug output, TODO markers, notes about the editing session,
// template placeholders and near-empty new files. A table of debug-output calls for each language,
// keyed by the file extension, finds the debug output. Each lead is a place to read; the review
// decides if it is residue or a deliberate line.
import { languageOf } from "../lib/languages.ts";
import { definePracticeScript, expectKind, type Lead } from "../lib/practice-script.ts";

// language key -> [human label, regex] of debug-output / residue constructs that, when ADDED, are worth a look.
const LANG_PATTERNS: Record<string, [string, RegExp][]> = {
	swift: [
		["print(", /(?:^|[^.\w])print\s*\(/u],
		["debugPrint(", /\bdebugPrint\s*\(/u],
		["NSLog(", /\bNSLog\s*\(/u],
		["dump(", /(?:^|[^.\w])dump\s*\(/u],
	],
	typescript: [
		["console.*", /\bconsole\.(?:log|debug|info|warn|error|trace)\s*\(/u],
		["debugger", /\bdebugger\b/u],
	],
	javascript: [
		["console.*", /\bconsole\.(?:log|debug|info|warn|error|trace)\s*\(/u],
		["debugger", /\bdebugger\b/u],
	],
	python: [
		["print(", /(?:^|[^.\w])print\s*\(/u],
		["breakpoint(", /\bbreakpoint\s*\(/u],
		["pprint(", /\bpprint\s*\(/u],
	],
	java: [
		["System.out/err.print", /\bSystem\.(?:out|err)\.print/u],
		["printStackTrace(", /\.printStackTrace\s*\(/u],
	],
	kotlin: [["println(", /\bprintln\s*\(/u]],
	go: [
		["fmt.Print*", /\bfmt\.Print[a-z]*\s*\(/u],
		["println(", /(?:^|[^.\w])println\s*\(/u],
	],
	ruby: [["puts/p/pp", /(?:^|[^.\w])(?:puts|pp?)\s+["'\d:@]/u]],
	rust: [["println!/dbg!/eprintln!", /\b(?:println|eprintln|dbg|print|eprint)\s*!/u]],
};

// A TODO/FIXME/XXX/HACK marker added in the diff — a residue signal across all languages.
const TODO_MARKER = /\b(?:TODO|FIXME|XXX|HACK)\b/u;

// A comment that narrates the editing session rather than the code, on any language's comment line.
const PROCESS_NOTE =
	/^\s*(?:\/\/|#|\*)\s*(?:FIX(?:ED)?\s*\d*\s*:|CHANGED?\s*:|ADDED\s*:|MOVED\s*:|UPDATED?\s*:|REMOVED?\s*:|NEW\s*:)/iu;
// A template placeholder left in a manifest the change edited.
const TEMPLATE_PLACEHOLDER = /#\s*TODO:\s*Adjust|<your[- ]|REPLACE[_ ]ME|CHANGE[_ ]ME/iu;

/** The leads that the script lists. */
const LISTED = 40;

export default definePracticeScript({
	meta: {
		kinds: {
			"debug-output":
				"An added call that writes debug output, such as print, console.log or println.",
			"todo-fixme-marker": "An added line with a TODO, FIXME, XXX or HACK marker.",
			"editing-process-note":
				"An added comment that tells about the editing session, such as FIXED: or CHANGED:.",
			"template-placeholder": "An added template placeholder, such as <your-name> or REPLACE_ME.",
			"near-empty-new-file": "A new text or Markdown file with one or two lines of content.",
		},
	},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const leads: Lead[] = [];
		const byLang: Record<string, number> = {};
		let debugCandidates = 0;
		let todoCandidates = 0;
		let processNotes = 0;
		let placeholders = 0;
		let scratchFiles = 0;

		for (const [path, df] of ctx.change) {
			const lang = languageOf(path);
			const patterns = lang === null ? [] : (LANG_PATTERNS[lang] ?? []);
			// A file added with a line or two of content and no language is a scratch file until read.
			const added = [...df.addedLines.values()].filter((l) => l.trim().length > 0);
			const firstLine = [...df.addedLines.keys()][0];
			if (
				lang === null &&
				df.removedLines.size === 0 &&
				firstLine !== undefined &&
				added.length > 0 &&
				added.length <= 2 &&
				/\.(?:txt|md)$/iu.test(path) &&
				!/README|CHANGELOG|LICENSE/iu.test(path)
			) {
				leads.push({ at: { change: path, line: firstLine }, kind: "near-empty-new-file" });
				scratchFiles += 1;
			}
			for (const [line, text] of df.addedLines) {
				const at = { change: path, line };
				// Skip lines that are themselves comments — a debug call inside a comment is not live residue.
				const isComment = /^(?:\/\/|#|\*|\/\*)/u.test(text.trim());
				// One debug-output lead per added line, as the sibling validates-inputs script does.
				const debugOutput = isComment ? undefined : patterns.find(([, re]) => re.test(text));
				if (debugOutput !== undefined) {
					leads.push({ at, kind: "debug-output", facts: { call: debugOutput[0] } });
					debugCandidates += 1;
					if (lang !== null) {
						byLang[lang] = (byLang[lang] ?? 0) + 1;
					}
				}
				if (TODO_MARKER.test(text)) {
					leads.push({ at, kind: "todo-fixme-marker" });
					todoCandidates += 1;
				}
				if (PROCESS_NOTE.test(text)) {
					leads.push({ at, kind: "editing-process-note" });
					processNotes += 1;
				}
				if (TEMPLATE_PLACEHOLDER.test(text)) {
					leads.push({ at, kind: "template-placeholder" });
					placeholders += 1;
				}
			}
		}

		const directions: string[] = [];
		if (debugCandidates > 0) {
			directions.push(
				`Found ${String(debugCandidates)} debug-output call(s) on added lines (print/console/log-style) — investigate whether each is leftover debug residue or a deliberate, intent-revealing log.`,
			);
		}
		if (todoCandidates > 0) {
			directions.push(
				`Found ${String(todoCandidates)} TODO/FIXME/XXX/HACK marker(s) added — investigate whether each is actionable residue or an intentional, tracked note.`,
			);
		}
		if (processNotes + placeholders + scratchFiles > 0) {
			directions.push(
				`Found ${String(processNotes)} comment(s) narrating the editing session, ${String(placeholders)} template placeholder(s) and ${String(scratchFiles)} near-empty new file(s) — residue of the writing process unless the surrounding text says otherwise.`,
			);
		}
		return {
			leads: leads.slice(0, LISTED),
			facts: {
				debugCandidates,
				todoCandidates,
				processNotes,
				placeholders,
				scratchFiles,
				...byLang,
			},
			directions,
		};
	},
});
