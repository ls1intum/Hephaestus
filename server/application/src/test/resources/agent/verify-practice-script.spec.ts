import assert from "node:assert/strict";
import test from "node:test";

import type { DiffFile } from "../../../../../../docker/agents/practice-scripts/lib/types.ts";
import { runBundled } from "../../../../../../docker/agents/practice-scripts/run-bundled.ts";

const SLUG = "states-how-to-verify-the-change";

function changed(file: string, added: string[]): [string, DiffFile] {
	return [
		file,
		{
			path: file,
			addedLines: new Map(added.map((line, index) => [index + 1, line])),
			removedLines: new Map(),
			hunks: [],
		},
	];
}

const metadata = (body: string) => ({
	pr_number: 4,
	pr_url: "https://example.org/team/project/-/merge_requests/4",
	repository_full_name: "team/project",
	source_branch: "4-day-2-aom",
	target_branch: "main",
	body,
});

void test("a diagram-and-README change is named as an occasion-gate kind, with the empty testing heading called out", async () => {
	const result = await runBundled(SLUG, {
		change: new Map([
			changed("diagrams/aom.png", ["PNG"]),
			changed("README.md", ["![AOM](diagrams/aom.png)"]),
		]),
		work: metadata("## Description\n\nCloses #4\n\n## Testing Instructions\n\n<!-- steps -->\n"),
		context: { "linked_work_items.json": { workItems: [{}] } },
	});
	assert.equal(result.facts?.codeFiles, 0);
	assert.equal(result.facts.imageFiles, 1);
	assert.match(result.directions?.[0] ?? "", /^No code, test or project-configuration file/u);
	assert.match(
		result.directions?.[1] ?? "",
		/one of the kinds the criteria's Occasion section names/u,
	);
	assert.match((result.directions ?? []).join("\n"), /testing heading .* has no content/u);
	assert.match(
		(result.directions ?? []).join("\n"),
		/Issue\(s\) the author names: #4 \(closing keyword in the description, branch\)/u,
	);
});

void test("a code change with a preview and a filled testing section is reported as material with its sources", async () => {
	const result = await runBundled(SLUG, {
		change: new Map([
			changed("App/Views/QuizView.swift", ["struct QuizView: View {", "#Preview { QuizView() }"]),
			changed("App/Tests/QuizTests.swift", ["func testScore() {}"]),
		]),
		work: metadata(
			"## Testing Instructions\n\n1. Open the Quiz tab\n2. Answer three questions\n3. The score reads 3/3\n\n![result](/uploads/x.png)\n",
		),
	});
	assert.equal(result.facts?.codeFiles, 1);
	assert.equal(result.facts.testFiles, 1);
	assert.equal(result.facts.previewFiles, 1);
	assert.equal(result.facts.testingSectionLines, 4);
	assert.equal(result.facts.screenshotsInDescription, 1);
	assert.equal(
		result.directions?.[0],
		"Files with line changes: 1 code, 1 test, 0 project-configuration, 0 prose, 0 image.",
	);
	assert.match((result.directions ?? []).join("\n"), /holds 4 line\(s\) of author text/u);
	assert.match(
		(result.directions ?? []).join("\n"),
		/preview or story in: App\/Views\/QuizView\.swift\./u,
	);
});

void test("an empty range is named as such", async () => {
	const result = await runBundled(SLUG, { work: metadata("") });
	assert.match(result.directions?.[0] ?? "", /empty diff is one of the kinds/u);
});

/** The course template's Definition of Done, which shows a closing keyword as an example. */
const TEMPLATE = "## Definition of Done\n\n- [ ] Related issue is linked (e.g., `Closes #12`)\n";

void test("a closing keyword a template shows as an example adopts no issue, and the captured issue is named as not adopted", async () => {
	const context = {
		"linked_work_items.json": {
			workItems: [{ number: 12, title: "Accumulate points based on answer", body: "" }],
		},
	};
	const result = await runBundled(SLUG, {
		change: new Map([changed("Trivio/QuizView.swift", ["struct QuizView: View {"])]),
		work: { ...metadata(`Closes #<!-- issue number -->\n\n${TEMPLATE}`), source_branch: "branch1" },
		context,
	});
	const directions = (result.directions ?? []).join("\n");
	assert.doesNotMatch(directions, /adopts|author names/u);
	assert.match(
		directions,
		/Captured as linked_work_items\/<n>\.md but not named by a closing keyword, the title, the branch or a provider closing link: #12\./u,
	);
	assert.equal(result.facts?.namedIssues, 0);
});

