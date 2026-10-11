#!/usr/bin/env node
/**
 * Run one practice script on a change as the runner runs it, with no model, and print the result
 * `<slug>.json` and the section `<slug>.md` that the review reads. A model slot is not available, so
 * an optional slot gives its items as not rated and a required slot skips the script.
 *
 * Without `--cause` or `--signal`, the script runs as a review that a person requested, of the kind
 * of work that `--metadata` holds, or of a pull request. Without `--previous`, no review came before.
 *
 * Exit codes: 0 when the script ran, 1 when it failed or ran out of time, 2 when an option or an input
 * file is not usable.
 */
import { mkdir, readFile, rm, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import { parseArgs } from "node:util";

import {
	isFailed,
	KINDS,
	MOMENTS,
	PRACTICE_SLUG,
	WORK_RECORDS,
	type Kind,
} from "./lib/contract.ts";
import { parseDiff } from "./lib/diff-parser.ts";
import { isJsonObject } from "./lib/json.ts";
import { ModelBudget } from "./lib/models-host.ts";
import { changeSince, parseInputs, type ScriptInputs } from "./lib/script-inputs.ts";
import { practiceFiles, readableRoots, runScript } from "./lib/script-run.ts";
import type { DiffFile } from "./lib/types.ts";
import { stagePracticeScripts } from "./stage.ts";

const USAGE =
	"Usage: node validate.ts --script <file.ts> --repo <clone> [--diff <patch>] [--metadata <json>] [--context <dir>] [--cause <json> | --signal <moment> [--recheck]] [--previous <json>] [--out <dir>]";

/** Stop on input that the command cannot use, with one line and exit code 2. */
function stop(problem: string): never {
	console.error(problem);
	process.exit(2);
}

const messageOf = (error: unknown) => (error instanceof Error ? error.message : String(error));

async function read(option: string, file: string): Promise<string> {
	try {
		return await readFile(file, "utf8");
	} catch (error) {
		return stop(`--${option}: ${messageOf(error)}`);
	}
}

async function requireFolder(option: string, dir: string): Promise<void> {
	const found = await stat(dir).catch(() => undefined);
	if (found?.isDirectory() !== true) {
		stop(`--${option}: ${dir} is not a folder`);
	}
}

function options() {
	try {
		return parseArgs({
			args: process.argv.slice(2),
			options: {
				script: { type: "string" },
				repo: { type: "string" },
				diff: { type: "string" },
				metadata: { type: "string" },
				context: { type: "string" },
				cause: { type: "string" },
				signal: { type: "string" },
				recheck: { type: "boolean" },
				previous: { type: "string" },
				out: { type: "string" },
			},
		}).values;
	} catch (error) {
		// Node's message can add a sentence about arguments without an option; the usage line replaces it.
		return stop(`${messageOf(error).replace(/\. .*$/su, "")}. ${USAGE}`);
	}
}

const {
	script,
	repo,
	context,
	out,
	diff,
	metadata: metadataFile,
	cause: causeFile,
	signal,
	recheck,
	previous: previousFile,
} = options();
if (script === undefined || repo === undefined) {
	stop(`--script and --repo are required. ${USAGE}`);
}
const slug = path.basename(script, ".ts");
if (!PRACTICE_SLUG.test(slug)) {
	stop(
		`${script}: the file name, less .ts, is the practice's identifier: lowercase letters, digits and hyphens`,
	);
}
await requireFolder("repo", repo);
if (context !== undefined) {
	await requireFolder("context", context);
}
const source = await read("script", script);
const change =
	diff === undefined ? new Map<string, DiffFile>() : parseDiff(await read("diff", diff));
if (diff !== undefined && change.size === 0) {
	stop(`--diff: ${diff} holds no unified diff`);
}
let metadata: Record<string, unknown> = {};
if (metadataFile !== undefined) {
	let value: unknown;
	try {
		value = JSON.parse(await read("metadata", metadataFile));
	} catch (error) {
		stop(`--metadata: ${metadataFile} is not JSON: ${messageOf(error)}`);
	}
	if (!isJsonObject(value)) {
		stop(`--metadata: ${metadataFile} must hold a JSON object`);
	}
	metadata = value;
}

/** A JSON file that an option names. */
async function json(option: string, file: string): Promise<unknown> {
	try {
		return JSON.parse(await read(option, file));
	} catch (error) {
		return stop(`--${option}: ${file} is not JSON: ${messageOf(error)}`);
	}
}

/** The kind whose record `metadata` holds, if one does. */
const kindOfRecord = (record: unknown): Kind | undefined =>
	KINDS.find((kind) => WORK_RECORDS[kind].schema.safeParse(record).success);

function signalKind(moment: string): Kind {
	const kind = KINDS.find((candidate) => MOMENTS[candidate].some((m) => m === moment));
	return kind ?? stop(`--signal: ${moment} is not a moment of any kind of work`);
}

if (causeFile !== undefined && signal !== undefined) {
	stop(`--cause and --signal exclude each other. ${USAGE}`);
}
if (recheck === true && signal === undefined) {
	stop(`--recheck needs --signal. ${USAGE}`);
}
const admittedAt = new Date().toISOString();
const kind =
	signal === undefined
		? (kindOfRecord(metadataFile === undefined ? undefined : metadata) ?? "scm.pull_request")
		: signalKind(signal);
const cause: unknown =
	causeFile === undefined
		? {
				kind,
				subject: { role: "AUTHOR" },
				cause:
					signal === undefined
						? { type: "requested", origin: "MANUAL", state: {}, admittedAt }
						: {
								type: recheck === true ? "recheck" : "moment",
								signal,
								origin: "LIVE",
								state: {},
								admittedAt,
							},
			}
		: await json("cause", causeFile);
const previous = previousFile === undefined ? null : await json("previous", previousFile);
const parsed = parseInputs(
	slug,
	cause,
	previous,
	metadataFile === undefined ? undefined : metadata,
);
let inputs: ScriptInputs = parsed;
if (
	"inputs" in parsed &&
	parsed.inputs.kind === "scm.pull_request" &&
	parsed.inputs.previous?.head !== undefined
) {
	const since = await changeSince(repo, parsed.inputs.previous.head, parsed.inputs.work.commit_sha);
	if ("change" in since) {
		inputs = { ...parsed, changeSince: since.change };
	} else {
		console.error(`No change since the previous review: ${since.unknown}`);
	}
}
if (out !== undefined) {
	await mkdir(out, { recursive: true }).catch((error: unknown) => {
		stop(`--out: ${messageOf(error)}`);
	});
}

// The inputs go to the script directly, so no file is staged beside it.
const staged = await stagePracticeScripts({ [slug]: source }, {});
try {
	const started = Date.now();
	const result = await runScript(
		slug,
		path.join(staged.practices, `${slug}.ts`),
		{
			readable: readableRoots([staged.stage, repo, context ?? ""]),
			sources: { change, repo, contextDir: context ?? "", contextReference: context ?? "" },
			changeDir: "",
			now: new Date(started).toISOString(),
			stageEnd: Number.POSITIVE_INFINITY,
			models: () => ({}),
			budget: new ModelBudget(0, 1),
			log: (line) => console.error(line),
		},
		inputs,
	);
	// Without `--out`, the section's pointer names the result that this command prints.
	const jsonPath = path.join(out ?? "", `${slug}.json`);
	const sectionPath = path.join(out ?? "", `${slug}.md`);
	const files = practiceFiles(result, Date.now() - started, jsonPath, context ?? "");
	if (out !== undefined) {
		await writeFile(jsonPath, files.json);
		await writeFile(sectionPath, files.section);
	}
	console.log(`==> ${jsonPath} <==\n${files.json}\n\n==> ${sectionPath} <==\n${files.section}`);
	process.exitCode = isFailed(result.status) ? 1 : 0;
} finally {
	await rm(staged.root, { recursive: true, force: true });
}
