// Finds each concurrency construct that the change adds to Swift files, with the type that encloses
// it, and the lines of the project files that name a default actor isolation. The review decides
// from the checkout whether the work must outlive the view and where each mutation runs.
import { factsOf, noMatchDirections } from "../lib/bounded-text.ts";
import {
	definePracticeScript,
	expectKind,
	requireChange,
	type FileTree,
	type ReviewLead,
} from "../lib/practice-script.ts";
import { countLabel, sampleNote, scanAddedLines, type SourcePattern } from "../lib/source-scan.ts";

const SWIFTUI_VIEW = /\b(?:View|App|Scene)\b/u;

// The first pattern that matches a line gives its kind.
const CONCURRENCY: readonly SourcePattern[] = [
	["detached-task", /\bTask\.detached\b/u],
	["stored-task-handle", /(?:let|var)\s+\w+\s*(?::\s*Task<[^>]*>)?\s*=\s*Task\s*[{(]/u],
	["task-block", /\bTask\s*(?:\(priority:[^)]*\))?\s*\{/u],
	["task-modifier", /\.task\s*(?:\(id:[^)]*\))?\s*\{/u],
	["on-appear", /\.onAppear\s*\{/u],
	["dispatch-queue", /\bDispatchQueue\b/u],
	["main-actor-mark", /@MainActor\b/u],
	["main-actor-run", /\bMainActor\.run\b/u],
	["actor-declaration", /^\s*(?:\w+\s+)*actor\s+\w+/u],
	["async-function", /\bfunc\s+\w+[^{]*\basync\b/u],
	["await", /\bawait\b/u],
];

/** Text naming a default actor isolation: an Xcode build setting or a SwiftPM target setting. */
const DEFAULT_ISOLATION = /SWIFT_DEFAULT_ACTOR_ISOLATION\s*[:=]|defaultIsolation\s*\(/u;

const PROJECT_FILES = [
	"**/project.yml",
	"**/project.yaml",
	"**/*.xcodeproj/project.pbxproj",
	"**/Package.swift",
];

/**
 * The lines of the first few project files that name a default actor isolation. The setting belongs
 * to one target or SwiftPM module and build configuration, and a line can be commented out. Thus, a
 * line found here is a place to read, not the isolation of a changed type.
 */
async function defaultIsolationLines(
	tree: FileTree | null,
): Promise<{ file: string; line: number }[]> {
	if (tree === null) {
		return [];
	}
	const files: string[] = [];
	try {
		for (const glob of PROJECT_FILES) {
			const { paths } = await tree.list(glob);
			files.push(...paths);
		}
	} catch {
		return [];
	}
	const found: { file: string; line: number }[] = [];
	const projectFiles = files.filter(
		(f) => !/(?:^|\/)(?:Pods|\.build|DerivedData|Carthage)\//u.test(f),
	);
	for (const file of projectFiles.slice(0, 20)) {
		const source = await tree.read(file).catch(() => null);
		if (source?.status !== "FOUND") {
			continue;
		}
		for (const [index, line] of source.text.split(/\r?\n/u).entries()) {
			if (DEFAULT_ISOLATION.test(line)) {
				found.push({ file, line: index + 1 });
			}
		}
	}
	return found.slice(0, 10);
}

export const LABELS = {
	"detached-task": "An added Task.detached call.",
	"stored-task-handle": "An added property that stores a Task.",
	"task-block": "An added unstructured Task { } block.",
	"task-modifier": "An added .task view modifier.",
	"on-appear": "An added .onAppear block.",
	"dispatch-queue": "An added line that uses DispatchQueue.",
	"main-actor-mark": "An added @MainActor attribute.",
	"main-actor-run": "An added MainActor.run call.",
	"actor-declaration": "An added actor declaration.",
	"async-function": "An added async function declaration.",
	await: "An added await expression.",
	"default-isolation-setting":
		"A line of a project file that names a default actor isolation for one target or module.",
};
const LABEL_OF = new Map<string, string>(Object.entries(LABELS));

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const scan = await scanAddedLines(ctx.files?.head ?? null, requireChange(ctx.files), {
			languages: ["swift"],
			patterns: CONCURRENCY,
			scope: SWIFTUI_VIEW,
			maxHints: 60,
		});
		const leads: ReviewLead[] = scan.hints.flatMap((hint) => {
			const label = LABEL_OF.get(hint.pattern);
			return label === undefined
				? []
				: [{ at: { change: hint.file, line: hint.line }, label, facts: factsOf(hint.flags) }];
		});
		const count = (label: string) => countLabel(scan, label);
		const unstructuredInViews = scan.hints.filter(
			(hint) => hint.pattern === "task-block" && hint.flags.inScope === true,
		).length;
		const facts = {
			concurrencyLinesAdded: scan.matched,
			taskBlocks: count("task-block"),
			taskBlocksInListedViewRows: unstructuredInViews,
			taskModifiers: count("task-modifier"),
			detachedTasks: count("detached-task"),
			dispatchQueues: count("dispatch-queue"),
			mainActorMarks: count("main-actor-mark") + count("main-actor-run"),
			filesWithoutCheckout: scan.filesWithoutCheckout,
			filesScanned: scan.filesScanned,
			linesAdded: scan.linesAdded,
		};
		const directions: string[] = [...sampleNote(scan)];
		if (leads.length === 0) {
			directions.push(...noMatchDirections(scan.linesAdded, scan.filesScanned));
		}
		if (unstructuredInViews > 0) {
			directions.push(
				`${unstructuredInViews} Task { } block(s) added inside view types — read the lifetime each needs: work tied to the view belongs to a lifecycle that cancels it, while a Task started from a user action may legitimately finish after the view is gone.`,
			);
		}
		if (facts.detachedTasks + facts.dispatchQueues > 0) {
			directions.push(
				`${facts.detachedTasks} detached task(s) and ${facts.dispatchQueues} dispatch queue use(s) — read whether the code shows a need for independent execution or scheduling; no written reason is required when it does.`,
			);
		}
		if (scan.matched > 0) {
			const settings = await defaultIsolationLines(ctx.files?.head ?? null);
			leads.push(
				...settings.map((at): ReviewLead => ({ at, label: LABELS["default-isolation-setting"] })),
			);
			directions.push(
				settings.length > 0
					? "Each default-isolation-setting lead applies only to its own target or SwiftPM module and build configuration, and a commented line sets nothing: read which target and configuration build the changed file before relying on any of them."
					: "No default actor isolation text was found in the project files read, which leaves the module's default unknown rather than nonisolated.",
				"A type's isolation comes from its declaration, its module's setting or what it inherits. A missing @MainActor on an added line does not by itself put a mutation off the main actor.",
				"Check the SDK before relying on inherited SwiftUI View isolation. Swallowed failures belong to handles-errors-instead-of-swallowing-them, not this practice.",
			);
		}
		return ctx.review({ leads, facts, directions });
	},
});
