import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import nodePath from "node:path";
import { after, test } from "node:test";

import { executionReportSchema } from "../../../../../../docker/agents/practice-scripts/lib/core/contract.ts";
import { PRACTICES_DIR } from "./golden-review-workspace.ts";
import {
	directProgram,
	field,
	readJsonFile,
	runRunner,
	stagePractices,
} from "./program-host-runner.ts";

// The runner's output files for the server's own tests. One real run, with the Pi SDK mocked, admits a built-in
// practice through its review session and a custom program's direct result, and fails a retired program.
// `RunnerOutputAdmissionIntegrationTest` admits these files and records the report on the server. Set
// UPDATE_RUNNER_OUTPUT=1 to write them again after a change to what the runner writes.

const OUTPUT = nodePath.join(import.meta.dirname, "contract-v3/runner-output");
const UPDATE = process.env.UPDATE_RUNNER_OUTPUT === "1";

/** The criterion the practices are staged with, as `stagePractices` writes it. */
const CRITERION = "Added authentication calls use checked(), not insecure().";

/** A program written for the retired leads contract. */
const LEGACY_PROGRAM = `import { definePracticeScript } from "../lib/core/practice-script.ts";
export default definePracticeScript({
	meta: { version: 1, kinds: { call: "An added call." } },
	async run() {
		return { leads: [] };
	},
});
`;

const PRACTICES = [
	{ slug: "auth-review" },
	{ slug: "ui-scope", program: directProgram() },
	{ slug: "zz-legacy", program: LEGACY_PROGRAM },
] as const;

const cwd = mkdtempSync(nodePath.join(tmpdir(), "pi-runner-server-contract-"));
after(() => {
	rmSync(cwd, { recursive: true, force: true });
});
stagePractices(cwd, PRACTICES);

/**
 * The golden workspace stages the captured sources under `evidence/`; the server stages them under `context/`
 * (`SandboxLayout.CONTEXT_PREFIX`), and a citation names its sourceFile by that path.
 */
function toServerContextRoot(): void {
	renameSync(nodePath.join(cwd, "evidence"), nodePath.join(cwd, "context"));
	const task = readJsonFile(cwd, "task.json");
	const relocated = (key: string) => String(field(task, key)).replace(/^evidence\//u, "context/");
	writeFileSync(
		nodePath.join(cwd, "task.json"),
		JSON.stringify({
			...(typeof task === "object" ? task : {}),
			contextRoot: "context",
			manifest: relocated("manifest"),
			compositionRequest: relocated("compositionRequest"),
		}),
	);
	const manifest = nodePath.join(cwd, "context/manifest.json");
	writeFileSync(manifest, readFileSync(manifest, "utf8").replaceAll('"evidence/', '"context/'));
	// The server writes the record indented, with the description that the golden record leaves out.
	const golden = readJsonFile(cwd, "context/metadata.json");
	writeFileSync(
		nodePath.join(cwd, "context/metadata.json"),
		`${JSON.stringify({ ...(typeof golden === "object" ? golden : {}), body: "Adds a login form." }, null, 2)}\n`,
	);
}
toServerContextRoot();
const record = readFileSync(nodePath.join(cwd, "context/metadata.json"), "utf8");
const change = readFileSync(nodePath.join(cwd, "context/change.json"), "utf8");
const TITLE_LINE = '  "title": "Add login",';
const titleLine = record.split("\n").indexOf(TITLE_LINE) + 1;

/** What the built-in practice's review session submits: one problem that rests on a duty the criteria state. */
const sessionCandidate = {
	outcome: "NOT_MET",
	summary: "The pull request asks for no checked() sign-in",
	explanation: "The record of the pull request names a login change and no checked() call.",
	citations: [],
	problems: [
		{
			severity: "MAJOR",
			summary: "Sign-in is added without checked()",
			explanation: "The criteria ask that an added authentication call use checked().",
			citations: [
				{
					sourceKind: "scm.pull-request.core",
					sourcePath: "context/metadata.json",
					path: "context/metadata.json",
					startLine: titleLine,
					endLine: titleLine,
					quote: TITLE_LINE,
				},
			],
			basis: {
				type: "duty",
				criterionQuote: CRITERION,
				event: 0,
				explanation:
					"The pull request adds a sign-in, which the criteria require to use checked().",
			},
			nextStep: "Call checked(token) before the session starts.",
		},
	],
	evidence: {},
};

const exit = await runRunner(
	cwd,
	{},
	{ PI_HOST_SESSION_CANDIDATE: JSON.stringify(sessionCandidate) },
);

/** The execution report with each duration set to 0: the only part of a run that changes between two runs. */
function stableReport(): unknown {
	const report = executionReportSchema.parse(readJsonFile(cwd, "out/practice-scripts.json"));
	return {
		...report,
		practices: report.practices.map((entry) => ({
			...entry,
			durationMs: entry.durationMs === null ? null : 0,
		})),
	};
}

/** What the server must hold to admit the run: each practice's program and criteria, and the captured sources. */
function staged(): unknown {
	return {
		criteria: readFileSync(nodePath.join(cwd, "catalog/practices/auth-review.md"), "utf8"),
		programs: Object.fromEntries(
			PRACTICES.flatMap(({ slug }) =>
				slug === "auth-review"
					? []
					: [[slug, readFileSync(nodePath.join(cwd, PRACTICES_DIR, `${slug}.ts`), "utf8")]],
			),
		),
		captured: { "context/metadata.json": record, "context/change.json": change },
	};
}

const files: Record<string, () => unknown> = {
	"result.json": () => readJsonFile(cwd, "out/result.json"),
	"practice-scripts.json": stableReport,
	"staged.json": staged,
};

void test("the runner admits the built-in's and the direct program's results, and exits", () => {
	assert.equal(exit.status, 0, exit.stderr);
});

void test("the server's runner output fixtures are what this run wrote", () => {
	if (UPDATE) {
		mkdirSync(OUTPUT, { recursive: true });
	}
	for (const [name, read] of Object.entries(files)) {
		const file = nodePath.join(OUTPUT, name);
		const written = read();
		if (UPDATE) {
			writeFileSync(file, `${JSON.stringify(written, null, "\t")}\n`);
		}
		const parsed: unknown = JSON.parse(readFileSync(file, "utf8"));
		assert.deepStrictEqual(
			written,
			parsed,
			`${name}: run with UPDATE_RUNNER_OUTPUT=1 to write it again`,
		);
	}
});
