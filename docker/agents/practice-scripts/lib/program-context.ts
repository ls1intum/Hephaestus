/**
 * The context a practice program runs with, built from the host's job data and a port to the host.
 * The child of a custom program reaches the host over IPC; the built-in program reaches it directly.
 * Both get the same context: one shape, one set of grants.
 */
import type { Candidate, ReviewOptions, SlotModels } from "./contract.ts";
import { evidenceTools } from "./evidence-tools.ts";
import { MAX_LOG_CHARS, type ProgramJob, type ReadRequest, type ReadValues } from "./ipc.ts";
import {
	UnratedError,
	type ChangeView,
	type Evidence,
	type FileTree,
	type Files,
	type ModelNeeds,
	type PracticeScriptContext,
} from "./practice-script.ts";

/** How a program's context reaches the host. */
export interface ProgramPort {
	read: <R extends ReadRequest>(request: R) => Promise<ReadValues[R["op"]]>;
	review: (options: ReviewOptions | undefined) => Promise<Candidate>;
	models: Partial<SlotModels>;
	tokensLeft: () => number;
	log: (text: string) => void;
	abortSignal: AbortSignal;
}

function headTree(revision: string, port: ProgramPort): FileTree {
	const tree = "head";
	return {
		revision,
		read: async (path) => port.read({ op: "read", tree, path }),
		list: async (glob) => port.read({ op: "list", tree, ...(glob === undefined ? {} : { glob }) }),
		grep: async (pattern, options = {}) => port.read({ op: "grep", tree, pattern, ...options }),
		cite: async (path, startLine, endLine) =>
			port.read({
				op: "cite",
				source: "tree",
				tree,
				path,
				startLine,
				...(endLine === undefined ? {} : { endLine }),
			}),
	};
}

function filesOf(job: ProgramJob, port: ProgramPort): Files | null {
	if (job.files === null) {
		return null;
	}
	const { change } = job.files;
	const view: ChangeView | null =
		change === null
			? null
			: {
					base: change.base,
					head: change.head,
					files: change.files,
					cite: async (path, side, startLine, endLine) =>
						port.read({
							op: "cite",
							source: "change",
							path,
							side,
							startLine,
							...(endLine === undefined ? {} : { endLine }),
						}),
				};
	return { head: headTree(job.files.head.revision, port), base: null, change: view };
}

/** The context of one practice's program. */
export function programContext(
	job: ProgramJob,
	port: ProgramPort,
): PracticeScriptContext<ModelNeeds> {
	const files = filesOf(job, port);
	const evidence: Evidence = {
		tools: evidenceTools(files?.head ?? null),
		record: async (name) => port.read({ op: "record", name }),
		citeRecord: async (name, startLine, endLine) =>
			port.read({
				op: "cite",
				source: "record",
				name,
				startLine,
				...(endLine === undefined ? {} : { endLine }),
			}),
	};
	let reviewing = false;
	const { inputs } = job;
	const common = {
		files,
		evidence,
		models: port.models,
		budget: {
			remaining: () => ({
				tokens: port.tokensLeft(),
				ms: Math.max(0, job.deadline - Date.now()),
			}),
		},
		now: job.now,
		abortSignal: port.abortSignal,
		log: (text: string) => {
			port.log(text.slice(0, MAX_LOG_CHARS));
		},
		review: async (options?: ReviewOptions): Promise<Candidate> => {
			if (reviewing) {
				throw new UnratedError("refused", "a review of this practice is already running");
			}
			reviewing = true;
			try {
				return await port.review(options);
			} finally {
				reviewing = false;
			}
		},
		practice: {
			slug: job.slug,
			revisionId: inputs.practice.revisionId,
			criteria: job.criteria,
		},
	};
	if (inputs.kind === "scm.pull_request") {
		const { changeSince } = job;
		return {
			...inputs,
			...common,
			previous:
				inputs.previous === null || changeSince === undefined
					? inputs.previous
					: { ...inputs.previous, changeSince },
		};
	}
	return { ...inputs, ...common };
}
