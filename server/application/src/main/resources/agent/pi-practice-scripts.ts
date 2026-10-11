import { spawnSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync } from "node:fs";
import path from "node:path";

import { CHANGE_ROOT } from "./pi-change.ts";
import { SUPPORTED_SCHEMA_VERSION, resolveTaskPaths } from "./pi-task-paths.ts";

/** SandboxLayout.PRACTICE_SCRIPT_MODELS_FILE; SandboxLayoutSyncTest keeps the two equal. */
const PRACTICE_SCRIPT_MODELS_FILE = "practice-script-models.json";
/**
 * Each script and what the server stages beside it: its cause and its previous claim.
 * `PracticeScriptContractSyncTest` keeps the suffixes equal to `SandboxLayout`.
 */
const STAGED_SUFFIXES = [".ts", ".cause.json", ".previous.json"];

const root = path.resolve(process.argv[2] ?? "/workspace");
const envelope: unknown = JSON.parse(readFileSync(path.resolve(root, "task.json"), "utf8"));
if (typeof envelope !== "object" || envelope === null) {
	throw new Error("task.json: expected an envelope");
}
if (Reflect.get(envelope, "schemaVersion") !== SUPPORTED_SCHEMA_VERSION) {
	throw new Error("task.json: unsupported schemaVersion");
}
const paths = resolveTaskPaths(root, envelope);
// The stage's budget in seconds, as pi-practice-scripts.sh enforces it. The runner ends a second earlier,
// so it writes its own completion instead of being killed with its last practices.
const budgetSeconds = Number(process.argv[3]);
const stageArguments =
	Number.isFinite(budgetSeconds) && budgetSeconds > 1
		? ["--stage-ms", String((budgetSeconds - 1) * 1000)]
		: [];
// The tokens the scripts may spend on models together, as the proxy caps them for the attempt.
const maxTokens = process.argv[4];
const tokenArguments = maxTokens === undefined ? [] : ["--tokens", maxTokens];
// The server writes it only when the job may call models for practice scripts.
const models = path.resolve(root, PRACTICE_SCRIPT_MODELS_FILE);
const modelArguments = existsSync(models) ? ["--models", models] : [];
const stage = path.resolve(root, "work/practice-scripts-stage");
const output = path.resolve(root, "work/practice-scripts-out");
rmSync(path.resolve(stage, "practices"), { recursive: true, force: true });
mkdirSync(path.resolve(stage, "practices"), { recursive: true });
mkdirSync(output, { recursive: true });
if (existsSync(paths.practiceScripts)) {
	for (const entry of readdirSync(paths.practiceScripts, { withFileTypes: true })) {
		if (entry.isFile() && STAGED_SUFFIXES.some((suffix) => entry.name.endsWith(suffix))) {
			copyFileSync(
				path.resolve(paths.practiceScripts, entry.name),
				path.resolve(stage, "practices", entry.name),
			);
		}
	}
}
const child = spawnSync(
	process.execPath,
	[
		"--permission",
		`--allow-fs-read=${root}`,
		"--allow-fs-read=/opt/practice-scripts",
		`--allow-fs-write=${output}*`,
		"--allow-child-process",
		"/opt/practice-scripts/runner.ts",
		"--repo",
		paths.repositoryRoot,
		"--diff",
		path.resolve(root, CHANGE_ROOT, "diff.patch"),
		"--metadata",
		path.resolve(paths.contextRoot, "metadata.json"),
		"--context",
		paths.contextRoot,
		"--context-reference",
		path.relative(root, paths.contextRoot),
		"--change",
		path.resolve(root, CHANGE_ROOT),
		"--practices",
		path.resolve(stage, "practices"),
		"--output",
		output,
		...stageArguments,
		...tokenArguments,
		...modelArguments,
	],
	{ stdio: "inherit" },
);
if (child.error) {
	throw child.error;
}
process.exitCode = child.status ?? 1;
