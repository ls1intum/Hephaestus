/**
 * The host's side of one custom program's process: its grants, its requests, its deadline and its
 * final value. The host answers each request once, keeps one review active, and ends the process at
 * the practice's deadline.
 */
import { fork } from "node:child_process";
import { once } from "node:events";
import { existsSync } from "node:fs";
import path from "node:path";

import {
	MODEL_SLOTS,
	parseMeta,
	parseReviewOptions,
	type Candidate,
	type ModelSlot,
	type ParsedMeta,
	type Reason,
	type ReviewOptions,
	type SlotModels,
	type StopReason,
} from "./contract.ts";
import type { EvidenceReader } from "./evidence-reader.ts";
import {
	childMessageSchema,
	MAX_IPC_MESSAGE_BYTES,
	type AbortReason,
	type ChildMessage,
	type HostMessage,
	type ProgramJob,
} from "./ipc.ts";
import { isJsonObject } from "./json.ts";
import { modelCall, type ModelBudget } from "./models-host.ts";
import { reasonOf, UnratedError } from "./practice-script.ts";

/** Heap per program, in MiB. */
const CHILD_HEAP_MB = 256;
/** A program's process is killed this long after its deadline, so a program that unwinds still returns. */
export const RETURN_MS = 5000;
/** Requests a child may have in flight at once. */
const MAX_IN_FLIGHT = 32;
/** The most of a failed program's error that its outcome keeps. */
export const MAX_ERROR_CHARS = 2000;
/** The most of a child's own output that the log keeps. */
const MAX_OUTPUT_CHARS = 64 * 1024;

/** One declared model slot of a program: what it needs, whether a model was bound, and its calls not rated. */
export interface SlotReport {
	slot: ModelSlot;
	need: "required" | "optional";
	bound: boolean;
	notRated: Partial<Record<Reason, number>>;
}

/** What the host gives one practice's program. */
export interface ProgramServices {
	/** The practice's reads, with their receipts. */
	reader: EvidenceReader;
	/** The practice's direct models, with the credential: every call names the practice to the proxy. */
	models: Partial<SlotModels>;
	/** The attempt's direct model account. */
	budget: ModelBudget;
	/** Run one review of this practice. It rejects with `UnratedError` when the session gives no result. */
	review: (options: ReviewOptions, signal: AbortSignal) => Promise<Candidate>;
	/** The attempt's cancellation: deadline, budget or cancelled. */
	signal: AbortSignal;
	log: (line: string) => void;
}

/** What one execution of a program did, before the host checks its value. */
export interface ProgramExecution {
	/** The value `run` returned; absent when it returned nothing. */
	value?: unknown;
	/** Why it returned nothing. */
	failure?: { stopReason: StopReason; error: string; skipped?: true };
	models: SlotReport[];
	calls: { review: number } & Record<ModelSlot, number>;
}

/** A request the host still owes a reply. */
interface OpenRequest {
	kind: "model" | "read" | "review";
	call: AbortController;
	/** The slot of a model request. */
	slot?: ModelSlot;
	/** The host already failed it and counted it when it stopped the program. */
	refused?: true;
}

const messageOf = (error: unknown) => (error instanceof Error ? error.message : String(error));
const cut = (text: string) => text.slice(0, MAX_ERROR_CHARS).toWellFormed();

export const noCalls = (): ProgramExecution["calls"] => ({
	review: 0,
	chat: 0,
	decision: 0,
	embedding: 0,
	reranking: 0,
});

/**
 * The folders a program's process may read: the toolkit install, the dependency folders that the
 * library resolves from, and the given inputs. In the image the dependencies are inside the install;
 * in a checkout they are above it.
 */
