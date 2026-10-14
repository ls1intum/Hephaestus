/**
 * The host's side of a direct model call of a practice program. A call arrives from a child as the
 * options of one `do*` method. The host keeps only the allowed fields, reserves budget, and calls the
 * proxy under `/practice-script/<slot>` with the job's proxy token and the practice's slug.
 */
import { createOpenAI } from "@ai-sdk/openai";
import { createOpenAICompatible } from "@ai-sdk/openai-compatible";
import { APICallError, InvalidResponseDataError, type RerankingModelV4 } from "@ai-sdk/provider";
import { defaultSettingsMiddleware, wrapLanguageModel } from "ai";
import { z } from "zod";

import {
	MODEL_SLOT_CALLS,
	PRACTICE_SCRIPT_PRACTICE_HEADER,
	type BoundModels,
	type ModelSlot,
	type Reason,
	type SlotAnswer,
	type SlotCall,
	type SlotModels,
} from "./contract.ts";
import { answerTokens, logprobDecisionModel } from "./decision-logprobs.ts";
import { reasonOf } from "./practice-script.ts";

const rerankResponseSchema = z.object({
	results: z.array(z.object({ index: z.int().nonnegative(), relevance_score: z.number() })),
});

/**
 * The Cohere `/rerank` shape (`results[].index`, `relevance_score`) that Cohere, Jina and vLLM share.
 * `@ai-sdk/cohere` requires the `meta` field that only Cohere returns, so it rejects the others.
 */
function cohereRerankModel(
	baseURL: string,
	headers: Record<string, string>,
	modelId: string,
): RerankingModelV4 {
	return {
		specificationVersion: "v4",
		provider: "hephaestus",
		modelId,
		doRerank: async ({ documents, query, topN, abortSignal }) => {
			const url = `${baseURL}/rerank`;
			const values =
				documents.type === "text"
					? documents.values
					: documents.values.map((d) => JSON.stringify(d));
			const response = await fetch(url, {
				method: "POST",
				headers: { ...headers, "content-type": "application/json" },
				body: JSON.stringify({ model: modelId, query, documents: values, top_n: topN }),
				signal: abortSignal,
			});
			if (!response.ok) {
				throw new APICallError({
					message: `rerank answered ${response.status}`,
					url,
					requestBodyValues: { model: modelId },
					statusCode: response.status,
				});
			}
			const body: unknown = await response.json();
			const parsed = rerankResponseSchema.safeParse(body);
			if (!parsed.success || parsed.data.results.some((r) => r.index >= values.length)) {
				throw new InvalidResponseDataError({
					data: body,
					message: "rerank answered off the format",
				});
			}
			return {
				ranking: parsed.data.results.map((r) => ({
					index: r.index,
					relevanceScore: r.relevance_score,
				})),
			};
		},
	};
}

/**
 * One practice's models for the bound slots, each through the client of its protocol. Every call
 * names the practice in {@link PRACTICE_SCRIPT_PRACTICE_HEADER}. A protocol that cannot serve its
 * slot leaves the slot without a model.
 */
export function hostModels(
	bound: BoundModels,
	proxyUrl: string,
	token: string,
	practice: string,
): Partial<SlotModels> {
	const headers = { [PRACTICE_SCRIPT_PRACTICE_HEADER]: practice };
	const settings = (slot: ModelSlot) => ({
		baseURL: `${proxyUrl.replace(/\/+$/u, "")}/practice-script/${slot}`,
		apiKey: token,
		headers,
	});
	const compatible = (slot: ModelSlot) =>
		createOpenAICompatible({
			name: "hephaestus",
			...settings(slot),
			supportsStructuredOutputs: true,
			includeUsage: true,
		});
	const { chat, decision, embedding, reranking } = bound;
	const models: Partial<SlotModels> = {};
	if (chat?.protocol === "openai-responses") {
		models.chat = wrapLanguageModel({
			model: createOpenAI(settings("chat")).responses(chat.modelId),
			// A stored response makes the next step of a tool loop cite it as an `item_reference`, which
			// OpenAI-compatible servers such as vLLM refuse; nor should a provider keep review content.
			middleware: defaultSettingsMiddleware({
				settings: { providerOptions: { openai: { store: false } } },
			}),
		});
	} else if (chat?.protocol === "openai-completions") {
		models.chat = compatible("chat").chatModel(chat.modelId);
	}
	if (decision?.protocol === "openai-decisions") {
		models.decision = createOpenAI(settings("decision")).decisionModel(decision.modelId);
	} else if (decision?.protocol === "openai-completions") {
		models.decision = logprobDecisionModel(
			createOpenAI(settings("decision")).chat(decision.modelId),
			{ reasoning: decision.reasoningEffort !== undefined },
		);
	}
	if (embedding?.protocol === "openai-embeddings") {
		models.embedding = compatible("embedding").embeddingModel(embedding.modelId);
	}
	if (reranking?.protocol === "cohere-rerank") {
		models.reranking = cohereRerankModel(
			settings("reranking").baseURL,
			{ ...headers, authorization: `Bearer ${token}` },
			reranking.modelId,
		);
	}
	return models;
}

/** The most output a practice script chat call may ask for. */
export const MAX_OUTPUT_TOKENS = 4096;

/**
 * Keep only the fields that a model call of a practice script may set. Provider options and headers
 * stay with the runner: a script must not reach provider features, upstream headers, another model
 * or another practice's attribution through them.
 */
