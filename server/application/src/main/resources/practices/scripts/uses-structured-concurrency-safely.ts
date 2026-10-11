// Finds each concurrency construct that the change adds to Swift files, with the type that encloses
// it, and the lines of the project files that name a default actor isolation. The review decides
// from the checkout whether the work must outlive the view and where each mutation runs.
import { readFile } from "node:fs/promises";
import path from "node:path";

import { factsOf, noMatchDirections } from "../lib/bounded-text.ts";
import { globFilesSync } from "../lib/files.ts";
import { definePracticeScript, expectKind, type Lead } from "../lib/practice-script.ts";
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

/**
 * The lines of the first few project files that name a default actor isolation. The setting belongs
 * to one target or SwiftPM module and build configuration, and a line can be commented out. Thus, a
 * line found here is a place to read, not the isolation of a changed type.
 */
async function defaultIsolationLines(repoPath: string): Promise<{ file: string; line: number }[]> {
	let files: string[];
	try {
		files = [
			...globFilesSync("**/project.yml", repoPath),
			...globFilesSync("**/project.yaml", repoPath),
			...globFilesSync("**/*.xcodeproj/project.pbxproj", repoPath),
			...globFilesSync("**/Package.swift", repoPath),
		].filter((f) => !/(?:^|\/)(?:Pods|\.build|DerivedData|Carthage)\//u.test(f));
	} catch {
		return [];
	}
	const found: { file: string; line: number }[] = [];
	for (const file of files.slice(0, 20)) {
		let text: string;
		try {
			text = await readFile(path.join(repoPath, file), "utf8");
		} catch {
			continue;
		}
		for (const [index, line] of text.split(/\r?\n/u).entries()) {
			if (DEFAULT_ISOLATION.test(line)) {
				found.push({ file, line: index + 1 });
			}
		}
	}
	return found.slice(0, 10);
}

export default definePracticeScript({
	meta: {
		version: 1,
		kinds: {
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
		},
	},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const scan = await scanAddedLines(ctx.repo, ctx.change, {
			languages: ["swift"],
			patterns: CONCURRENCY,
			scope: SWIFTUI_VIEW,
			maxHints: 60,
		});
		const leads: Lead[] = scan.hints.map((hint) => ({
			at: { change: hint.file, line: hint.line },
			kind: hint.pattern,
			facts: factsOf(hint.flags),
		}));
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
			const settings = await defaultIsolationLines(ctx.repo);
			leads.push(...settings.map((at): Lead => ({ at, kind: "default-isolation-setting" })));
			directions.push(
				settings.length > 0
					? "Each default-isolation-setting lead applies only to its own target or SwiftPM module and build configuration, and a commented line sets nothing: read which target and configuration build the changed file before relying on any of them."
					: "No default actor isolation text was found in the project files read, which leaves the module's default unknown rather than nonisolated.",
				"A type's isolation comes from its declaration, its module's setting or what it inherits. A missing @MainActor on an added line does not by itself put a mutation off the main actor.",
				"Check the SDK before relying on inherited SwiftUI View isolation. Swallowed failures belong to handles-errors-instead-of-swallowing-them, not this practice.",
			);
		}
		return { leads, facts, directions };
	},
});
