import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdir, mkdtemp, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { describe, it } from "node:test";

import type { DiffFile } from "./kits/scm/types.ts";
import { isJsonObject } from "./lib/core/json.ts";
import { isPracticeScriptDefinition, UnratedError } from "./lib/core/practice-script.ts";
import {
	event,
	requested,
	runBundled,
	runBundledProgram,
	runBundledReview,
	type RunInputs,
} from "./run-bundled.ts";
import { stagePracticeScripts } from "./stage.ts";

const KINDS = ["scm.pull_request", "scm.issue", "chat.thread", "docs.document"] as const;
type Kind = string;

const SCRIPTS_DIR = path.resolve(
	import.meta.dirname,
	"../../../server/application/src/main/resources/practices/scripts",
);

/** A path the diff touched. The scripts under test read the changed paths, not the hunk bodies. */
function changedFile(file: string): DiffFile {
	return { path: file, addedLines: new Map(), removedLines: new Map(), hunks: [] };
}

void describe("ships-tests-with-the-change", () => {
	const slug = "ships-tests-with-the-change";

	void it("classifies the changed source paths by convention, Xcode test target folders included", async () => {
		const paths = [
			"App/Latest.swift",
			"App/Contest.swift",
			"CourseUITests/Smoke.swift",
			"AppTests/ScoreTests.swift",
			"README.md",
		];
		const change = new Map(paths.map((file) => [file, changedFile(file)]));

		// No checkout at all: the question is answered from the diff, with no repository census.
		const result = await runBundled(slug, {
			repo: path.join(tmpdir(), `missing-checkout-${randomUUID()}`),
			change,
		});

		assert.deepEqual(result.facts, { diffProductionFiles: 2, diffTestFiles: 2 });
		assert.deepEqual(result.leads, []);
		assert.match(
			result.directions?.[0] ?? "",
			/^The diff changes 2 production source file\(s\) and 2 test file\(s\), by path: CourseUITests\/Smoke\.swift, AppTests\/ScoreTests\.swift\./u,
		);
	});

	void it("judges untested production code without a test target instead of abstaining", async () => {
		const change = new Map([["App/Quiz.swift", changedFile("App/Quiz.swift")]]);

		const result = await runBundled(slug, { change });

		assert.deepEqual(result.facts, { diffProductionFiles: 1, diffTestFiles: 0 });
		const directions = (result.directions ?? []).join(" ");
		assert.match(directions, /does not decide this practice: judge the changed behaviour/u);
		assert.doesNotMatch(directions, /cannot be assessed/u);
	});

	void it("says nothing about a change with no source file", async () => {
		const change = new Map([["README.md", changedFile("README.md")]]);

		const result = await runBundled(slug, { change });

		assert.deepEqual(result.directions, []);
	});

	void it("lists only as many test paths as one direction holds, and counts the rest", async () => {
		const paths = Array.from({ length: 30 }, (_, i) => `AppTests/Feature${String(i)}Tests.swift`);
		const change = new Map(paths.map((file) => [file, changedFile(file)]));

		const result = await runBundled(slug, { change });

		assert.equal(result.facts?.diffTestFiles, 30);
		assert.match(result.directions?.[0] ?? "", /, and \d+ more\.$/u);
	});
});

