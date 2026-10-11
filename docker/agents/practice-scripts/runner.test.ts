import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { copyFile, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { createInterface } from "node:readline";
import { after, test } from "node:test";

import { RETIRED_FORM_ERROR } from "./lib/contract.ts";
import { isJsonObject } from "./lib/json.ts";
import { MAX_ERROR_CHARS } from "./lib/script-run.ts";
import { stagePracticeScripts } from "./stage.ts";

const runner = path.join(import.meta.dirname, "runner.ts");

const roots: string[] = [];
after(async () =>
	Promise.all(roots.map(async (root) => rm(root, { recursive: true, force: true }))),
);

/**
 * Stages the scripts with the record of a pull request, and the change when given, and returns the
 * runner's arguments.
 */
async function stage(scripts: Record<string, string>, diff?: string) {
	const { root, practices } = await stagePracticeScripts(scripts);
	roots.push(root);
	const output = path.join(root, "out");
	const context = path.join(root, "context");
	await mkdir(context);
	await copyFile(
		path.join(import.meta.dirname, "fixtures", "pull-request-metadata.json"),
		path.join(context, "metadata.json"),
	);
	const args = [
		runner,
		"--repo",
		root,
		"--context",
		context,
		"--context-reference",
		"context",
		"--practices",
		practices,
		"--output",
		output,
	];
	if (diff !== undefined) {
		await writeFile(path.join(root, "change.diff"), diff);
		args.push("--diff", path.join(root, "change.diff"));
	}
	return {
		root,
		output,
		args,
		section: async (slug: string) => readFile(path.join(output, `${slug}.md`), "utf8"),
		json: async (slug: string) => {
			const parsed: unknown = JSON.parse(await readFile(path.join(output, `${slug}.json`), "utf8"));
			assert.ok(isJsonObject(parsed), `${slug}.json holds an object`);
			return parsed;
		},
	};
}

/** Stages the scripts and runs the runner on them to the end. */
async function run(scripts: Record<string, string>, diff?: string, extra: string[] = []) {
	const staged = await stage(scripts, diff);
	const { status, stderr } = spawnSync(process.execPath, [...staged.args, ...extra], {
		encoding: "utf8",
		stdio: ["ignore", "ignore", "pipe"],
	});
	assert.equal(status, 0, stderr);
	return staged;
}

/** A unified diff that adds `lines` lines to each named file. */
function additions(files: Record<string, number>): string {
	return Object.entries(files)
		.map(([file, lines]) =>
			[
				`diff --git a/${file} b/${file}`,
				"--- /dev/null",
				`+++ b/${file}`,
				`@@ -0,0 +1,${lines} @@`,
				...Array.from({ length: lines }, (_, i) => `+line ${i + 1}`),
			].join("\n"),
		)
		.join("\n");
}

/** A script whose result is the given JSON, spelled inline, with the lead kinds it declares. */
function script(result: object, kinds: Record<string, string> = {}): string {
	return `export default {
	meta: { version: 1, kinds: ${JSON.stringify(kinds)} },
	run: async () => (${JSON.stringify(result)}),
};
`;
}

/** A script whose `run` is the given body, with no lead kinds. */
function running(body: string): string {
	return `export default { meta: { version: 1, kinds: {} }, run: async (ctx) => { ${body} } };\n`;
}

void test("runner executes a staged practice and writes its public artifact contract", async () => {
	const { output, section, json } = await run(
		{
			sample: script(
				{
					leads: [
						{
							at: { change: "App/View.swift", line: 42 },
							kind: "debug-output",
							facts: { inScope: false },
						},
						{ at: { record: "metadata.json" }, kind: "record-row" },
					],
					facts: { found: 1 },
					directions: ["inspect sample"],
				},
				{ "debug-output": "A print call on an added line.", "record-row": "The record." },
			),
		},
		additions({ "App/View.swift": 42 }),
	);
	const written = await json("sample");
	assert.partialDeepStrictEqual(written, {
		practice: "sample",
		status: "ok",
		contract: "definition",
		dropped: 0,
		facts: { found: 1 },
	});
	assert.equal(typeof written.durationMs, "number");
	const summary = await section("sample");
	assert.match(summary, /^- inspect sample\n/u);
	// A changed line is cited the way the diff view prints it, with the quote that the runner read.
	assert.match(
		summary,
		/- debug-output at `App\/View\.swift` \[L42\]: `line 42` \[inScope=false\]/u,
	);
	assert.match(summary, /- record-row at `context\/metadata\.json`/u);
	assert.ok(await readFile(path.join(output, ".complete"), "utf8"));
});

void test("each practice's section stays under its own bound, with its not-rated lines and a count of what it leaves out", async () => {
	const leads = Array.from({ length: 30 }, (_, i) => ({
		at: { change: `src/file${i}.ts`, line: 1 },
		kind: "candidate",
		facts: { note: "y".repeat(150) },
	}));
	const unrated = [{ what: "added comments", count: 3, reason: "unavailable" }];
	const scripts = Object.fromEntries(
		Array.from({ length: 8 }, (_, n) => [
			`p${n}`,
			script({ leads, unrated }, { candidate: "A line to check." }),
		]),
	);
	const { section } = await run(
		scripts,
		additions(Object.fromEntries(Array.from({ length: 30 }, (_, i) => [`src/file${i}.ts`, 1]))),
	);
	// One busy practice does not trim another: each is bounded alone.
	for (let n = 0; n < 8; n += 1) {
		const text = await section(`p${n}`);
		assert.ok(text.length <= 3000, `p${n} is ${text.length} chars`);
		assert.match(
			text,
			new RegExp(
				`- \\d+ more lines are not shown, \\d+ of them leads\\. The full result is in \`[^\`]*/p${n}\\.json\`\\.`,
				"u",
			),
		);
		assert.match(text, /- Not rated: 3 added comments \(unavailable\)\. Read those yourself\.\n$/u);
	}
});

void test("a script has no environment and cannot read outside the workspace, write, or start a program", async () => {
	const probe = `
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
const attempt = (action) => { try { action(); return "allowed"; } catch (error) { return error.code ?? "denied"; } };
export default {
	meta: { version: 1, kinds: {} },
	run: async () => ({
		leads: [],
		facts: { environment: Object.keys(process.env).length },
		directions: [
			"read " + attempt(() => readFileSync("/etc/hostname")),
			"write " + attempt(() => writeFileSync("${path.join(tmpdir(), "practice-script-escape")}", "x")),
			"program " + attempt(() => execFileSync("cat", ["/proc/1/environ"])),
		],
	}),
};
`;
	const { section, json } = await run({ probe });
	assert.equal(
		await section("probe"),
		"- read ERR_ACCESS_DENIED\n- write ERR_ACCESS_DENIED\n- program ERR_ACCESS_DENIED\n\n**Facts:**\n- environment = 0\n",
	);
	assert.partialDeepStrictEqual(await json("probe"), { facts: { environment: 0 } });
});

void test("a script that never returns loses only its own practice, and is told apart from one that fails", async () => {
	const { section, json } = await run(
		{
			spin: running("for (;;) {}"),
			throws: running('throw new Error("broken");'),
			// A kill that the runner did not send, as the kernel sends when memory runs out.
			killed: running('process.kill(process.pid, "SIGKILL");'),
			// It fails before it says what it is, so the models it uses are not known.
			early: 'throw new Error("at import");\nexport default {};\n',
			quick: script({ leads: [], directions: ["finished"] }),
		},
		undefined,
		// The deadline includes starting the script's process, which a busy machine can take a second for.
		["--stage-ms", "4000"],
	);
	assert.match(
		await section("spin"),
		/\*\*Script failed\.\*\*.*- Script failed: Timeout after \d+ms/su,
	);
	const spin = await json("spin");
	assert.partialDeepStrictEqual(spin, { contract: "definition", status: "timeout", models: {} });
	assert.match(String(spin.error), /^Timeout after \d+ms$/u);
	assert.partialDeepStrictEqual(await json("throws"), {
		contract: "definition",
		status: "error",
		error: "broken",
	});
	assert.partialDeepStrictEqual(await json("killed"), {
		contract: "definition",
		status: "error",
		error: "the script's process was killed by SIGKILL",
	});
	assert.partialDeepStrictEqual(await json("early"), {
		contract: "unknown",
		status: "error",
		error: "at import",
	});
	assert.equal(await section("quick"), "- finished\n");
	const quick = await json("quick");
	assert.equal(quick.error, undefined);
});

void test("a script in the retired form fails with one line that tells how to fix it, and the other practices run", async () => {
	const { section, json } = await run({
		"plain-function": "export default () => ({ hints: [], metrics: {}, directions: [] });\n",
		"without-meta": "export default { run: async () => ({ leads: [] }) };\n",
		quick: script({ leads: [], directions: ["finished"] }),
	});
	for (const slug of ["plain-function", "without-meta"]) {
		assert.partialDeepStrictEqual(await json(slug), {
			contract: "unknown",
			status: "error",
			leads: [],
			error: RETIRED_FORM_ERROR,
		});
		assert.equal(
			await section(slug),
			`> **Script failed.** Agent must analyze this practice manually.\n\n- Script failed: ${RETIRED_FORM_ERROR}\n`,
		);
	}
	assert.equal(await section("quick"), "- finished\n");
});

void test("a large result or error reaches the runner whole, and the result keeps the error up to its bound as well-formed text", async () => {
	const { json } = await run({
		large: script({ leads: [], directions: ["d".repeat(1_000_000)] }),
		long: running('throw new Error("x".repeat(1_000_000));'),
		lone: running(String.raw`throw new Error("\uD800");`),
	});
	// The runner read the whole result: it names the direction that is too long.
	const large = await json("large");
	assert.partialDeepStrictEqual(large, { status: "error" });
	assert.match(String(large.error), /^result: directions\.0 /u);
	assert.partialDeepStrictEqual(await json("long"), {
		status: "error",
		error: "x".repeat(MAX_ERROR_CHARS),
	});
	// A lone surrogate, as a cut through a surrogate pair leaves one, is stored as U+FFFD.
	assert.partialDeepStrictEqual(await json("lone"), { status: "error", error: "\uFFFD" });
});

void test("the stage's totals count the leads and the failed practices", async () => {
	const { output } = await run(
		{
			leads: script(
				{ leads: [{ at: { change: "src/a.ts", line: 1 }, kind: "added-line" }] },
				{ "added-line": "A line that the change adds." },
			),
			none: script({ leads: [] }),
			throws: running('throw new Error("broken");'),
		},
		additions({ "src/a.ts": 1 }),
	);
	const timing: unknown = JSON.parse(await readFile(path.join(output, ".timing.json"), "utf8"));
	assert.partialDeepStrictEqual(timing, { practices: 3, leads: 1, errors: 1 });
});

void test("a staged file whose name is not a practice slug does not run", async () => {
	const { output } = await run({
		Not_A_Slug: script({ leads: [], directions: ["ran"] }),
		kept: script({ leads: [], directions: ["ran"] }),
	});
	assert.equal(existsSync(path.join(output, "Not_A_Slug.json")), false);
	assert.equal(existsSync(path.join(output, "kept.json")), true);
});

void test("a practice that would start after the stage deadline does not run, and ended at the deadline", async () => {
	const { section, json } = await run(
		{ late: script({ leads: [], directions: ["ran"] }) },
		undefined,
		["--stage-ms", "1"],
	);
	assert.match(
		await section("late"),
		/Script failed: the stage deadline passed before this practice started/u,
	);
	assert.partialDeepStrictEqual(await json("late"), { contract: "unknown", status: "timeout" });
});

void test("a script searches the workspace through the runner, which alone may start grep", async () => {
	const grepModule = path.join(import.meta.dirname, "lib", "grep.ts");
	const { root, args, section } = await stage({
		search: `
import { grep } from ${JSON.stringify(grepModule)};
export default {
	meta: { version: 1, kinds: {} },
	run: async (ctx) => {
		const matches = await grep("needle", ctx.repo, { glob: "*.txt" });
		return { leads: [], directions: matches.map((m) => m.file + ":" + m.line) };
	},
};
`,
	});
	await writeFile(path.join(root, "notes.txt"), "hay\nneedle\n");
	const { status, stderr } = spawnSync(process.execPath, args, { encoding: "utf8" });
	assert.equal(status, 0, stderr);
	// grep reports a path relative to the folder it searched.
	assert.equal(await section("search"), "- notes.txt:2\n");
});

void test("a practice that finished keeps its section when the stage is stopped", async () => {
	const { output, args } = await stage({
		quick: script({ leads: [], directions: ["finished"] }),
		spin: running("for (;;) {}"),
	});
	const stopped = spawn(process.execPath, args, {
		stdio: ["ignore", "ignore", "pipe"],
		detached: true,
	});
	// The runner logs a practice after it wrote the practice's files.
	for await (const line of createInterface({ input: stopped.stderr })) {
		if (line.includes(" quick: ")) {
			break;
		}
	}
	process.kill(-Number(stopped.pid), "SIGKILL");
	assert.equal(await readFile(path.join(output, "quick.md"), "utf8"), "- finished\n");
	assert.equal(existsSync(path.join(output, ".complete")), false);
});
