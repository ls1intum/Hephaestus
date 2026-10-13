/**
 * Runs one admitted practice's program under the scope the host supplies: the built-in in this
 * process, a custom program in a child process. Either way the program gets the same context, and its
 * final value is checked as one candidate. The host keeps one practice and one review active.
 */
import { createHash } from "node:crypto";

import {
	LEGACY_CONTRACT_ERROR,
	parseCandidate,
	parseMeta,
	parseReviewOptions,
	type Candidate,
	type ProgramCalls,
	type ProgramKind,
	type RunStatus,
	type StopReason,
} from "./contract.ts";
import { noReceipts, type Receipts } from "./evidence-reader.ts";
import type { ProgramJob } from "./ipc.ts";
import { isJsonObject } from "./json.ts";
import { definePracticeScript, reasonOf, UnratedError } from "./practice-script.ts";
import { programContext } from "./program-context.ts";
import {
	abortReasonOf,
	acceptModels,
	noCalls,
	runCustomProgram,
	type ProgramExecution,
	type ProgramServices,
	type SlotReport,
} from "./script-run.ts";

/** The program of a practice without a custom one: one review of the practice, as it is. */
export const BUILTIN_PROGRAM = definePracticeScript({ meta: {}, run: async (ctx) => ctx.review() });

/** Which program a practice runs: its staged custom program, or the built-in. */
export interface PracticeProgram {
	/** The staged `<slug>.ts`, or null for the built-in. */
	script: { path: string; source: Buffer } | null;
}

/** How one practice's program ran, and the candidate it returned when it passed the host's checks. */
export interface ProgramOutcome {
	candidate?: Candidate;
	program: { kind: ProgramKind; digest: string | null };
	models: SlotReport[];
	status: RunStatus;
	stopReason: StopReason | null;
	/** Why the program failed; only on `failed`. */
	error?: string;
	calls: ProgramCalls;
	coverageReached: Receipts;
	durationMs: number | null;
}

/** The SHA-256 of a staged program's bytes. */
export const programDigest = (source: Buffer): string =>
	createHash("sha256").update(source).digest("hex");

/** A value that still has the form of the retired leads return. */
function isLeadsReturn(value: unknown): boolean {
	return isJsonObject(value) && Object.hasOwn(value, "leads") && !Object.hasOwn(value, "outcome");
}

/**
 * The outcome of a practice the host did not start: the attempt's time or budget ran out first, or
 * the attempt was cancelled.
 */
export function notReached(
	program: PracticeProgram,
	stopReason: StopReason,
	receipts: Receipts = noReceipts(),
): ProgramOutcome {
	return {
		program: identity(program),
		models: [],
		status: "not-reached",
		stopReason,
		calls: noCalls(),
		coverageReached: { ...receipts },
		durationMs: null,
	};
}

function identity(program: PracticeProgram): ProgramOutcome["program"] {
	return program.script === null
		? { kind: "builtin", digest: null }
		: { kind: "custom", digest: programDigest(program.script.source) };
}

/** Run the built-in program in this process, with the same context and services as a child. */
async function runBuiltin(job: ProgramJob, services: ProgramServices): Promise<ProgramExecution> {
	const calls = noCalls();
	const parsed = parseMeta(BUILTIN_PROGRAM.meta);
	const report = "meta" in parsed ? acceptModels(parsed.meta, services.models).report : [];
	const signal = AbortSignal.any([
		services.signal,
		AbortSignal.timeout(Math.max(0, job.deadline - Date.now())),
	]);
	const ctx = programContext(job, {
		read: async (request) => services.reader.answer(request),
		review: async (options) => {
			const checked = parseReviewOptions(options);
			if ("error" in checked) {
				throw new UnratedError("error", checked.error);
			}
			calls.review += 1;
			return services.review(checked.options, signal);
		},
		models: {},
		tokensLeft: () => services.budget.left,
		log: (text) => {
			services.log(`  [${job.slug}] ${text}`);
		},
		abortSignal: signal,
	});
	try {
		signal.throwIfAborted();
		if (Date.now() >= job.deadline) {
			throw new UnratedError("deadline", "the program reached its deadline");
		}
		const value = await BUILTIN_PROGRAM.run(ctx);
		signal.throwIfAborted();
		if (Date.now() >= job.deadline) {
			throw new UnratedError("deadline", "the program reached its deadline");
		}
		return { value, models: report, calls };
	} catch (error) {
		const stopReason = signal.aborted ? abortReasonOf(signal) : reasonOf(error);
		return {
			failure: { stopReason, error: error instanceof Error ? error.message : String(error) },
			models: report,
			calls,
		};
	}
}

/**
 * Run one practice's program and check what it returned. Its candidate passes only as a whole: a
 * value that is not a candidate, or host coverage that no review of this practice returned, fails
 * the practice and admits nothing for it.
 */
export async function runPracticeProgram(
	program: PracticeProgram,
	job: ProgramJob,
	services: ProgramServices & { receipts: Receipts },
): Promise<ProgramOutcome> {
	const started = Date.now();
	/** The coverage each review of this practice returned: the only host coverage its candidate may carry. */
	const returned = new Set<string>();
	const hosted: ProgramServices = {
		...services,
		review: async (options, signal) => {
			const candidate = await services.review(options, signal);
			returned.add(JSON.stringify(candidate.coverage));
			return candidate;
		},
	};
	let execution: ProgramExecution;
	try {
		execution =
			program.script === null
				? await runBuiltin(job, hosted)
				: await runCustomProgram(program.script.path, job, hosted);
	} catch (error) {
		execution = {
			failure: {
				stopReason: "error",
				error: error instanceof Error ? error.message : String(error),
			},
			models: [],
			calls: noCalls(),
		};
	}
	const finished = (
		status: RunStatus,
		stopReason: StopReason | null,
		parts: { candidate?: Candidate; error?: string } = {},
	): ProgramOutcome => ({
		...parts,
		program: identity(program),
		models: execution.models,
		status,
		stopReason,
		calls: execution.calls,
		coverageReached: { ...services.receipts },
		durationMs: Date.now() - started,
	});
	const { failure } = execution;
	if (failure !== undefined) {
		return failure.skipped === true
			? finished("skipped", failure.stopReason)
			: finished("failed", failure.stopReason, { error: failure.error });
	}
	const parsed = parseCandidate(execution.value, (citation) =>
		services.reader.citationIdentity(citation),
	);
	if (!parsed.ok) {
		return finished("failed", "invalid-candidate", {
			error: isLeadsReturn(execution.value)
				? LEGACY_CONTRACT_ERROR
				: `the program returned no valid result: ${parsed.reasonCode}: ${parsed.message}`,
		});
	}
	const { candidate } = parsed;
	if (candidate.coverage.source === "host" && !returned.has(JSON.stringify(candidate.coverage))) {
		return finished("failed", "invalid-candidate", {
			error: "the result carries host coverage that no review of this practice returned",
		});
	}
	return finished("ok", null, { candidate });
}