void describe("issue classification across practices", () => {
	const names = [
		"issue-has-checkable-outcome",
		"issue-states-an-actionable-problem",
		"issue-scoped-to-single-concern",
	];

	const opened = event("scm.issue.opened");

	for (const { name, metadata, emptyOrTitleEcho, hasDeliverableType, looksUmbrella } of [
		{
			name: "empty deliverable",
			metadata: { title: "Export billing reports", labels: ["FEATURE"] },
			emptyOrTitleEcho: 1,
			hasDeliverableType: 1,
			looksUmbrella: 0,
		},
		{
			name: "title repeated with different punctuation",
			metadata: {
				title: "Export billing reports to CSV",
				body: "EXPORT billing reports: to CSV!",
				issue_type: "Task",
			},
			emptyOrTitleEcho: 1,
			hasDeliverableType: 1,
			looksUmbrella: 0,
		},
		{
			name: "detailed body that begins with the title",
			metadata: {
				title: "Add sign-in",
				body: "Add sign-in so returning members can save a draft. Done when the draft survives reopening.",
				labels: ["type::User Story"],
			},
			emptyOrTitleEcho: 0,
			hasDeliverableType: 1,
			looksUmbrella: 0,
		},
		{
			name: "short concrete sentence",
			metadata: { title: "Invoice PDF", body: "Text no longer overlaps." },
			emptyOrTitleEcho: 0,
			hasDeliverableType: 0,
			looksUmbrella: 0,
		},
		{
			name: "non-Latin body that is not the non-Latin title",
			metadata: { title: "报告", body: "导出月度报告" },
			emptyOrTitleEcho: 0,
			hasDeliverableType: 0,
			looksUmbrella: 0,
		},
		{
			name: "substantive umbrella",
			metadata: {
				title: "Billing improvements",
				body: "Split the invoice work into independently deliverable child issues with their own acceptance criteria.",
				labels: ["REQUIREMENT"],
			},
			emptyOrTitleEcho: 0,
			hasDeliverableType: 1,
			looksUmbrella: 1,
		},
		{
			name: "substantive body with an omitted title",
			metadata: {
				body: "The PDF contains overlapping text when a customer's address spans more than three lines.",
				labels: ["BUG"],
			},
			emptyOrTitleEcho: 0,
			hasDeliverableType: 1,
			looksUmbrella: 0,
		},
		{
			name: "substantive body with an empty title",
			metadata: {
				title: "",
				body: "The PDF contains overlapping text when a customer's address spans more than three lines.",
				labels: ["BUG"],
			},
			emptyOrTitleEcho: 0,
			hasDeliverableType: 1,
			looksUmbrella: 0,
		},
		{
			name: "substantive body with a non-Latin title",
			metadata: {
				title: "报告",
				body: "The PDF contains overlapping text when a customer's address spans more than three lines.",
				labels: ["BUG"],
			},
			emptyOrTitleEcho: 0,
			hasDeliverableType: 1,
			looksUmbrella: 0,
		},
		{
			name: "substantive untyped issue",
			metadata: {
				title: "Unreadable invoice",
				body: "The PDF contains overlapping text when a customer's address spans more than three lines.",
			},
			emptyOrTitleEcho: 0,
			hasDeliverableType: 0,
			looksUmbrella: 0,
		},
	]) {
		void it(`preserves shared facts for a ${name}`, async () => {
			for (const scriptName of names) {
				const result = await runBundled(scriptName, { item: metadata, inputs: opened });
				assert.equal(result.facts?.emptyOrTitleEcho, emptyOrTitleEcho, scriptName);
				assert.deepEqual(result.leads, [], scriptName);
				if (scriptName !== "issue-scoped-to-single-concern") {
					assert.equal(result.facts.hasDeliverableType, hasDeliverableType, scriptName);
					assert.equal(result.facts.looksUmbrella, looksUmbrella, scriptName);
				}
				const said = (result.directions ?? []).join("\n");
				assert.equal(
					/body is empty|repeats the title and says nothing else/u.test(said),
					emptyOrTitleEcho === 1,
					`${scriptName}: ${name}`,
				);
				assert.doesNotMatch(said, /no quotable deliverable|nothing to build from/iu, scriptName);
			}
		});
	}

	const issue = { title: "Course checklist", body: "- [ ] Clarify\n- [ ] Reply\n- [ ] Defer" };
	// What a count is of is said only for a recorded source; a legacy or unrecognized one claims neither.
	const provenance = /as the provider reports|stored at capture|whole hierarchy/u;
	const subIssueCases: [
		Record<string, number | string | null>,
		RegExp,
		{ subIssuesTotal?: number; subIssuesCompleted?: number },
		RegExp,
	][] = [
		[{}, /sub-issue rollup not reported by the provider \(unknown, not zero\)/u, {}, provenance],
		[
			{ sub_issues_total: null, sub_issues_completed: null, sub_issues_source: "children" },
			/sub-issue rollup not reported by the provider \(unknown, not zero\)/u,
			{},
			provenance,
		],
		[
			{ sub_issues_total: 0, sub_issues_completed: 0 },
			/0 sub-issue\(s\), 0 completed/u,
			{ subIssuesTotal: 0, subIssuesCompleted: 0 },
			provenance,
		],
		[
			{ sub_issues_total: 3, sub_issues_completed: null },
			/3 sub-issue\(s\), completed count not reported/u,
			{ subIssuesTotal: 3 },
			provenance,
		],
		[
			{ sub_issues_total: 0, sub_issues_completed: 0, sub_issues_source: "provider" },
			/0 sub-issue\(s\), 0 completed, as the provider reports them/u,
			{ subIssuesTotal: 0, subIssuesCompleted: 0 },
			/stored at capture|whole hierarchy/u,
		],
		[
			{ sub_issues_total: 0, sub_issues_completed: 0, sub_issues_source: "children" },
			/0 sub-issue\(s\), 0 completed.*stored at capture/u,
			{ subIssuesTotal: 0, subIssuesCompleted: 0 },
			/as the provider reports|whole hierarchy|unknown/u,
		],
		[
			{ sub_issues_total: 2, sub_issues_completed: 2, sub_issues_source: "provider" },
			/2 sub-issue\(s\), 2 completed, as the provider reports them/u,
			{ subIssuesTotal: 2, subIssuesCompleted: 2 },
			/stored at capture|whole hierarchy/u,
		],
		[
			{ sub_issues_total: 3, sub_issues_completed: 1, sub_issues_source: "children" },
			/3 sub-issue\(s\), 1 completed.*stored at capture/u,
			{ subIssuesTotal: 3, subIssuesCompleted: 1 },
			/as the provider reports|whole hierarchy/u,
		],
		[
			{ sub_issues_total: 2, sub_issues_completed: 2, sub_issues_source: "children" },
			/2 sub-issue\(s\), 2 completed.*stored at capture/u,
			{ subIssuesTotal: 2, subIssuesCompleted: 2 },
			/as the provider reports/u,
		],
		[
			{ sub_issues_total: 2, sub_issues_completed: 2, sub_issues_source: "Children" },
			/2 sub-issue\(s\), 2 completed/u,
			{ subIssuesTotal: 2, subIssuesCompleted: 2 },
			provenance,
		],
	];

	void it("keeps sub-issue counts separate from their source and scope in each script that reads them", async () => {
		for (const { slug, checkboxes } of [
			{ slug: "breaks-large-work-into-trackable-subtasks", checkboxes: "taskCheckboxes" },
			{ slug: "issue-scoped-to-single-concern", checkboxes: "checkboxes" },
		]) {
			for (const [rollup, text, facts, unsaid] of subIssueCases) {
				const result = await runBundled(slug, { item: { ...issue, ...rollup }, inputs: opened });
				const said = (result.directions ?? []).join("\n");
				assert.deepEqual(result.leads, [], slug);
				assert.match(said, text, slug);
				assert.doesNotMatch(said, unsaid, slug);
				if (
					rollup.sub_issues_source === "children" &&
					typeof rollup.sub_issues_total === "number"
				) {
					assert.match(said, /mirror may omit other children/u, slug);
					if (
						rollup.sub_issues_total > 0 &&
						rollup.sub_issues_completed === rollup.sub_issues_total
					) {
						assert.match(
							said,
							/stored children were closed, not.*whole hierarchy.*complete/u,
							slug,
						);
					}
				}
				assert.equal(result.facts?.subIssuesTotal, facts.subIssuesTotal, slug);
				assert.equal(result.facts?.subIssuesCompleted, facts.subIssuesCompleted, slug);
				assert.equal(result.facts?.[checkboxes], 3, slug);
				assert.doesNotMatch(said, /looks large|strong candidate/iu, slug);
			}
		}
	});
});