export function allowedOptions(slot: ModelSlot, options: unknown): Record<string, unknown> {
	const kept: Record<string, unknown> = {};
	if (typeof options !== "object" || options === null) {
		return kept;
	}
	for (const key of MODEL_SLOT_CALLS[slot].options) {
		const value: unknown = Reflect.get(options, key);
		if (value !== undefined) {
			kept[key] = value;
		}
	}
	if (slot === "chat") {
		const asked = kept.maxOutputTokens;
		kept.maxOutputTokens =
			typeof asked === "number" && Number.isInteger(asked) && asked > 0
				? Math.min(asked, MAX_OUTPUT_TOKENS)
				: MAX_OUTPUT_TOKENS;
		kept.temperature ??= 0;
		if (Array.isArray(kept.tools)) {
			kept.tools = kept.tools.filter(
				(t: unknown) =>
					typeof t === "object" && t !== null && Reflect.get(t, "type") === "function",
			);
		}
	}
	return kept;
}

/**
 * The tokens reserved for a call before it is made: four characters per token of input, plus the most
 * output the call can write. It is an estimate; the call settles at the tokens that it reports.
 */
export function reservationOf(slot: ModelSlot, options: Record<string, unknown>): number {
	const input = Math.ceil(JSON.stringify(options).length / 4);
	switch (slot) {
		case "chat": {
			return input + Number(options.maxOutputTokens ?? MAX_OUTPUT_TOKENS);
		}
		case "decision": {
			return input + answerTokens(MODEL_SLOT_CALLS.decision.items.count(options));
		}
		case "embedding":
		case "reranking": {
			return input + 64;
		}
	}
}

/**
 * The call that a script asked a slot's model for, with only the options a script may set: the tokens
 * to reserve for it and the call itself, or "too-large" when it holds more items than one call to the
 * model may. Options that are not a call to the slot's model throw.
 */
export function modelCall<S extends ModelSlot>(
	slot: S,
	model: SlotModels[S],
	options: unknown,
	signal: AbortSignal,
):
	| { reason: "too-large" }
	| { reserve: number; run: () => Promise<{ value: SlotAnswer; tokens: number | undefined }> } {
	// A generic slot indexes the mapped type only: the inferred type of the map is not one.
	const calls: { [K in ModelSlot]: SlotCall<K> } = MODEL_SLOT_CALLS;
	const entry = calls[slot];
	const kept = allowedOptions(slot, options);
	if (!entry.accepts(kept)) {
		throw new Error(`its options are not a ${slot} call`);
	}
	if (entry.items !== undefined && entry.items.count(kept) > entry.items.max) {
		return { reason: "too-large" };
	}
	return {
		reserve: reservationOf(slot, kept),
		run: async () => entry.call(model, { ...kept, abortSignal: signal }),
	};
}

/** Why a started call failed. A call that fails after its signal aborts failed at the deadline. */
function failureOf(signal: AbortSignal, error: unknown): Reason {
	if (signal.aborted) {
		return "deadline";
	}
	const reason = reasonOf(error);
	return reason === "cancelled" ? "deadline" : reason;
}

/**
 * The attempt's account for direct model calls: one token ceiling for every practice's direct calls,
 * and a limit on calls in flight. Tokens are reserved before a call starts and settled when it ends.
 * A call that fails, is cancelled, or reports no usage keeps its reservation, because the provider may
 * have billed it. A review session's own calls are not direct calls: the proxy bounds them.
 */
export class ModelBudget {
	#left: number;
	#free: number;
	#waiting: ((turn: undefined) => void)[] = [];

	constructor(tokens: number, concurrency: number) {
		this.#left = tokens;
		this.#free = concurrency;
	}

	/** The tokens that direct calls of this attempt may still spend. */
	get left(): number {
		return this.#left;
	}

	/**
	 * Run `call` when fewer calls than the limit are in flight and the account holds `reserve` tokens.
	 * A call waiting for its turn holds nothing. The result is the call's value and the tokens charged,
	 * or the reason it did not run or failed.
	 */
	async run<T>(
		reserve: number,
		signal: AbortSignal,
		call: () => Promise<{ value: T; tokens: number | undefined }>,
	): Promise<{ value: T; tokens: number } | { reason: Reason }> {
		try {
			signal.throwIfAborted();
		} catch {
			return { reason: "deadline" };
		}
		if (this.#free > 0) {
			this.#free -= 1;
		} else {
			const turn = Promise.withResolvers<undefined>();
			this.#waiting.push(turn.resolve);
			const abandon = () => {
				this.#waiting = this.#waiting.filter((waiter) => waiter !== turn.resolve);
				turn.reject(signal.reason);
			};
			signal.addEventListener("abort", abandon, { once: true });
			try {
				await turn.promise;
			} catch {
				return { reason: "deadline" };
			} finally {
				signal.removeEventListener("abort", abandon);
			}
		}
		try {
			if (signal.aborted) {
				return { reason: "deadline" };
			}
			if (reserve > this.#left) {
				return { reason: "budget" };
			}
			this.#left -= reserve;
			try {
				const answered = await call();
				const tokens =
					answered.tokens !== undefined &&
					Number.isSafeInteger(answered.tokens) &&
					answered.tokens >= 0
						? answered.tokens
						: reserve;
				signal.throwIfAborted();
				// Settle: give back what the call did not use, take what it used beyond the estimate.
				this.#left += reserve - tokens;
				return { value: answered.value, tokens };
			} catch (error) {
				return { reason: failureOf(signal, error) };
			}
		} finally {
			const next = this.#waiting.shift();
			if (next === undefined) {
				this.#free += 1;
			} else {
				next(undefined);
			}
		}
	}
}
