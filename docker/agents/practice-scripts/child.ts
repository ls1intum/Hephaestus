/**
 * One custom practice program in its own process. The process may not start a program, reads nothing
 * but its own code, and holds no credential, so its reads, model calls and reviews go to the host.
 */
import { once } from "node:events";
import { pathToFileURL } from "node:url";
import { promisify } from "node:util";

import { callLabel } from "./lib/call-label.ts";
import { RETIRED_FORM_ERROR } from "./lib/contract.ts";
import {
	MAX_LABEL_CHARS,
	type ChildJob,
	type ChildMessage,
	type HostMessage,
	type ReadRequest,
	type ReadValues,
} from "./lib/ipc.ts";
import { isJsonObject } from "./lib/json.ts";
import { ipcModels } from "./lib/models-ipc.ts";
import {
	isPracticeScriptDefinition,
	UnratedError,
	type ModelNeeds,
	type PracticeScriptDefinition,
} from "./lib/practice-script.ts";
import { programContext } from "./lib/program-context.ts";

const send = process.send?.bind(process);
if (send === undefined) {
	throw new Error("child.ts runs only as a process forked by the program host");
}
const post = (message: ChildMessage) => send(JSON.stringify(message));

/** Send the last message. A disconnect before a large message has left this process drops it. */
const postLast = promisify((message: ChildMessage, sent: (error: Error | null) => void) => {
	send(JSON.stringify(message), undefined, undefined, sent);
});

/** Replies the host owes this child, by request id. A reply with another id is not this child's. */
const pending = new Map<number, (message: HostMessage) => void>();
let nextId = 0;
const metaReply = Promise.withResolvers<Extract<HostMessage, { kind: "meta-accepted" }>>();
let tokensLeft = 0;
/** Aborts when the host stops this program. */
const stopped = new AbortController();

/** The host's own messages are trusted, so only the field that routes them is checked. */
function isHostMessage(value: unknown): value is HostMessage {
	return isJsonObject(value) && typeof value.kind === "string";
}

const jobArrived = once(process, "message");
process.on("message", (message: unknown) => {
	if (!isHostMessage(message) || message.kind === "job") {
		return;
	}
	if (message.kind === "meta-accepted") {
		tokensLeft = message.tokens;
		metaReply.resolve(message);
		return;
	}
	if (message.kind === "abort") {
		stopped.abort(
			new UnratedError(message.reason, `the host stopped this program: ${message.reason}`),
		);
		return;
	}
	const waiter = pending.get(message.id);
	pending.delete(message.id);
	waiter?.(message);
});

async function request(make: (id: number) => ChildMessage): Promise<HostMessage> {
	const id = nextId;
	nextId += 1;
	const reply = Promise.withResolvers<HostMessage>();
	pending.set(id, reply.resolve);
	post(make(id));
	return reply.promise;
}

async function read<R extends ReadRequest>(readRequest: R): Promise<ReadValues[R["op"]]>;
async function read(readRequest: ReadRequest): Promise<ReadValues[keyof ReadValues]> {
	stopped.signal.throwIfAborted();
	const reply = await request((id) => ({ kind: "read", id, request: readRequest }));
	if (reply.kind === "read-result") {
		return reply.value;
	}
	if (reply.kind === "read-failed") {
		throw new UnratedError(reply.reason, reply.message);
	}
	throw new Error("unexpected reply to a read");
}

async function runDefinition(
	job: ChildJob,
	definition: PracticeScriptDefinition<ModelNeeds>,
): Promise<unknown> {
	post({ kind: "meta", meta: definition.meta });
	const accepted = await metaReply.promise;
	const abortSignal = AbortSignal.any([
		stopped.signal,
		AbortSignal.timeout(Math.max(0, job.deadline - Date.now())),
	]);
	const models = ipcModels(accepted.models, async (slot, options, signal) => {
		signal?.throwIfAborted();
		const answered = new AbortController();
		const reply = await request((id) => {
			signal?.addEventListener(
				"abort",
				() => {
					post({ kind: "cancel", id });
				},
				{ once: true, signal: answered.signal },
			);
			const label = (callLabel.getStore() ?? "").slice(0, MAX_LABEL_CHARS);
			return { kind: "model", id, slot, label, options };
		}).finally(() => {
			answered.abort();
		});
		if (reply.kind === "model") {
			tokensLeft = reply.tokensLeft;
			return reply.answer;
		}
		if (reply.kind === "model-failed") {
			tokensLeft = reply.tokensLeft;
			// The AI SDK's `maxRetries` retries only an `APICallError`, so each direct model call runs once.
			throw new UnratedError(reply.reason, reply.message);
		}
		throw new Error("unexpected reply to a model call");
	});
	const ctx = programContext(job, {
		read,
		review: async (options) => {
			stopped.signal.throwIfAborted();
			const reply = await request((id) => ({ kind: "review", id, options: options ?? {} }));
			if (reply.kind === "review-result") {
				return reply.candidate;
			}
			if (reply.kind === "review-failed") {
				throw new UnratedError(reply.reason, reply.message);
			}
			throw new Error("unexpected reply to a review");
		},
		models,
		tokensLeft: () => tokensLeft,
		log: (text) => {
			post({ kind: "log", text });
		},
		abortSignal,
	});
	return definition.run(ctx);
}

const arrived: readonly unknown[] = await jobArrived;
const first = arrived[0];
if (!isHostMessage(first) || first.kind !== "job") {
	throw new Error("the host sends the job first");
}
const { job } = first;
try {
	const mod: unknown = await import(pathToFileURL(job.modulePath).href);
	const exported: unknown = isJsonObject(mod) ? mod.default : undefined;
	if (!isPracticeScriptDefinition(exported)) {
		throw new Error(RETIRED_FORM_ERROR);
	}
	await postLast({ kind: "result", value: await runDefinition(job, exported) });
} catch (error) {
	// A practice program is foreign code (DB-stored data), so it can throw a non-Error value.
	await postLast({
		kind: "failed",
		message: error instanceof Error ? error.message : String(error),
		...(error instanceof UnratedError ? { reason: error.reason } : {}),
	});
}
process.disconnect();
