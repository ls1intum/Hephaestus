/**
 * The host's side of what a program reads about its review: the files that the server stages for
 * each admitted practice, the record of the work, and the change since the previous review. The host
 * validates them once, before the program starts. A program cannot start git, so the host computes
 * the change.
 */
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";

import {
	isCommit,
	KINDS,
	CAUSE_FILE_SUFFIX,
	practiceInputsSchema,
	PREVIOUS_FILE_SUFFIX,
	WORK_RECORDS,
	type Kind,
	type PracticeInputs,
} from "./contract.ts";
import { parseDiff } from "./diff-parser.ts";
import { isJsonObject } from "./json.ts";
import type { DiffFile } from "./types.ts";

/** What the host gives a program, or why it cannot. */
export type ScriptInputs =
	| { inputs: PracticeInputs; changeSince?: Map<string, DiffFile> }
	| { error: string };

/** The bytes of a change since the previous review that the host reads; a larger one is not known. */
const MAX_CHANGE_BYTES = 64 * 1024 * 1024;

const messageOf = (error: unknown) => (error instanceof Error ? error.message : String(error));

const isKind = (value: unknown): value is Kind => KINDS.some((kind) => kind === value);

/** A JSON file's value, or why there is none. */
export async function readJson(file: string): Promise<{ value: unknown } | { error: string }> {
	let text: string;
	try {
		text = await readFile(file, "utf8");
	} catch {
		return { error: `${path.basename(file)} was not staged` };
	}
	try {
		return { value: JSON.parse(text) };
	} catch (error) {
		return { error: `${path.basename(file)} is not JSON: ${messageOf(error)}` };
	}
}

/**
 * Validate a practice's inputs: its cause file (`kind`, `subject`, `cause`), its previous claim,
 * and the record of the work. An error names the file that it is in.
 */
export function parseInputs(
	slug: string,
	cause: unknown,
	previous: unknown,
	work: unknown,
): { inputs: PracticeInputs } | { error: string } {
	if (!isJsonObject(cause)) {
		return { error: `${slug}${CAUSE_FILE_SUFFIX}: must hold a JSON object` };
	}
	const parsed = practiceInputsSchema.safeParse({ ...cause, previous, work });
	if (parsed.success) {
		return { inputs: parsed.data };
	}
	const issue = parsed.error.issues[0];
	const [first] = issue?.path ?? [];
	let file = `${slug}${CAUSE_FILE_SUFFIX}`;
	if (first === "previous") {
		file = `${slug}${PREVIOUS_FILE_SUFFIX}`;
	} else if (first === "work" && isKind(cause.kind)) {
		file = WORK_RECORDS[cause.kind].file;
	}
	const where = issue?.path.join(".") ?? "";
	return { error: `${file}: ${where === "" ? "" : `${where} `}${issue?.message ?? "invalid"}` };
}

const GIT_ENV = { ...process.env, GIT_TERMINAL_PROMPT: "0", GIT_OPTIONAL_LOCKS: "0" };

// oxlint-disable-next-line typescript/strict-void-return -- Node types `execFile`'s promise form through `util.promisify.custom`, which tsc resolves to `{ stdout, stderr }` and the rule does not read.
const git = promisify(execFile);

/**
 * The change from `from` to `to` in `repo`, when `from` is an ancestor of `to`. Otherwise, such as
 * after a force push, it is not known, and the reason says why. An empty change is a known one: the
 * work did not change.
 */
export async function changeSince(
	repo: string,
	from: string,
	to: string,
): Promise<{ change: Map<string, DiffFile> } | { unknown: string }> {
	if (!isCommit(from) || !isCommit(to)) {
		return { unknown: "a head is not a commit id" };
	}
	try {
		await git("git", ["-C", repo, "merge-base", "--is-ancestor", from, to], { env: GIT_ENV });
	} catch {
		// Exit 1: not an ancestor. Any other failure, such as a missing commit, proves nothing either.
		return {
			unknown: `the previous head ${from.slice(0, 12)} is not an ancestor of the reviewed head`,
		};
	}
	try {
		const { stdout } = await git(
			"git",
			[
				"-C",
				repo,
				"--no-pager",
				"-c",
				"core.quotePath=false",
				"diff",
				"--no-color",
				"--no-ext-diff",
				"--find-renames=50%",
				from,
				to,
			],
			{ env: GIT_ENV, maxBuffer: MAX_CHANGE_BYTES, encoding: "utf8" },
		);
		return { change: parseDiff(stdout) };
	} catch (error) {
		return { unknown: `git diff failed: ${messageOf(error)}` };
	}
}

interface InputReaderOptions {
	/** The folder of the staged programs, which holds each practice's cause and previous result. */
	practicesDir: string;
	contextDir: string;
	repo: string;
	log: (line: string) => void;
}

/**
 * Reads and validates the inputs of each practice for one host, each file once. The record of the
 * work and each change since a previous head are shared by every practice that reads them.
 */
export class InputReader {
	readonly #records = new Map<Kind, Promise<{ value: unknown } | { error: string }>>();
	readonly #changes = new Map<
		string,
		Promise<{ change: Map<string, DiffFile> } | { unknown: string }>
	>();

	readonly #options: InputReaderOptions;

	constructor(options: InputReaderOptions) {
		this.#options = options;
	}

	async read(slug: string): Promise<ScriptInputs> {
		const { practicesDir, contextDir } = this.#options;
		const cause = await readJson(path.join(practicesDir, `${slug}${CAUSE_FILE_SUFFIX}`));
		if ("error" in cause) {
			return cause;
		}
		const previous = await readJson(path.join(practicesDir, `${slug}${PREVIOUS_FILE_SUFFIX}`));
		if ("error" in previous) {
			return previous;
		}
		const kind = isJsonObject(cause.value) ? cause.value.kind : undefined;
		let work: unknown;
		if (isKind(kind)) {
			const record = await this.#record(kind, contextDir);
			if ("error" in record) {
				return record;
			}
			work = record.value;
		}
		const parsed = parseInputs(slug, cause.value, previous.value, work);
		if ("error" in parsed) {
			return parsed;
		}
		const { inputs } = parsed;
		if (inputs.kind !== "scm.pull_request" || inputs.previous?.head === undefined) {
			return { inputs };
		}
		const since = await this.#change(inputs.previous.head, inputs.work.commit_sha);
		if ("unknown" in since) {
			this.#options.log(`  [${slug}] no change since the previous review: ${since.unknown}`);
			return { inputs };
		}
		return { inputs, changeSince: since.change };
	}

	async #record(kind: Kind, contextDir: string) {
		let record = this.#records.get(kind);
		if (record === undefined) {
			record =
				contextDir === ""
					? Promise.resolve({ error: `${WORK_RECORDS[kind].file} was not staged` })
					: readJson(path.join(contextDir, WORK_RECORDS[kind].file));
			this.#records.set(kind, record);
		}
		return record;
	}

	async #change(from: string, to: string) {
		let change = this.#changes.get(from);
		if (change === undefined) {
			change = changeSince(this.#options.repo, from, to);
			this.#changes.set(from, change);
		}
		return change;
	}
}
