/**
 * Run one bundled practice program in the test process, with the context `child.ts` gives it and a
 * recording fake `review()`: no model, no session. A test gives the record of the work, the cause, the
 * change, and the files of the checkout and of the context folder. The harness validates the inputs as
 * the host validates them, answers reads as the host answers them, checks each review input and
 * resolves its leads as the host does, and returns what the program asked the review and what it
 * returned.
 */
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { isDeepStrictEqual } from "node:util";

import {
	KINDS,
	MOMENTS,
	parseCandidate,
	parseReviewOptions,
	practiceInputsSchema,
	type Candidate,
	type Kind,
	type Moment,
	type ReviewOptions,
} from "./lib/contract.ts";
import { EvidenceReader, noReceipts, type EvidenceSources } from "./lib/evidence-reader.ts";
import { isJsonObject } from "./lib/json.ts";
import { resolveLeads } from "./lib/leads.ts";
import {
	isPracticeScriptDefinition,
	type ModelNeeds,
	type PracticeScriptDefinition,
} from "./lib/practice-script.ts";
import { programContext } from "./lib/program-context.ts";
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

/** The practice revision and authored scope that every staged cause names. */
const BOUND = {
	practice: { revisionId: 1 },
	scope: { key: "scm.pull_request:1:AUTHOR", mode: "change", description: "the reviewed change" },
} as const;

/** The cause file of a review of `kind` that a person requested, in the review state `state`. */
export function requested(kind: Kind = "scm.pull_request", state: Record<string, string> = {}) {
	return {
		kind,
		subject: { role: "AUTHOR" },
		...BOUND,
		cause: { type: "requested", origin: "MANUAL", state, admittedAt: ADMITTED_AT },
	};
}

/** The cause file of a review at `signal`, to which the practice is bound. */
export function moment(signal: Moment, type: "moment" | "recheck" = "moment") {
	const kind = KINDS.find((candidate) => MOMENTS[candidate].some((m) => m === signal));
	return {
		kind,
		subject: { role: "AUTHOR" },
		...BOUND,
		cause: { type, signal, origin: "LIVE", state: {}, admittedAt: ADMITTED_AT },
	};
}

/** The result the fake review returns: an open question about the cited change, with host coverage. */
export const REVIEWED: Candidate = {
	outcome: "UNDETERMINED",
	summary: "The fake review leaves the practice open",
	explanation: "The test harness answers every review with this result.",
	citations: [
		{
			sourceKind: "scm.pull-request.core",
			artifactPath: "context/metadata.json",
			path: "context/metadata.json",
			startLine: 1,
			endLine: 1,
			quote: "{",
		},
	],
	evidence: {
		undecidability: [
			{ openQuestion: "What would the review decide?", wouldSettleIt: "A review session." },
		],
	},
	coverage: { source: "host", reads: 0, truncatedReads: 0, failedReads: 0, complete: true },
};

/** Files by their path in a folder: a string is the text of the file, and any other value is JSON. */
export type Files = Record<string, unknown>;

export interface RunInputs {
	/**
	 * The record of the work, which the program reads as `ctx.work`. For a pull request or an issue,
	 * these fields go over a record that holds every required field.
	 */
	work?: Record<string, unknown>;
	/** The cause file: `kind`, `subject`, `practice`, `scope` and `cause`. The default is a requested pull request review. */
	cause?: unknown;
	previous?: unknown;
	/** The checkout: a folder, or the files of a new one. The default is an empty checkout. */
	repo?: string | Files;
	/** The captured change. The default is a captured empty change; null is a change that was not captured. */
	change?: ReadonlyMap<string, DiffFile> | null;
	/** The context folder: a folder, or the files of a new one. The default is no folder. */
	context?: string | Files;
	contextReference?: string;
	/** What the fake review returns. */
	reviewed?: Candidate;
}

/** What a run of a bundled program did: the input of each review it asked for, and what it returned or threw. */
export interface BundledRun {
	reviews: ReviewOptions[];
	outcome: { candidate: Candidate } | { error: unknown };
}