void test("the author's own closing keyword is named beside the template's example", async () => {
	const context = {
		"linked_work_items.json": {
			workItems: [
				{ number: 12, title: "Save tiles permanently", body: "" },
				{ number: 25, title: "Show tiles on the route", body: "" },
			],
		},
	};
	const result = await runBundled(SLUG, {
		change: new Map([changed("App/TileView.swift", ["struct TileView: View {"])]),
		work: { ...metadata(`Closes #25\n\n${TEMPLATE}`), source_branch: "tiles" },
		context,
	});
	const directions = (result.directions ?? []).join("\n");
	assert.match(
		directions,
		/Issue\(s\) the author names: #25 \(closing keyword in the description\)\./u,
	);
	assert.match(directions, /not named by .*: #12\./u);
});

void test("a testing section of only N/A is stated as such, not as a section to judge, and an indented heading is found", async () => {
	const result = await runBundled(SLUG, {
		change: new Map([changed("App/HomeView.swift", ['AlertToast(type: .regular, title: "Hi")'])]),
		work: metadata(
			"  ## Description\n\nadded AlertToast package to home screen showing a pop up message on the top of the screen\n\n  ## Testing Instructions\n\nN/A\n",
		),
	});
	const directions = (result.directions ?? []).join("\n");
	assert.match(directions, /testing section \("## Testing Instructions"\) holds only "N\/A"\./u);
	assert.doesNotMatch(directions, /judge whether/u);
});

/** A file the diff moves or renames without changing a line. */
function moved(file: string): [string, DiffFile] {
	return [file, { path: file, addedLines: new Map(), removedLines: new Map(), hunks: [] }];
}

void test("moved files are counted apart from the files whose lines change, and no direction decides the gate", async () => {
	const result = await runBundled(SLUG, {
		change: new Map([
			...["Trivio/ContentView.swift", "Trivio/TrivioApp.swift", "Trivio/Info.plist"].map(moved),
			changed("Trivio/Tut1.swift", ["struct Tut1: View {", "#Preview { Tut1() }"]),
			changed("project.yml", ["name: Trivio"]),
			changed("diagrams/class.png", []),
		]),
		work: metadata(""),
	});
	assert.equal(
		result.directions?.[0],
		"Files with line changes: 1 code, 0 test, 1 project-configuration, 0 prose, 1 image.",
	);
	assert.match(
		result.directions[1] ?? "",
		/^3 file\(s\) change no line — moves, renames or binary files: Trivio\/ContentView\.swift, /u,
	);
	assert.doesNotMatch((result.directions ?? []).join("\n"), /applies|ENTRY/u);
});

void test("a key the code reads at runtime and an ignored secrets file are located, and a literal client id is not", async () => {
	const result = await runBundled(SLUG, {
		change: new Map([
			changed("Intrinsic/FMPClient.swift", [
				'let clientID = "b3f1c0ffee"',
				'if let secretsURL = Bundle.main.url(forResource: "secrets", withExtension: "plist"),',
			]),
			changed(".gitignore", ["secrets.plist"]),
			changed("App/ContentView.swift", [
				'Button(/*@START_MENU_TOKEN@*/"Button"/*@END_MENU_TOKEN@*/) {',
			]),
		]),
		work: metadata(""),
	});
	assert.deepEqual(result.leads, [
		{ at: { change: "App/ContentView.swift", line: 1 }, kind: "xcode-placeholder" },
		{ at: { change: "Intrinsic/FMPClient.swift", line: 2 }, kind: "runtime-secret-read" },
		{ at: { change: ".gitignore", line: 1 }, kind: "ignored-secret" },
	]);
	assert.equal(result.facts?.secretReadLines, 1);
});
