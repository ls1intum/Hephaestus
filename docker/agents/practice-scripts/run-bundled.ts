/**
 * Run one bundled practice script in the test process, as `child.ts` runs it, with no model. A test
 * gives the record of the work, the cause, the change, and the files of the checkout and of the context
 * folder. The harness validates the inputs as the runner validates them, and the result as the runner
 * checks it: against the result schema, and each lead kind against `meta.kinds`.
 */
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";

import {
	KINDS,
	MOMENTS,
	practiceInputsSchema,
	practiceScriptResultSchema,
	type Kind,
	type Moment,
	type PracticeScriptResult,
} from "./lib/contract.ts";
import { evidenceTools } from "./lib/evidence-tools.ts";
import { isJsonObject } from "./lib/json.ts";
import {
	isPracticeScriptDefinition,
	type ModelNeeds,
	type PracticeScriptContext,
	type PracticeScriptDefinition,
} from "./lib/practice-script.ts";
import type { DiffFile } from "./lib/types.ts";
import { stagePracticeScripts } from "./stage.ts";

const SCRIPTS_DIR = path.resolve(
	import.meta.dirname,
	"../../../server/application/src/main/resources/practices/scripts",
);

const ADMITTED_AT = "2026-01-01T00:00:00Z";

/** A full commit id, for a record or a previous head. */
export const COMMIT = "a".repeat(40);

/** A pull request record with the fields that every record holds, and `fields` over them. */
export function pullRequestRecord(fields: Record<string, unknown> = {}): Record<string, unknown> {
	return {
		basis: { admission_fields: [], other_fields: "CAPTURE" },
		pr_number: 1,
		pr_url: "https://example.org/team/project/pull/1",
		repository_full_name: "team/project",
		source_branch: "feature",
		target_branch: "main",
		commit_sha: COMMIT,
		subject_role: "AUTHOR",
		is_draft: false,
		is_merged: false,
		additions: 0,
		deletions: 0,
		changed_files: 0,
		labels: [],
		assignees: [],
		...fields,
	};
}

/** An issue record with each field that the provider did not record as null, and `fields` over them. */
export function issueRecord(fields: Record<string, unknown> = {}): Record<string, unknown> {
	return {
		issue_number: 1,
		title: null,
		body: "",
		state: "OPEN",
		state_reason: null,
		html_url: null,
		repository_full_name: "team/project",
		author: null,
		author_id: null,
		issue_type: null,
		is_locked: false,
		sub_issues_total: null,
		sub_issues_completed: null,
		milestone: null,
		closed_at: null,
		labels: [],
		assignees: [],
		...fields,
	};
}

/** The cause file of a review of `kind` that a person requested, in the review state `state`. */
export function requested(kind: Kind = "scm.pull_request", state: Record<string, string> = {}) {
	return {
		kind,
		subject: { role: "AUTHOR" },
		cause: { type: "requested", origin: "MANUAL", state, admittedAt: ADMITTED_AT },
	};
}

/** The cause file of a review at `signal`, to which the practice is bound. */
export function moment(signal: Moment, type: "moment" | "recheck" = "moment") {
	const kind = KINDS.find((candidate) => MOMENTS[candidate].some((m) => m === signal));
	return {
		kind,
		subject: { role: "AUTHOR" },
		cause: { type, signal, origin: "LIVE", state: {}, admittedAt: ADMITTED_AT },
	};
}

/** Files by their path in a folder: a string is the text of the file, and any other value is JSON. */
export type Files = Record<string, unknown>;

export interface RunInputs {
	/**
	 * The record of the work, which the script reads as `ctx.work`. For a pull request or an issue,
	 * these fields go over a record that holds every required field.
	 */
	work?: Record<string, unknown>;
	/** The cause file: `kind`, `subject` and `cause`. The default is a requested pull request review. */
	cause?: unknown;
	previous?: unknown;
	/** The checkout: a folder, or the files of a new one. The default is an empty checkout. */
	repo?: string | Files;
	change?: ReadonlyMap<string, DiffFile>;
	changeDir?: string;
	/** The context folder: a folder, or the files of a new one. The default is no folder. */
	context?: string | Files;
	contextReference?: string;
}

async function writeFiles(dir: string, files: Files): Promise<void> {
	for (const [name, value] of Object.entries(files)) {
		const file = path.join(dir, name);
		await mkdir(path.dirname(file), { recursive: true });
		await writeFile(file, typeof value === "string" ? value : JSON.stringify(value));
	}
}

/** The folder that a test gives, or a new folder under `root` that holds its files. */
async function folder(root: string, name: string, given: string | Files | undefined) {
	if (typeof given === "string") {
		return given;
	}
	const dir = path.join(root, name);
	await mkdir(dir, { recursive: true });
	await writeFiles(dir, given ?? {});
	return dir;
}

/** The record of the work: `work` over the full record of a pull request or an issue. */
function recordOf(kind: unknown, work: Record<string, unknown> | undefined): unknown {
	if (kind === "scm.issue") {
		return issueRecord(work);
	}
	return kind === "scm.pull_request" ? pullRequestRecord(work) : work;
}

/** Stage the bundled script beside a `lib` link, as the runner stages it, and import it. */
async function load(slug: string): Promise<{
	definition: PracticeScriptDefinition<ModelNeeds>;
	root: string;
}> {
	const { root, practices } = await stagePracticeScripts(
		{ [slug]: await readFile(path.join(SCRIPTS_DIR, `${slug}.ts`), "utf8") },
		{},
	);
	const mod: unknown = await import(path.join(practices, `${slug}.ts`));
	const exported: unknown = isJsonObject(mod) ? mod.default : undefined;
	if (!isPracticeScriptDefinition(exported)) {
		throw new Error(`${slug} does not export a definePracticeScript definition`);
	}
	return { definition: exported, root };
}

/** Run the bundled script `slug` with no model on validated inputs, and return its checked result. */
export async function runBundled(
	slug: string,
	inputs: RunInputs = {},
): Promise<PracticeScriptResult> {
	const { definition, root } = await load(slug);
	try {
		const cause = inputs.cause ?? requested();
		if (!isJsonObject(cause)) {
			throw new TypeError("the cause must be an object");
		}
		const parsed = practiceInputsSchema.parse({
			...cause,
			previous: inputs.previous ?? null,
			work: recordOf(cause.kind, inputs.work),
		});
		const repo = await folder(root, "repo", inputs.repo);
		const contextDir =
			inputs.context === undefined ? "" : await folder(root, "context", inputs.context);
		const common = {
			practice: { slug },
			context: { dir: contextDir, reference: inputs.contextReference ?? "" },
			now: ADMITTED_AT,
			models: {},
			tools: evidenceTools(repo),
			remaining: () => ({ tokens: 0, ms: 60_000 }),
			abortSignal: new AbortController().signal,
			log: () => undefined,
		};
		const ctx: PracticeScriptContext<ModelNeeds> =
			parsed.kind === "scm.pull_request"
				? {
						...common,
						...parsed,
						repo,
						change: inputs.change ?? new Map(),
						changeDir: inputs.changeDir ?? "",
					}
				: { ...common, ...parsed };
		const result = practiceScriptResultSchema.parse(await definition.run(ctx));
		const undeclared = result.leads.filter(
			(lead) => !Object.hasOwn(definition.meta.kinds, lead.kind),
		);
		if (undeclared.length > 0) {
			throw new Error(
				`${slug} returned undeclared lead kinds: ${undeclared.map((lead) => lead.kind).join(", ")}`,
			);
		}
		return result;
	} finally {
		await rm(root, { recursive: true, force: true });
	}
}