void it("testing guidance keeps words separated by template comments apart", async () => {
	const result = await runBundled("states-how-to-verify-the-change", {
		item: { title: "", source_branch: "", body: "## How to test\nn<!-- template -->a" },
	});
	assert.match((result.directions ?? []).join("\n"), /holds 1 line\(s\) of author text/u);
});

void it("linked-work analysis reads only explicitly supplied context, never a repository-derived fallback", async () => {
	const root = await mkdtemp(path.join(tmpdir(), "linked-work-context-"));
	const slug = "honours-linked-issue-acceptance-criteria";
	try {
		const legacy = path.join(root, "inputs", "context");
		const context = path.join(root, "areas/linked work");
		const repo = path.join(root, "inputs", "sources", "scm", "repo");
		await mkdir(legacy, { recursive: true });
		await mkdir(context, { recursive: true });
		await writeFile(
			path.join(legacy, "linked_work_items.json"),
			JSON.stringify({
				workItems: [{ number: 12, title: "Wrong issue", body: "- [ ] WRONG CONTEXT" }],
			}),
		);
		await writeFile(
			path.join(context, "linked_work_items.json"),
			JSON.stringify({
				workItems: [
					{ number: 12, title: "Issue", body: "Acceptance criteria\n- [ ] one\n- [ ] two" },
				],
			}),
		);
		const item = { source_branch: "fix-example", title: "Fixes #12" };
		const explicit = await runBundled(slug, { item, repo, context });
		assert.equal(explicit.facts?.acceptanceCriteriaCheckboxes, 2);
		assert.deepEqual(explicit.leads, []);
		const absent = await runBundled(slug, { item, repo });
		assert.equal(absent.facts?.linkedItemsFilePresent, 0);
		const missing = await runBundled(slug, { item, repo, context: path.join(root, "missing") });
		assert.equal(missing.facts?.linkedItemsFilePresent, 0);
	} finally {
		await rm(root, { recursive: true, force: true });
	}
});

