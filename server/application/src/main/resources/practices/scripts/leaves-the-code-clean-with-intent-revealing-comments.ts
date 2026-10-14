import { scm } from "../kits/scm/index.ts";
import {
	occurrences,
	selectedSources,
	selectedOccurrences,
	occurrenceLead,
	sortOccurrenceLeads,
} from "../kits/scm/occurrences.ts";
// Finds residue that the change adds: debug output, TODO markers, notes about the editing session,
// template placeholders and near-empty new files. A table of debug-output calls for each language,
// keyed by the file extension, finds the debug output. Each lead is a place to read; the review
// decides if it is residue or a deliberate line.
import { definePracticeScript, type ReviewLead } from "../lib/core/practice-script.ts";

export const LABELS = {
	"debug-output":
		"A selected call that writes debug output, such as print, console.log or println.",
	"todo-fixme-marker": "A selected line with a TODO, FIXME, XXX or HACK marker.",
	"editing-process-note":
		"A selected comment that tells about the editing session, such as FIXED: or CHANGED:.",
	"template-placeholder": "A selected template placeholder, such as <your-name> or REPLACE_ME.",
	"near-empty-new-file": "A new text or Markdown file with one or two lines of content.",
};

// language key -> [human label, regex] of debug-output / residue constructs that, when selected, are worth a look.
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

// A TODO/FIXME/XXX/HACK marker selected in the diff — a residue signal across all languages.
const TODO_MARKER = /\b(?:TODO|FIXME|XXX|HACK)\b/u;

// A comment that narrates the editing session rather than the code, on any language's comment line.
const PROCESS_NOTE =
	/^\s*(?:\/\/|#|\*)\s*(?:FIX(?:ED)?\s*\d*\s*:|CHANGED?\s*:|selected\s*:|MOVED\s*:|UPDATED?\s*:|REMOVED?\s*:|NEW\s*:)/imu;
// A template placeholder left in a manifest the change edited.
const TEMPLATE_PLACEHOLDER = /#\s*TODO:\s*Adjust|<your[- ]|REPLACE[_ ]ME|CHANGE[_ ]ME/iu;

/** The leads that the script lists. */
const LISTED = 40;

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		if (domain.kind !== "scm.pull_request" && domain.kind !== "scm.file") {
			throw new Error("this practice requires SCM source files");
		}
		const leads: ReviewLead[] = [];
		const byLang: Record<string, number> = {};
		let debugCandidates = 0;
		let todoCandidates = 0;
		let processNotes = 0;
		let placeholders = 0;

		for (const file of await selectedSources(ctx, domain.files)) {
			const lang = file.language;
			if (lang === null) {
				continue;
			}
			const debug = selectedOccurrences(
				ctx,
				domain.files,
				occurrences(
					file,
					(LANG_PATTERNS[lang] ?? []).map(([_call, test]) => ({
						label: LABELS["debug-output"],
						test,
					})),
				).map((row) => ({ ...row, facts: { ...row.facts, call: row.label } })),
			);
			leads.push(...debug.map(occurrenceLead));
			debugCandidates += debug.length;
			byLang[lang] = (byLang[lang] ?? 0) + debug.length;
			for (const [kind, test] of [
				["todo-fixme-marker", TODO_MARKER],
				["editing-process-note", PROCESS_NOTE],
				["template-placeholder", TEMPLATE_PLACEHOLDER],
			] as const) {
				const found = selectedOccurrences(
					ctx,
					domain.files,
					occurrences(file, [{ label: LABELS[kind], test }], { includeComments: true }),
				);
				leads.push(...found.map(occurrenceLead));
				if (kind === "todo-fixme-marker") {
					todoCandidates += found.length;
				}
				if (kind === "editing-process-note") {
					processNotes += found.length;
				}
				if (kind === "template-placeholder") {
					placeholders += found.length;
				}
			}
		}
		const directions: string[] = [];
		if (debugCandidates > 0) {
			directions.push(
				`Found ${String(debugCandidates)} debug-output call(s) on selected lines (print/console/log-style) — investigate whether each is leftover debug residue or a deliberate, intent-revealing log.`,
			);
		}
		if (todoCandidates > 0) {
			directions.push(
				`Found ${String(todoCandidates)} TODO/FIXME/XXX/HACK marker(s) selected — investigate whether each is actionable residue or an intentional, tracked note.`,
			);
		}
		if (processNotes + placeholders > 0) {
			directions.push(
				`Found ${String(processNotes)} comment(s) narrating the editing session, ${String(placeholders)} template placeholder(s) — residue of the writing process unless the surrounding text says otherwise.`,
			);
		}
		return ctx.review({
			leads: sortOccurrenceLeads(leads).slice(0, LISTED),
			facts: {
				debugCandidates,
				todoCandidates,
				processNotes,
				placeholders,
				...byLang,
			},
			directions,
		});
	},
});
