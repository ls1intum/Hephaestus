#!/usr/bin/env node
/**
 * Run one practice program on local captures as the host runs it, with no model and no review session.
 * It prints the report `<slug>.json`: how the program ran, each review input it asked for, and the
 * result it returned. It prints the section `<slug>.review-<n>.md` that each review session would read.
 *
 * No model is bound: an optional slot is absent, and a required slot skips the program. No review
 * session runs: `review()` returns the result in `--review` after the host's checks, and without it
 * `review()` rejects with "unavailable". The tool never makes up a result.
 *
 * Without `--cause` or `--signal`, the program runs as a review that a person requested, of the kind
 * of work that `--metadata` holds, or of a pull request. Without `--previous`, no review came before.
 * Without `--diff`, the work has no captured change. The base commit of the change is not known here,
 * so `files.change.base` is empty. A citation of a captured record needs the server's folder index,
 * which a local run does not have, so `citeRecord` fails here.
 *
 * Exit codes: 0 when the program returned a result, 1 when it failed, was skipped or returned none,
 * 2 when an option or an input file is not usable.
 */
import { mkdir, readFile, rm, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import { parseArgs } from "node:util";

import {
	KINDS,
	MOMENTS,
	parseCandidate,
	PRACTICE_SLUG,
	WORK_RECORDS,
	type Candidate,
	type Kind,
	type ReviewOptions,
} from "./lib/contract.ts";
import { parseDiff } from "./lib/diff-parser.ts";
import { EvidenceReader, noReceipts, type EvidenceSources } from "./lib/evidence-reader.ts";
import type { JobFiles } from "./lib/ipc.ts";
import { isJsonObject } from "./lib/json.ts";
import { renderReviewInput, resolveLeads } from "./lib/leads.ts";
import { ModelBudget } from "./lib/models-host.ts";
import { reasonOf, UnratedError, type UnratedReason } from "./lib/practice-script.ts";
import {
	notReached,
	runPracticeProgram,
	type PracticeProgram,
	type ProgramOutcome,
} from "./lib/program-host.ts";
import { changeSince, parseInputs } from "./lib/script-inputs.ts";
import type { DiffFile } from "./lib/types.ts";
import { stagePracticeScripts } from "./stage.ts";

const USAGE =
	"Usage: node validate.ts --script <file.ts> --repo <clone> [--diff <patch>] [--metadata <json>] [--context <dir>] [--cause <json> | --signal <moment> [--recheck]] [--previous <json>] [--review <candidate.json>] [--out <dir>]";

/** How long the program may run. */
const DEADLINE_MS = 600_000;
/** Where citations name the context folder and the checkout, as the sandbox stages them. */
const CONTEXT_REFERENCE = "context";
const TREE_ARTIFACT = "repos/reviewed/.git/HEAD";

/** Stop on input that the command cannot use, with one line and exit code 2. */
function stop(problem: string): never {
	console.error(problem);
	process.exit(2);
}

const messageOf = (error: unknown) => (error instanceof Error ? error.message : String(error));
const oneLine = (text: string) => text.replaceAll(/\s+/gu, " ").trim();

async function read(option: string, file: string): Promise<string> {
	try {
		return await readFile(file, "utf8");
	} catch (error) {
		return stop(`--${option}: ${messageOf(error)}`);
	}
}

/** A JSON file that an option names. */
async function json(option: string, file: string): Promise<unknown> {
	const text = await read(option, file);
	try {
		return JSON.parse(text);
	} catch (error) {
		return stop(`--${option}: ${file} is not JSON: ${messageOf(error)}`);
	}
}

async function requireFolder(option: string, dir: string): Promise<void> {
	const found = await stat(dir).catch(() => undefined);
	if (found?.isDirectory() !== true) {
		stop(`--${option}: ${dir} is not a folder`);
	}
}

function parsedArgs() {
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
				review: { type: "string" },
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
	review: reviewFile,
} = parsedArgs();
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
let change: Map<string, DiffFile> | null = null;
if (diff !== undefined) {
	change = parseDiff(await read("diff", diff));
	if (change.size === 0) {
		stop(`--diff: ${diff} holds no unified diff`);
	}
}
let metadata: Record<string, unknown> | undefined;
if (metadataFile !== undefined) {
	const value = await json("metadata", metadataFile);
	if (!isJsonObject(value)) {
		stop(`--metadata: ${metadataFile} must hold a JSON object`);
	}
	metadata = value;
}
let reviewed: Candidate | undefined;
if (reviewFile !== undefined) {
	const parsed = parseCandidate(await json("review", reviewFile));
	if (!parsed.ok) {
		stop(
			`--review: ${reviewFile} is not a result: ${parsed.reasonCode}: ${oneLine(parsed.message)}`,
		);
	}
	reviewed = parsed.candidate;
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
const started = Date.now();
const admittedAt = new Date(started).toISOString();
const kind =
	signal === undefined ? (kindOfRecord(metadata) ?? "scm.pull_request") : signalKind(signal);
const cause: unknown =
	causeFile === undefined
		? {
				kind,
				subject: { role: "AUTHOR" },
				practice: { revisionId: 1 },
				scope: { key: `${kind}:1:AUTHOR`, mode: "change", description: "the reviewed work" },
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
if (out !== undefined) {
	await mkdir(out, { recursive: true }).catch((error: unknown) => {
		stop(`--out: ${messageOf(error)}`);
	});
}

// Without `--out`, each pointer names the file that this command prints. The section of the n-th
// review is `<slug>.review-<n>.md`; a review with an empty input has none.
const jsonPath = path.join(out ?? "", `${slug}.json`);
const sectionPath = (n: number) => path.join(out ?? "", `${slug}.review-${n}.md`);

/** One review that the program asked for: its input as the host parsed it, and what it gave back. */
interface AskedReview {
	/** The input, each lead with the quote that the host resolved. */
	input: ReviewOptions;
	/** The section that the review session reads; none when the input is empty. */
	section?: string;
	candidate?: Candidate;
	unrated?: { reason: UnratedReason; message: string };
}
const reviews: AskedReview[] = [];

const staged = await stagePracticeScripts({ [slug]: source }, {});
let outcome: ProgramOutcome;
try {
	const program: PracticeProgram = {
		script: { path: path.join(staged.practices, `${slug}.ts`), source: Buffer.from(source) },
	};
	const parsed = parseInputs(slug, cause, previous, metadata);
	if ("error" in parsed) {
		outcome = {
			...notReached(program, "unavailable"),
			status: "failed",
			error: `the review's inputs are not valid: ${parsed.error}`,
			durationMs: 0,
		};
	} else {
		const { inputs } = parsed;
		let since: Map<string, DiffFile> | undefined;
		if (inputs.kind === "scm.pull_request" && inputs.previous?.head !== undefined) {
			const found = await changeSince(repo, inputs.previous.head, inputs.work.commit_sha);
			if ("change" in found) {
				since = found.change;
			} else {
				console.error(`No change since the previous review: ${found.unknown}`);
			}
		}
		const head = inputs.kind === "scm.pull_request" ? inputs.work.commit_sha : null;
		const captured = change === null || head === null ? null : { base: "", head, files: change };
		const sources: EvidenceSources = {
			repo: path.resolve(repo),
			headRevision: head,
			treeArtifact: TREE_ARTIFACT,
			contextDir: context === undefined ? null : path.resolve(context),
			contextReference: CONTEXT_REFERENCE,
			change: captured,
			sourceKindOf: () => undefined,
		};
		const files: JobFiles | null =
			head === null ? null : { head: { revision: head }, change: captured };
		const receipts = noReceipts();
		outcome = await runPracticeProgram(
			program,
			{
				slug,
				inputs,
				criteria: "",
				...(since === undefined ? {} : { changeSince: since }),
				files,
				now: admittedAt,
				deadline: started + DEADLINE_MS,
			},
			{
				reader: new EvidenceReader(sources, receipts),
				models: {},
				budget: new ModelBudget(0, 1),
				review: async (options) => {
					const asked: AskedReview = { input: options };
					reviews.push(asked);
					try {
						const leads = await resolveLeads(options.leads ?? [], sources, since);
						asked.input = leads.length === 0 ? options : { ...options, leads };
						const prepared =
							leads.length > 0 ||
							Object.keys(options.facts ?? {}).length > 0 ||
							(options.directions ?? []).length > 0 ||
							(options.context ?? []).length > 0 ||
							(options.limitations ?? []).length > 0;
						if (prepared) {
							asked.section = renderReviewInput(
								options,
								leads,
								CONTEXT_REFERENCE,
								`\`${jsonPath}\``,
								since !== undefined,
							);
						}
					} catch (error) {
						asked.unrated = { reason: reasonOf(error), message: messageOf(error) };
						throw error;
					}
					if (reviewed === undefined) {
						const message =
							"this tool opens no review session: give --review <candidate.json> for the result that review() returns";
						asked.unrated = { reason: "unavailable", message };
						throw new UnratedError("unavailable", message);
					}
					asked.candidate = reviewed;
					return reviewed;
				},
				signal: new AbortController().signal,
				log: (line) => {
					console.error(line);
				},
				receipts,
			},
		);
	}
} finally {
	await rm(staged.root, { recursive: true, force: true });
}

const { candidate, error } = outcome;
const report = `${JSON.stringify(
	{
		practiceSlug: slug,
		program: outcome.program,
		status: outcome.status,
		stopReason: outcome.stopReason,
		...(error === undefined ? {} : { error }),
		models: outcome.models,
		calls: outcome.calls,
		coverageReached: outcome.coverageReached,
		durationMs: outcome.durationMs,
		reviews: reviews.map(({ input, section, candidate: returned, unrated }, index) => ({
			input,
			section: section === undefined ? null : sectionPath(index + 1),
			...(returned === undefined ? {} : { candidate: returned }),
			...(unrated === undefined ? {} : { unrated }),
		})),
		...(candidate === undefined ? {} : { candidate }),
	},
	null,
	2,
)}\n`;
const printed = [`==> ${jsonPath} <==\n${report}`];
if (out !== undefined) {
	await writeFile(jsonPath, report);
}
for (const [index, { section }] of reviews.entries()) {
	if (section !== undefined) {
		printed.push(`==> ${sectionPath(index + 1)} <==\n${section}`);
		if (out !== undefined) {
			await writeFile(sectionPath(index + 1), section);
		}
	}
}
console.log(printed.join("\n"));
console.error(
	candidate === undefined
		? `${slug}: ${outcome.status} (${outcome.stopReason ?? "no result"})${error === undefined ? "" : `: ${oneLine(error)}`}`
		: `${slug}: ok: the program returned a ${candidate.outcome} result`,
);
process.exitCode = candidate === undefined ? 1 : 0;