void it("linked acceptance criteria require the captured workItems record", async () => {
	const slug = "honours-linked-issue-acceptance-criteria";
	for (const record of [
		{},
		[],
		{ items: [] },
		{ number: 12, title: "Issue", body: "- [ ] task" },
		{ workItems: [null] },
		{ workItems: [{ number: 12, title: "Issue", description: "- [ ] task" }] },
	]) {
		await assert.rejects(
			runBundled(slug, { context: { "linked_work_items.json": record } }),
			(error: unknown) => {
				assert.ok(error instanceof UnratedError);
				assert.equal(error.reason, "unavailable");
				assert.match(error.message, /linked_work_items\.json/u);
				return true;
			},
		);
	}
	const result = await runBundled(slug, {
		context: {
			"linked_work_items.json": { workItems: [{ number: 12, title: "Issue", body: null }] },
		},
	});
	assert.equal(result.facts?.linkedIssueCount, 1);
	assert.equal(result.facts.linkedIssueBodyPresent, 0);
});

void it("a logger under a long repository path stays within the review direction bound", async () => {
	const file = "App/main.ts";
	const logger = `${"folder/".repeat(60)}logger.ts`;
	const source = 'console.log("diagnostic");';
	const result = await runBundled("logs-through-the-platform-logger", {
		repo: { [file]: source, [logger]: 'const log = LoggerFactory.getLogger("App");' },
		change: new Map([
			[
				file,
				{ path: file, addedLines: new Map([[1, source]]), removedLines: new Map(), hunks: [] },
			],
		]),
	});
	assert.equal(result.leads?.length, 1);
	const direction = result.directions?.find((text) =>
		text.includes("the checkout matches a logger"),
	);
	assert.ok(direction !== undefined);
	assert.equal(direction.length, 300);
	assert.ok(direction.endsWith("…"));
});

void it("issue-reference syntax in templates remains a candidate rather than an authored closing claim", async () => {
	const metadata = {
		source_branch: "plain-branch",
		title: "Documentation update",
		body: "- [ ] Related issue is linked (e.g., `Closes #12`)\nFor the intended check, see #12.",
		pr_number: 1,
		pr_url: "https://example.invalid/pull/1",
		repository_full_name: "owner/project",
		target_branch: "main",
		commit_sha: "a".repeat(40),
	};
	const result = await runBundled("honours-linked-issue-acceptance-criteria", { item: metadata });
	const said = (result.directions ?? []).join("\n");
	assert.equal(result.facts?.issueReferenceSyntaxCandidateCount, 1);
	assert.match(said, /syntax candidate/u);
	assert.match(said, /before establishing that the author/u);
	assert.doesNotMatch(said, /this change claims to close/u);
	const trace = await runBundled("ready-and-traceable-handoff", { item: metadata });
	assert.equal(trace.facts?.issueMentionSyntaxCandidateCount, 1);
	assert.match(
		(trace.directions ?? []).join("\n"),
		/templates, examples and branch numbers may be unrelated/u,
	);
	assert.doesNotMatch(
		(trace.directions ?? []).join("\n"),
		/all establish the link|motivating-issue reference IS present/u,
	);
});

