import type { Candidate, ReviewOptions, SlotModels } from "./contract.ts";
import { registerDomainAccess } from "./domain-access.ts";
import { MAX_LOG_CHARS, type ProgramJob, type ReadRequest, type ReadValues } from "./ipc.ts";
import { UnratedError, type Evidence, type PracticeScriptContext } from "./practice-script.ts";

export interface ProgramPort {
	read: <R extends ReadRequest>(request: R) => Promise<ReadValues[R["op"]]>;
	review: (options: ReviewOptions | undefined) => Promise<Candidate>;
	models: Partial<SlotModels>;
	tokensLeft: () => number;
	log: (text: string) => void;
	abortSignal: AbortSignal;
}

export function programContext(job: ProgramJob, port: ProgramPort): PracticeScriptContext {
	const evidence: Evidence = {
		tools: {},
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
	const context: PracticeScriptContext = {
		...job.inputs,
		evidence,
		models: port.models,
		budget: {
			remaining: () => ({ tokens: port.tokensLeft(), ms: Math.max(0, job.deadline - Date.now()) }),
		},
		now: job.now,
		abortSignal: port.abortSignal,
		log: (text) => port.log(text.slice(0, MAX_LOG_CHARS)),
		review: Object.assign(async (options?: ReviewOptions): Promise<Candidate> => {
			if (reviewing) {
				throw new UnratedError("refused", "a review of this practice is already running");
			}
			reviewing = true;
			try {
				return await port.review(options);
			} finally {
				reviewing = false;
			}
		}, job.inputs.review),
	};
	registerDomainAccess(context, job.files, port);
	return context;
}
