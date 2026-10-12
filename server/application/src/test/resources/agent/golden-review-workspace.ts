/**
 * The workspace of the golden prompt test: one review of one practice of a pull request, staged as the server
 * stages it. The frozen prompts in `golden-prompts/` were captured from commit 5347f0108 over this same
 * workspace, so a prompt block that differs is a block the runner changed.
 */
import { mkdirSync, writeFileSync } from "node:fs";
import nodePath from "node:path";

/** The lead labels of the prepared review input: the lead kinds of the retired script, word for word. */
export const LEAD_LABELS = {
	"insecure-call": "An added call to insecure(); check whether a checked() call guards it.",
	"auth-helper": "The repository's authentication helper that the change could call instead.",
	"review-record": "The pull request record that names the change's intent.",
} as const;

/** One lead of the prepared input, by its label's key. */
export interface GoldenLead {
	at:
		| { change: string; line: number }
		| { file: string; line: number }
		| { record: string; line: number };
	kind: keyof typeof LEAD_LABELS;
	facts?: Record<string, string | number | boolean>;
}

/** The leads of the prepared input: three, or those three and sixty more, which no section holds whole. */
export function goldenLeads(many: boolean): GoldenLead[] {
	return [
		{ at: { change: "src/Auth.java", line: 10 }, kind: "insecure-call", facts: { guarded: false } },
		{ at: { file: "src/Checked.java", line: 2 }, kind: "auth-helper" },
		{ at: { record: "metadata.json", line: 1 }, kind: "review-record" },
		...(many
			? Array.from({ length: 60 }, (_, i) => ({
					at: { file: "src/Checked.java", line: i + 1 },
					kind: "auth-helper" as const,
					facts: { index: i },
				}))
			: []),
	];
}

export const GOLDEN_FACTS = { insecureCalls: 1, checkedCalls: 0 };
export const GOLDEN_DIRECTIONS = ["Check each added authentication call against the criteria."];
export const GOLDEN_UNRATED = [
	{ what: "added calls in generated files", count: 2, reason: "too-large" as const },
];

/** The record of the reviewed pull request, complete, as `metadata.json` holds it on one line. */
export const GOLDEN_RECORD = {
	basis: { admission_fields: [], other_fields: "CAPTURE" },
	pr_number: 3,
	pr_url: "https://gitlab.example/group/repo/-/merge_requests/3",
	repository_full_name: "group/repo",
	source_branch: "login",
	target_branch: "main",
	commit_sha: "a".repeat(40),
	subject_role: "AUTHOR",
	title: "Add login",
	is_draft: false,
	is_merged: false,
	additions: 1,
	deletions: 0,
	changed_files: 1,
	labels: [],
	assignees: [],
};

/** The cause of the review in the server's current form: with the practice revision and the authored scope. */
export const GOLDEN_CAUSE = {
	kind: "scm.pull_request",
	subject: { role: "AUTHOR" },
	practice: { revisionId: 7 },
	scope: { key: "scm.pull_request:3:AUTHOR", mode: "change", description: "the reviewed change" },
	cause: {
		type: "requested",
		origin: "MANUAL",
		state: { draftStatus: "NOT_DRAFT", state: "OPEN" },
		admittedAt: "2026-01-01T00:00:00Z",
	},
};

/** Where the staged programs and their inputs live, as `task.json#practiceScripts` names it. */
export const PRACTICES_DIR = "work/practice-scripts/practices";

/**
 * Stage the review workspace under `cwd`. `schemaVersion` is the task contract the runner reads; `orchestrator`
 * is the staged system prompt.
 */
export function stageGoldenWorkspace(
	cwd: string,
	schemaVersion: number,
	orchestrator: string,
): void {
	const write = (file: string, text: string) => {
		mkdirSync(nodePath.dirname(nodePath.join(cwd, file)), { recursive: true });
		writeFileSync(nodePath.join(cwd, file), text);
	};
	write("AGENTS.md", orchestrator);
	write("evidence/metadata.json", JSON.stringify(GOLDEN_RECORD));
	write(
		"evidence/change.json",
		JSON.stringify({ base_sha: "b".repeat(40), head_sha: "a".repeat(40) }),
	);
	// The change view the runner derives from the checkout before any practice is reviewed.
	write(
		"work/change/diff.patch",
		"diff --git a/src/Auth.java b/src/Auth.java\n--- a/src/Auth.java\n+++ b/src/Auth.java\n@@ -10,0 +10,1 @@\n[L10] + insecure();\n",
	);
	write(
		"repos/primary/src/Checked.java",
		`${Array.from({ length: 70 }, (_, i) => (i === 1 ? "  checked(token);" : `  line${i + 1}();`)).join("\n")}\n`,
	);
	mkdirSync(nodePath.join(cwd, "repos/primary/.git"), { recursive: true });
	write(
		"catalog/practices/test-practice.md",
		"# Uses secure authentication\n\n## Standard\nAdded authentication calls use checked(), not insecure().\n\n## Occasion\nAn authentication call was added.\n\n## Judge\nMET for checked(); NOT_MET for insecure().\n\n## Severity\nMAJOR for an added insecure() call.\n\n## Defer\nOther code is outside this practice.\n",
	);
	write(
		"catalog/practices/index.json",
		JSON.stringify([
			{
				slug: "test-practice",
				group: "security",
				readsSources: ["scm.pull-request.core", "scm.pull-request.diff"],
				exhaustiveSources: ["scm.pull-request.diff"],
				revisionId: 7,
			},
		]),
	);
	write(`${PRACTICES_DIR}/test-practice.cause.json`, JSON.stringify(GOLDEN_CAUSE));
	write(`${PRACTICES_DIR}/test-practice.previous.json`, "null");
	write(
		"evidence/manifest.json",
		JSON.stringify({
			artifactKind: "scm.pull_request",
			artifacts: [
				{ kind: "scm.pull-request.core", artifact: { path: "evidence/metadata.json" } },
				{ kind: "scm.pull-request.diff", artifact: { path: "evidence/change.json" } },
			],
			sources: [
				{
					kind: "scm.pull-request.core",
					state: { availability: "AVAILABLE" },
					artifacts: [{ path: "evidence/metadata.json" }],
				},
				{
					kind: "scm.pull-request.diff",
					state: { availability: "AVAILABLE" },
					artifacts: [{ path: "evidence/change.json" }],
				},
			],
		}),
	);
	write(
		"pi-provider.json",
		JSON.stringify({ apiProtocol: "openai-completions", modelId: "test-model" }),
	);
	write(
		"task.json",
		JSON.stringify({
			schemaVersion,
			contextRoot: "evidence",
			repositoryRoot: "repos/primary",
			manifest: "evidence/manifest.json",
			practiceIndex: "catalog/practices/index.json",
			compositionRequest: "evidence/composition.json",
			preparedFeedback: "history/prepared.json",
			practiceScripts: PRACTICES_DIR,
			prompt: "Review the practice.",
			repositoryFullName: "group/repo",
			pullRequestNumber: 3,
		}),
	);
}