export function readableRoots(inputs: readonly string[]): string[] {
	const install = path.dirname(import.meta.dirname);
	// `@types/node` declares the permission API always present; without `--permission` it is undefined.
	const permission = process.permission as NodeJS.ProcessPermission | undefined;
	const dependencies: string[] = [];
	for (let dir = install; ; dir = path.dirname(dir)) {
		const candidate = path.join(dir, "node_modules");
		// Under the permission model a probe of an ungranted folder throws: a child gets only what the
		// host may read itself.
		if ((permission?.has("fs.read", candidate) ?? true) && existsSync(candidate)) {
			dependencies.push(candidate);
		}
		if (path.dirname(dir) === dir) {
			break;
		}
	}
	return [install, ...dependencies, ...inputs]
		.filter((dir) => dir !== "")
		.map((dir) => path.resolve(dir));
}

/** Which declared slots have a model, and the report of each. A required slot without one skips the program. */
export function acceptModels(
	meta: ParsedMeta,
	models: Partial<SlotModels>,
): { report: SlotReport[]; available: Partial<Record<ModelSlot, string>>; missing: ModelSlot[] } {
	const report: SlotReport[] = [];
	const available: Partial<Record<ModelSlot, string>> = {};
	for (const slot of MODEL_SLOTS) {
		const need = meta.models?.[slot];
		if (need === undefined) {
			continue;
		}
		const modelId = models[slot]?.modelId;
		report.push({ slot, need, bound: modelId !== undefined, notRated: {} });
		if (modelId !== undefined) {
			available[slot] = modelId;
		}
	}
	return {
		report,
		available,
		missing: report.filter((r) => r.need === "required" && !r.bound).map((r) => r.slot),
	};
}

/** The reason an aborted signal carries, as a stop reason. */
export function abortReasonOf(signal: AbortSignal): AbortReason {
	const reason: unknown = signal.reason;
	return reason instanceof UnratedError &&
		(reason.reason === "budget" || reason.reason === "cancelled")
		? reason.reason
		: "deadline";
}

/**
 * Run one custom program in its own process, until it returns, fails or reaches its deadline. Its
 * value is returned unchecked: the host validates it as a candidate.
 */