void it("the readiness practice script leaves every title to the model and keeps checklist and issue facts", async () => {
	for (const title of [
		"Add a saved message draft",
		"Enforce WIP limits on the board",
		"WIP: add the quiz results screen",
	]) {
		const trace = await runBundled("ready-and-traceable-handoff", {
			item: {
				source_branch: "plain-branch",
				title,
				body: "Closes #42\n\n- [x] Results screen\n- [ ] Empty state\n",
			},
		});
		const directions = (trace.directions ?? []).join("\n");
		assert.equal(trace.facts?.checklistTicked, 1);
		assert.equal(trace.facts.checklistUnticked, 1);
		assert.equal(trace.facts.issueMentionSyntaxCandidateCount, 1);
		assert.match(directions, /1 ticked and 1 unticked checkbox line/u);
		assert.match(directions, /#42/u);
		assert.doesNotMatch(directions, /draft-style|Readiness fact/u);
	}
});

/** The smallest valid inputs of each kind: a pull request with a captured empty change, and empty records. */
const MINIMAL_INPUTS: Record<Kind, RunInputs> = {
	"scm.pull_request": { inputs: requested("scm.pull_request"), change: new Map() },
	"scm.issue": { inputs: requested("scm.issue") },
	"chat.conversation_thread": {
		inputs: requested("chat.conversation_thread"),
		item: { channel: "C1", threadTs: "1.0", messages: [], messageCount: 0, truncated: false },
	},
	"docs.document": {
		inputs: requested("docs.document"),
		item: {
			title: null,
			collection: null,
			collectionSlug: null,
			slug: null,
			createdBy: null,
			updatedBy: null,
			createdAt: null,
			updatedAt: null,
			archived: false,
		},
	},
};

/** The kind that a bundled program reads: a program for another kind names its kind when it throws. */
async function kindOf(slug: string): Promise<Kind> {
	const { outcome } = await runBundledProgram(slug, MINIMAL_INPUTS["scm.pull_request"]);
	const message = "error" in outcome && outcome.error instanceof Error ? outcome.error.message : "";
	const read = /^the script reads (?<kind>\S+),/u.exec(message)?.groups?.kind;
	return KINDS.find((kind) => kind === read) ?? "scm.pull_request";
}

/** The `meta` of a bundled program, imported from its staged source. */
async function stagedMeta(slug: string): Promise<unknown> {
	const source = await readFile(path.join(SCRIPTS_DIR, `${slug}.ts`), "utf8");
	const { root, practices } = await stagePracticeScripts({ [slug]: source }, {});
	try {
		const mod: unknown = await import(path.join(practices, `${slug}.ts`));
		const definition: unknown = isJsonObject(mod) ? mod.default : undefined;
		assert.ok(isPracticeScriptDefinition(definition), `${slug} exports no definition`);
		return definition.meta;
	} finally {
		await rm(root, { recursive: true, force: true });
	}
}

void describe("every bundled practice script", async () => {
	const names = await readdir(SCRIPTS_DIR);
	const slugs = names
		.filter((name) => name.endsWith(".ts"))
		.map((name) => name.slice(0, -".ts".length))
		.toSorted();

	for (const slug of slugs) {
		void it(`${slug} asks exactly one review on minimal inputs of its kind`, async () => {
			// runBundledReview fails unless the program asks one review and returns its result unchanged.
			await runBundledReview(slug, MINIMAL_INPUTS[await kindOf(slug)]);
		});

		void it(`${slug} declares an empty meta: it needs no direct model`, async () => {
			assert.deepEqual(await stagedMeta(slug), {});
		});
	}

	void it("a change that was not captured stops a program that reads the change before any review", async () => {
		const { reviews, outcome } = await runBundledProgram("ships-tests-with-the-change", {
			change: null,
		});
		assert.equal(reviews.length, 0);
		assert.ok("error" in outcome);
		assert.ok(outcome.error instanceof Error);
		assert.equal(outcome.error.name, "UnratedError");
	});
});