/** The one review a bundled program asks for, and the result it returned. */
export interface BundledReview {
	review: ReviewOptions;
	candidate: Candidate;
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

/** Stage the bundled program beside a `lib` link, as the server stages it, and import it. */
async function load(slug: string): Promise<{
	definition: PracticeScriptDefinition<ModelNeeds>;
	labels: Record<string, string>;
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
	const labels: unknown = isJsonObject(mod) ? mod.LABELS : undefined;
	return {
		definition: exported,
		labels: isJsonObject(labels)
			? Object.fromEntries(
					Object.entries(labels).flatMap(([key, label]) =>
						typeof label === "string" ? [[key, label]] : [],
					),
				)
			: {},
		root,
	};
}

/**
 * The key that a bundled program's `LABELS` export gives each lead label, so that a test names a
 * lead by its key rather than by its sentence. A label the program does not export fails the test.
 */
export async function leadKeys(slug: string): Promise<(label: string) => string> {
	const { labels, root } = await load(slug);
	await rm(root, { recursive: true, force: true });
	const byLabel = new Map(Object.entries(labels).map(([key, label]) => [label, key]));
	return (label) => {
		const key = byLabel.get(label);
		if (key === undefined) {
			throw new Error(`${slug} exports no LABELS entry for "${label}"`);
		}
		return key;
	};
}

/**
 * Run the bundled program `slug` on validated inputs with the fake review, and return each review it
 * asked for and what it returned or threw.
 */
export async function runBundledProgram(slug: string, inputs: RunInputs = {}): Promise<BundledRun> {
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
			inputs.context === undefined ? null : await folder(root, "context", inputs.context);
		const change = inputs.change === undefined ? new Map<string, DiffFile>() : inputs.change;
		const sources: EvidenceSources = {
			repo,
			headRevision: COMMIT,
			treeArtifact: "repos/reviewed/.git/HEAD",
			contextDir,
			contextReference: inputs.contextReference ?? "",
			change: change === null ? null : { base: "b".repeat(40), head: COMMIT, files: change },
			sourceKindOf: () => "scm.pull-request.core",
		};
		const reader = new EvidenceReader(sources, noReceipts());
		const reviews: ReviewOptions[] = [];
		const reviewed = inputs.reviewed ?? REVIEWED;
		const ctx = programContext(
			{
				slug,
				inputs: parsed,
				criteria: "",
				files:
					parsed.kind === "scm.pull_request"
						? {
								head: { revision: COMMIT },
								change:
									change === null
										? null
										: { base: "b".repeat(40), head: COMMIT, files: new Map(change) },
							}
						: null,
				now: ADMITTED_AT,
				deadline: Date.now() + 60_000,
			},
			{
				read: async (request) => reader.answer(request),
				review: async (options) => {
					const checked = parseReviewOptions(options);
					if ("error" in checked) {
						throw new Error(checked.error);
					}
					await resolveLeads(checked.options.leads ?? [], sources);
					reviews.push(checked.options);
					return reviewed;
				},
				models: {},
				tokensLeft: () => 0,
				log: () => undefined,
				abortSignal: new AbortController().signal,
			},
		);
		try {
			const returned = parseCandidate(await definition.run(ctx));
			if (!returned.ok) {
				throw new Error(
					`${slug} returned no candidate: ${returned.reasonCode}: ${returned.message}`,
				);
			}
			return { reviews, outcome: { candidate: returned.candidate } };
		} catch (error) {
			return { reviews, outcome: { error } };
		}
	} finally {
		await rm(root, { recursive: true, force: true });
	}
}

/**
 * Run the bundled program `slug` and return the input of the one review it asks for. It fails unless
 * the program asks exactly one review and returns that review's result unchanged.
 */
export async function runBundled(slug: string, inputs: RunInputs = {}): Promise<ReviewOptions> {
	const { review } = await runBundledReview(slug, inputs);
	return review;
}

/** {@link runBundled}, with the candidate the program returned. */
export async function runBundledReview(
	slug: string,
	inputs: RunInputs = {},
): Promise<BundledReview> {
	const { reviews, outcome } = await runBundledProgram(slug, inputs);
	if ("error" in outcome) {
		throw outcome.error;
	}
	const [review, ...more] = reviews;
	if (review === undefined || more.length > 0) {
		throw new Error(`${slug} asked ${reviews.length} reviews; a bundled program asks exactly one`);
	}
	if (!isDeepStrictEqual(outcome.candidate, inputs.reviewed ?? REVIEWED)) {
		throw new Error(`${slug} did not return its review's result unchanged`);
	}
	return { review, candidate: outcome.candidate };
}