export async function runCustomProgram(
	modulePath: string,
	job: ProgramJob,
	services: ProgramServices,
): Promise<ProgramExecution> {
	const { slug } = job;
	const calls = noCalls();
	if (services.signal.aborted || Date.now() >= job.deadline) {
		const stopReason = services.signal.aborted ? abortReasonOf(services.signal) : "deadline";
		return {
			failure: { stopReason, error: `the host stopped this program: ${stopReason}` },
			models: [],
			calls,
		};
	}
	let report: SlotReport[] = [];
	const readable = readableRoots([path.dirname(path.dirname(modulePath))]);
	const child = fork(path.join(import.meta.dirname, "..", "child.ts"), [], {
		env: {},
		execArgv: [
			"--permission",
			...readable.map((dir) => `--allow-fs-read=${dir}`),
			`--max-old-space-size=${CHILD_HEAP_MB}`,
		],
		stdio: ["ignore", "pipe", "pipe", "ipc"],
		serialization: "advanced",
	});
	let output = 0;
	const prefix = (chunk: Buffer) => {
		if (output >= MAX_OUTPUT_CHARS) {
			return;
		}
		const text = chunk.toString().slice(0, MAX_OUTPUT_CHARS - output);
		output += text.length;
		for (const line of text.split("\n")) {
			if (line !== "") {
				services.log(`  [${slug}] ${line}`);
			}
		}
		if (output >= MAX_OUTPUT_CHARS) {
			services.log(`  [${slug}] (more output is not logged)`);
		}
	};
	child.stdout?.on("data", prefix);
	child.stderr?.on("data", prefix);

	// Every call the host makes for this program ends with the program, its deadline, or the attempt.
	const stopped = new AbortController();
	/** The process ended on its own account: its calls end with it, and the host did not stop it. */
	let ended = false;
	child.on("close", () => {
		ended = true;
		stopped.abort(new UnratedError("deadline", "the program ended"));
	});
	const signal = AbortSignal.any([
		stopped.signal,
		services.signal,
		AbortSignal.timeout(Math.max(0, job.deadline - Date.now())),
	]);
	let aborted: AbortReason | undefined;
	/** Requests the host still owes a reply, by id, each with its own cancellation. */
	const open = new Map<number, OpenRequest>();
	/** Count one model call whose failure the program receives. */
	const countUnrated = (slot: ModelSlot, reason: Reason) => {
		const slotReport = report.find((r) => r.slot === slot);
		if (slotReport !== undefined) {
			slotReport.notRated[reason] = (slotReport.notRated[reason] ?? 0) + 1;
		}
	};
	const reply = (message: Exclude<HostMessage, { kind: "job" | "abort" | "meta-accepted" }>) => {
		// A reply to a request already answered, or after the program stopped, is dropped.
		if (!open.delete(message.id) || !child.connected) {
			return;
		}
		child.send(message);
	};
	/** Answer a request that the host will not run, with the failure of its own kind. */
	const refuseRequest = (kind: "model" | "read" | "review", id: number, reason: AbortReason) => {
		if (!child.connected) {
			return;
		}
		const message = `stopped: ${reason}`;
		const failures: Record<typeof kind, HostMessage> = {
			model: { kind: "model-failed", id, reason, message, tokensLeft: services.budget.left },
			read: { kind: "read-failed", id, reason, message },
			review: { kind: "review-failed", id, reason, message },
		};
		child.send(failures[kind]);
	};
	const sendAbort = (reason: AbortReason) => {
		if (aborted !== undefined) {
			return;
		}
		aborted = reason;
		settled = {
			failure: { stopReason: reason, error: `the host stopped this program: ${reason}` },
		};
		if (child.connected) {
			child.send({ kind: "abort", reason } satisfies HostMessage);
		}
		// Every request in flight fails now; whatever its call answers later is not sent or counted.
		// The count is taken here, before the program can read the failure and return.
		for (const [id, request] of open) {
			request.refused = true;
			request.call.abort(new UnratedError(reason, `the host stopped this program: ${reason}`));
			if (request.slot !== undefined) {
				// A slot report has no cancelled count: as in the direct model calls, it is the deadline.
				countUnrated(request.slot, reason === "cancelled" ? "deadline" : reason);
			}
			refuseRequest(request.kind, id, reason);
		}
		open.clear();
		killLater = setTimeout(stopAtLimit, RETURN_MS);
	};
	signal.addEventListener(
		"abort",
		() => {
			if (!ended) {
				sendAbort(abortReasonOf(signal));
				return;
			}
			for (const { call } of open.values()) {
				call.abort(new UnratedError("error", "the program ended"));
			}
			open.clear();
		},
		{ once: true },
	);

	const limit = { reached: false };
	const stopAtLimit = () => {
		limit.reached = true;
		child.kill("SIGKILL");
	};
	let killLater: ReturnType<typeof setTimeout> | undefined;

	let settled: Omit<ProgramExecution, "models" | "calls"> | undefined;
	/** Whether the program sent its one final message: a result, or why it has none. */
	let finished = false;
	let meta: ParsedMeta | undefined;
	let accepted: Partial<Record<ModelSlot, string>> = {};
	let reviewing = false;

	const fail = (stopReason: StopReason, error: string) => {
		settled ??= { failure: { stopReason, error: cut(error) } };
		child.kill("SIGKILL");
	};

	const answerModel = async (
		message: Extract<ChildMessage, { kind: "model" }>,
		request: OpenRequest,
		call: AbortSignal,
	) => {
		const { id, slot, label } = message;
		const model = services.models[slot];
		if (
			accepted[slot] === undefined ||
			model === undefined ||
			!report.some((r) => r.slot === slot)
		) {
			reply({
				kind: "model-failed",
				id,
				reason: "unavailable",
				message: `no ${slot} model is available to this program`,
				tokensLeft: services.budget.left,
			});
			return;
		}
		const asked = modelCall(slot, model, message.options, call);
		// A call counts as use only once the budget lets it reach the proxy.
		const outcome =
			"reason" in asked
				? asked
				: await services.budget.run(asked.reserve, call, async () => {
						calls[slot] += 1;
						return asked.run();
					});
		if ("value" in outcome) {
			reply({ kind: "model", id, answer: outcome.value, tokensLeft: services.budget.left });
			return;
		}
		if (request.refused === true) {
			return;
		}
		countUnrated(slot, outcome.reason);
		services.log(
			`  [${slug}] ${slot} call${label === "" ? "" : ` in ${label}`} not rated: ${outcome.reason}`,
		);
		reply({
			kind: "model-failed",
			id,
			reason: outcome.reason,
			message: `${slot} call not rated: ${outcome.reason}`,
			tokensLeft: services.budget.left,
		});
	};

	const answerReview = async (
		message: Extract<ChildMessage, { kind: "review" }>,
		call: AbortSignal,
	) => {
		const { id } = message;
		if (reviewing) {
			reply({
				kind: "review-failed",
				id,
				reason: "refused",
				message: "a review of this practice is already running",
			});
			return;
		}
		const parsed = parseReviewOptions(message.options);
		if ("error" in parsed) {
			reply({ kind: "review-failed", id, reason: "error", message: parsed.error });
			return;
		}
		reviewing = true;
		calls.review += 1;
		try {
			const candidate = await services.review(parsed.options, call);
			reply({ kind: "review-result", id, candidate });
		} catch (error) {
			reply({ kind: "review-failed", id, reason: reasonOf(error), message: cut(messageOf(error)) });
		} finally {
			reviewing = false;
		}
	};

	const answerRead = async (message: Extract<ChildMessage, { kind: "read" }>) => {
		try {
			reply({
				kind: "read-result",
				id: message.id,
				value: await services.reader.answer(message.request),
			});
		} catch (error) {
			reply({
				kind: "read-failed",
				id: message.id,
				reason: reasonOf(error),
				message: cut(messageOf(error)),
			});
		}
	};

	const acceptMeta = (value: unknown) => {
		if (meta !== undefined) {
			fail("invalid-program", "meta was sent twice");
			return;
		}
		const parsed = parseMeta(value);
		if ("error" in parsed) {
			fail("invalid-program", parsed.error);
			return;
		}
		meta = parsed.meta;
		const models = acceptModels(meta, services.models);
		report = models.report;
		accepted = models.available;
		if (models.missing.length > 0) {
			settled ??= {
				failure: {
					stopReason: "unavailable",
					error: `no model is bound for the required slot(s) ${models.missing.join(", ")}`,
					skipped: true,
				},
			};
			child.kill("SIGKILL");
			return;
		}
		child.send({
			kind: "meta-accepted",
			models: accepted,
			tokens: services.budget.left,
		} satisfies HostMessage);
	};

	/** Answer one request; a request the host cannot answer, such as options without a JSON form, stops the program. */
	const answerRequest = async (
		message: Extract<ChildMessage, { kind: "model" | "read" | "review" }>,
		request: OpenRequest,
		callSignal: AbortSignal,
	) => {
		try {
			if (message.kind === "model") {
				await answerModel(message, request, callSignal);
			} else if (message.kind === "read") {
				await answerRead(message);
			} else {
				await answerReview(message, callSignal);
			}
		} catch (error) {
			fail("error", `the host could not answer a ${message.kind} request: ${messageOf(error)}`);
		}
	};

	child.on("message", (raw: unknown) => {
		if (aborted === undefined && Date.now() >= job.deadline) {
			sendAbort("deadline");
		}
		if (settled !== undefined && aborted === undefined && !finished) {
			return;
		}
		if (typeof raw !== "string") {
			fail("error", "the program's process must send JSON text");
			return;
		}
		if (Buffer.byteLength(raw) > MAX_IPC_MESSAGE_BYTES) {
			fail("error", `the program sent a message larger than ${MAX_IPC_MESSAGE_BYTES} bytes`);
			return;
		}
		let value: unknown;
		try {
			value = JSON.parse(raw);
		} catch {
			fail("error", "the program sent a message that is not JSON");
			return;
		}
		if (finished && isJsonObject(value) && (value.kind === "result" || value.kind === "failed")) {
			settled = {
				failure: { stopReason: "error", error: "the program sent a second final message" },
			};
			child.kill("SIGKILL");
			return;
		}
		if (settled !== undefined && aborted === undefined) {
			return;
		}
		const parsed = childMessageSchema.safeParse(value);
		if (!parsed.success) {
			fail("error", "the program's process sent a message the host does not accept");
			return;
		}
		const message = parsed.data;
		switch (message.kind) {
			case "meta": {
				acceptMeta(message.meta);
				return;
			}
			case "log": {
				services.log(`  [${slug}] ${message.text}`);
				return;
			}
			case "cancel": {
				// A cancel may cross the reply it cancels; one for a request already answered changes nothing.
				open
					.get(message.id)
					?.call.abort(new UnratedError("cancelled", "the program cancelled the call"));
				return;
			}
			case "result": {
				if (meta === undefined) {
					fail("invalid-program", "the program sent its result before meta");
					return;
				}
				finished = true;
				if (aborted !== undefined) {
					return;
				}
				settled = { value: message.value };
				return;
			}
			case "failed": {
				finished = true;
				// A program that fails before it declares itself, such as one in the retired form, is not a program.
				const reason = message.reason ?? (meta === undefined ? "invalid-program" : "error");
				settled = {
					failure: {
						stopReason: aborted ?? reason,
						error: cut(message.message),
					},
				};
				return;
			}
			case "model":
			case "read":
			case "review": {
				if (message.kind !== "read" && meta === undefined) {
					fail("invalid-program", `the program sent a ${message.kind} request before meta`);
					return;
				}
				if (open.has(message.id)) {
					fail("error", `the program reused the id of request ${message.id}`);
					return;
				}
				if (open.size >= MAX_IN_FLIGHT) {
					fail("error", `more than ${MAX_IN_FLIGHT} requests at once`);
					return;
				}
				if (aborted !== undefined) {
					refuseRequest(message.kind, message.id, aborted);
					return;
				}
				const call = new AbortController();
				const request: OpenRequest = {
					kind: message.kind,
					call,
					...(message.kind === "model" && { slot: message.slot }),
				};
				open.set(message.id, request);
				const callSignal = AbortSignal.any([signal, call.signal]);
				void answerRequest(message, request, callSignal);
			}
		}
	});
	child.on("error", (error) => {
		settled ??= { failure: { stopReason: "error", error: cut(messageOf(error)) } };
	});
	const closed = once(child, "close");
	const deadlineLimit = setTimeout(stopAtLimit, Math.max(0, job.deadline - Date.now()) + RETURN_MS);
	child.send({ kind: "job", job: { ...job, modulePath } } satisfies HostMessage);
	const exit: readonly unknown[] = await closed;
	clearTimeout(deadlineLimit);
	clearTimeout(killLater);
	const done = (outcome: Omit<ProgramExecution, "models" | "calls">): ProgramExecution => ({
		...outcome,
		models: report,
		calls,
	});
	if (settled !== undefined) {
		return done(settled);
	}
	if (limit.reached || aborted !== undefined) {
		return done({
			failure: {
				stopReason: aborted ?? "deadline",
				error: "the program did not return before its deadline",
			},
		});
	}
	const exitSignal = exit[1];
	return done({
		failure: {
			stopReason: "error",
			error:
				typeof exitSignal === "string"
					? `the program's process was killed by ${exitSignal}`
					: "the program's process ended without a result",
		},
	});
}
