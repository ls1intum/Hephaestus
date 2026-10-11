import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import {
	mkdtempSync,
	mkdirSync,
	realpathSync,
	readFileSync,
	rmSync,
	symlinkSync,
	writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { mock, test } from "node:test";
import { fileURLToPath } from "node:url";

const repositoryRoot = fileURLToPath(new URL("../../../../../../", import.meta.url));
const imageRoot = path.join(repositoryRoot, "docker/agents/practice-scripts");

const scenarioRoot = process.env.PRACTICE_SCRIPT_SCENARIO_ROOT;
if (scenarioRoot !== undefined && scenarioRoot !== "") {
	mock.module("node:child_process", {
		namedExports: {
			spawnSync(command: string, args: string[]) {
				assert.equal(command, process.execPath);
				// The server's models file reaches the runner, which reads it under its own grants.
				assert.equal(
					args[args.indexOf("--models") + 1],
					path.join(scenarioRoot, "practice-script-models.json"),
				);
				// The runner ends a second before pi-practice-scripts.sh stops the stage.
				assert.equal(args[args.indexOf("--stage-ms") + 1], "29000");
				assert.equal(args[args.indexOf("--tokens") + 1], "5000");
				// Use local dependencies but execute the real runner with its permission restrictions.
				const toolchain = [
					path.join(repositoryRoot, "docker/agents/node_modules"),
					path.join(repositoryRoot, "docker/node_modules"),
					realpathSync(path.join(repositoryRoot, "node_modules")),
				].map((allowed) => `--allow-fs-read=${allowed}`);
				const index = args.indexOf("--permission") + 1;
				return spawnSync(
					command,
					args
						.map((arg) => arg.replace("/opt/practice-scripts", imageRoot))
						.toSpliced(index, 0, ...toolchain),
					{ stdio: "inherit" },
				);
			},
		},
	});
	process.argv[2] = scenarioRoot;
	process.argv[3] = "30";
	process.argv[4] = "5000";
	await import("../../../main/resources/agent/pi-practice-scripts.ts");
} else {
	void test("practice script stages only regular scripts and executes the image runner with task-declared locations", () => {
		// Node module-load grants require resolved paths; macOS temporary directories can be symlinks.
		const root = realpathSync(mkdtempSync(path.join(tmpdir(), "task-practice-script-#")));
		try {
			const context = "areas/changed work";
			const scripts = "catalog/scripts";
			mkdirSync(path.join(root, context), { recursive: true });
			mkdirSync(path.join(root, scripts), { recursive: true });
			// The change view pi-change.ts derived before this step; the runner reads the diff from it.
			mkdirSync(path.join(root, "work/change"), { recursive: true });
			writeFileSync(
				path.join(root, "work/change/diff.patch"),
				"diff --git a/a b/a\n--- a/a\n+++ b/a\n@@ -0,0 +1 @@\n[L1] +line\n",
			);
			writeFileSync(
				path.join(root, scripts, "example.ts"),
				`import { readFileSync } from "node:fs";
import { parseDiff } from "../lib/diff-parser.ts";
export default {
 meta: { version: 1, kinds: { record: "The record of the work." } },
 run: async (ctx) => ({
  leads: [{ at: { record: "metadata.json" }, kind: "record" }],
  facts: {
   files: ctx.change.size,
   addedLine: Number(ctx.change.get("a").addedLines.get(1) === "line"),
   parsed: parseDiff(readFileSync(ctx.changeDir + "/diff.patch", "utf8")).size,
   context: Number(readFileSync(ctx.context.dir + "/marker", "utf8")),
   reference: ctx.context.reference,
   repo: Number(readFileSync(ctx.repo + "/marker", "utf8")),
   prNumber: ctx.work.pr_number,
  },
 }),
};
`,
			);
			for (const [name, source] of Object.entries({
				"missing-export": "export const value = 1;",
				"invalid-result":
					"export default { meta: { version: 1, kinds: {} }, run: async () => ({ leads: [], facts: { count: Infinity } }) };",
				throws:
					"export default { meta: { version: 1, kinds: {} }, run: async () => { throw new Error('broken practice'); } };",
				"stored-inventory": `import { inventoryIssues, readProjectInventory } from "../lib/context.ts";
export default {
 meta: { version: 1, kinds: {} },
 run: async (ctx) => ({ leads: [], facts: { issues: inventoryIssues(await readProjectInventory(ctx.context.dir)).size } }),
};`,
				"stored-scoped-inventory": `import { inventoryIssues } from "../lib/context.ts";
export default {
 meta: { version: 1, kinds: {} },
 run: async () => ({
  leads: [],
  facts: {
   issue: Number(inventoryIssues({ repository: "team/project", issues: [{ number: 7, title: "Focal issue" }] }).get(7)?.title === "Focal issue"),
  },
 }),
};`,
			})) {
				writeFileSync(path.join(root, scripts, `${name}.ts`), source);
			}
			for (const name of [
				"example",
				"missing-export",
				"invalid-result",
				"throws",
				"stored-inventory",
				"stored-scoped-inventory",
			]) {
				writeFileSync(
					path.join(root, scripts, `${name}.cause.json`),
					JSON.stringify({
						kind: "scm.pull_request",
						subject: { role: "AUTHOR" },
						cause: {
							type: "requested",
							origin: "MANUAL",
							state: {},
							admittedAt: "2026-01-01T00:00:00Z",
						},
					}),
				);
				writeFileSync(path.join(root, scripts, `${name}.previous.json`), "null");
			}
			// A definition script reads what the server staged beside it, which the launcher stages with it.
			writeFileSync(
				path.join(root, scripts, "reads-cause.ts"),
				`export default {
 meta: { version: 1, kinds: {} },
 run: async (ctx) => ({ leads: [], facts: { kind: ctx.kind, cause: ctx.cause.type, signal: ctx.cause.signal, previous: ctx.previous === null, messages: ctx.work.messages.length } }),
};
`,
			);
			writeFileSync(
				path.join(root, scripts, "reads-cause.cause.json"),
				JSON.stringify({
					kind: "chat.conversation_thread",
					subject: { role: "AUTHOR" },
					cause: {
						type: "moment",
						signal: "chat.conversation_thread.settled",
						origin: "LIVE",
						state: {},
						admittedAt: "2026-01-01T00:00:00Z",
					},
				}),
			);
			writeFileSync(path.join(root, scripts, "reads-cause.previous.json"), "null");
			writeFileSync(
				path.join(root, context, "conversation_thread.json"),
				JSON.stringify({
					channel: "C1",
					threadTs: "1.0",
					messages: [{ ts: "1.0", author: "U1", text: "Why?" }],
					messageCount: 1,
					truncated: false,
				}),
			);
			mkdirSync(path.join(root, "repos/project with spaces"), { recursive: true });
			writeFileSync(path.join(root, "repos/project with spaces/marker"), "7");
			writeFileSync(path.join(root, context, "marker"), "11");
			writeFileSync(
				path.join(root, context, "metadata.json"),
				JSON.stringify({
					basis: { admission_fields: [], other_fields: "CAPTURE" },
					pr_number: 13,
					pr_url: "https://example.org/team/project/pull/13",
					repository_full_name: "team/project",
					source_branch: "feature",
					target_branch: "main",
					commit_sha: "a".repeat(40),
					subject_role: "AUTHOR",
					is_draft: false,
					is_merged: false,
					additions: 1,
					deletions: 0,
					changed_files: 1,
					labels: [],
					assignees: [],
				}),
			);
			writeFileSync(
				path.join(root, context, "project_inventory.json"),
				JSON.stringify({
					issues: [{ repository: "team/other", number: 7, title: "Foreign issue" }],
				}),
			);
			symlinkSync("example.ts", path.join(root, scripts, "linked.ts"));
			writeFileSync(
				path.join(root, "practice-script-models.json"),
				JSON.stringify({ chat: { protocol: "openai-completions", modelId: "chat-model" } }),
			);
			writeFileSync(
				path.join(root, "task.json"),
				JSON.stringify({
					schemaVersion: 4,
					contextRoot: context,
					repositoryRoot: "repos/project with spaces",
					manifest: "manifest.json",
					practiceIndex: "catalog/index.json",
					compositionRequest: "composition.json",
					preparedFeedback: "history/prepared.json",
					practiceScripts: scripts,
				}),
			);
			mkdirSync(path.join(root, "work/practice-scripts-stage"), { recursive: true });
			mkdirSync(path.join(root, "work/practice-scripts-out"), { recursive: true });
			symlinkSync(path.join(imageRoot, "lib"), path.join(root, "work/practice-scripts-stage/lib"));
			const child = spawnSync(
				process.execPath,
				[
					"--experimental-test-module-mocks",
					"--permission",
					"--allow-worker",
					`--allow-fs-read=${fileURLToPath(new URL("../../../", import.meta.url))}`,
					`--allow-fs-read=${root}`,
					`--allow-fs-read=${imageRoot}`,
					"--allow-child-process",
					`--allow-fs-write=${root}/work/practice-scripts-stage*`,
					`--allow-fs-write=${root}/work/practice-scripts-out*`,
					import.meta.filename,
				],
				{
					env: { ...process.env, PRACTICE_SCRIPT_SCENARIO_ROOT: root },
					encoding: "utf8",
					timeout: 10_000,
				},
			);
			assert.equal(child.status, 0, child.stderr);
			const result: unknown = JSON.parse(
				readFileSync(path.join(root, "work/practice-scripts-out/example.json"), "utf8"),
			);
			assert.ok(typeof result === "object" && result !== null);
			assert.equal(Reflect.get(result, "status"), "ok", JSON.stringify(result));
			assert.deepEqual(Reflect.get(result, "leads"), [
				{ at: { record: "metadata.json" }, kind: "record", quote: "" },
			]);
			assert.deepEqual(Reflect.get(result, "facts"), {
				files: 1,
				addedLine: 1,
				parsed: 1,
				context: 11,
				reference: context,
				repo: 7,
				prNumber: 13,
			});
			assert.match(
				readFileSync(path.join(root, "work/practice-scripts-out/example.md"), "utf8"),
				new RegExp(`- record at \`${context}/metadata\\.json\``, "u"),
			);
			assert.ok(readFileSync(path.join(root, "work/practice-scripts-out/.complete"), "utf8"));
			const scoped: unknown = JSON.parse(
				readFileSync(path.join(root, "work/practice-scripts-out/stored-scoped-inventory.json"), "utf8"),
			);
			assert.ok(typeof scoped === "object" && scoped !== null);
			assert.equal(Reflect.get(scoped, "status"), "ok");
			assert.deepEqual(Reflect.get(scoped, "facts"), { issue: 1 });
			const definition: unknown = JSON.parse(
				readFileSync(path.join(root, "work/practice-scripts-out/reads-cause.json"), "utf8"),
			);
			assert.ok(typeof definition === "object" && definition !== null);
			assert.equal(Reflect.get(definition, "status"), "ok", JSON.stringify(definition));
			assert.deepEqual(Reflect.get(definition, "facts"), {
				kind: "chat.conversation_thread",
				cause: "moment",
				signal: "chat.conversation_thread.settled",
				previous: true,
				messages: 1,
			});
			for (const slug of ["missing-export", "invalid-result", "throws", "stored-inventory"]) {
				const failure: unknown = JSON.parse(
					readFileSync(path.join(root, `work/practice-scripts-out/${slug}.json`), "utf8"),
				);
				assert.ok(typeof failure === "object" && failure !== null);
				assert.equal(Reflect.get(failure, "status"), "error");
				assert.deepEqual(Reflect.get(failure, "leads"), []);
			}
			// A failed script's practice is said to be the model's to analyze, in its own section.
			assert.match(
				readFileSync(path.join(root, "work/practice-scripts-out/throws.md"), "utf8"),
				/^> \*\*Script failed\.\*\* Agent must analyze this practice manually\./u,
			);

			const advisory = readFileSync(
				path.join(root, "work/practice-scripts-out/stored-inventory.md"),
				"utf8",
			);
			assert.match(advisory, /Agent must analyze this practice manually/u);
			assert.match(advisory, /no explicit single-repository scope/u);
			assert.throws(
				() => readFileSync(path.join(root, "work/practice-scripts-stage/practices/linked.ts")),
				/ENOENT/u,
			);
		} finally {
			rmSync(root, { recursive: true, force: true });
		}
	});
}
