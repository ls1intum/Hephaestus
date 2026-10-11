/**
 * Run one bundled practice script in the test process, as `child.ts` runs it, with no model. A test
 * gives the record of the work, the cause and the change; the inputs are validated as the runner
 * validates them.
 */
import { readFile } from "node:fs/promises";
import path from "node:path";

import {
	KINDS,
	MOMENTS,
	practiceInputsSchema,
	type Kind,
	type Moment,
	type PracticeScriptResult,
} from "./lib/contract.ts";
import { evidenceTools } from "./lib/evidence-tools.ts";
import { isJsonObject } from "./lib/json.ts";
import type {
	ModelNeeds,
	PracticeScriptContext,
	PracticeScriptDefinition,
} from "./lib/practice-script.ts";
import type { DiffFile } from "./lib/types.ts";
import { stagePracticeScripts } from "./stage.ts";

const SCRIPTS_DIR = path.resolve(
	import.meta.dirname,
	"../../../server/application/src/main/resources/practices/scripts",
);

const ADMITTED_AT = "2026-01-01T00:00:00Z";

function isDefinition(value: unknown): value is PracticeScriptDefinition<ModelNeeds> {
	return isJsonObject(value) && typeof value.run === "function" && isJsonObject(value.meta);
}

/**
 * Stage a bundled script beside a `lib` link and import it. The caller removes `root`.
 */
export async function loadDefinition(
	slug: string,
): Promise<{ definition: PracticeScriptDefinition<ModelNeeds>; root: string }> {
	const { root, practices } = await stagePracticeScripts(
		{ [slug]: await readFile(path.join(SCRIPTS_DIR, `${slug}.ts`), "utf8") },
		{},
	);
	const mod: unknown = await import(path.join(practices, `${slug}.ts`));
	const exported: unknown = isJsonObject(mod) ? mod.default : undefined;
	if (!isDefinition(exported)) {
		throw new Error(`${slug} does not export a definePracticeScript definition`);
	}
	return { definition: exported, root };
}

/** A pull request record with the fields that every record holds, and `fields` over them. */
export function pullRequestRecord(fields: Record<string, unknown> = {}): Record<string, unknown> {
	return {
		basis: { admission_fields: [], other_fields: "CAPTURE" },
		pr_number: 1,
		pr_url: "https://example.org/team/project/pull/1",
		repository_full_name: "team/project",
		source_branch: "feature",
		target_branch: "main",
		commit_sha: "a".repeat(40),
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

/** The cause file of a review of `kind` that a person requested. */
export function requested(kind: Kind = "scm.pull_request") {
	return {
		kind,
		subject: { role: "AUTHOR" },
		cause: { type: "requested", origin: "MANUAL", state: {}, admittedAt: ADMITTED_AT },
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

export interface RunInputs {
	/** The record of the work, which the script reads as `ctx.work`. */
	work: unknown;
	/** The cause file: `kind`, `subject` and `cause`. The default is a requested pull request review. */
	cause?: unknown;
	previous?: unknown;
	repo?: string;
	change?: ReadonlyMap<string, DiffFile>;
	changeDir?: string;
	contextDir?: string;
	contextReference?: string;
}

/** Run the script with no model on validated inputs, and return what `run` returned. */
export async function runDefinition(
	definition: PracticeScriptDefinition<ModelNeeds>,
	inputs: RunInputs,
): Promise<PracticeScriptResult> {
	const cause = inputs.cause ?? requested();
	if (!isJsonObject(cause)) {
		throw new TypeError("the cause must be an object");
	}
	const parsed = practiceInputsSchema.parse({
		...cause,
		previous: inputs.previous ?? null,
		work: inputs.work,
	});
	const repo = inputs.repo ?? "";
	const common = {
		practice: { slug: "test" },
		context: { dir: inputs.contextDir ?? "", reference: inputs.contextReference ?? "" },
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
	return definition.run(ctx);
}
