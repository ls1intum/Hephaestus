import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { describe, test } from "node:test";
import { setImmediate } from "node:timers/promises";

import { InvalidResponseDataError } from "@ai-sdk/provider";

import { MockEmbeddingModelV4 } from "ai/test";

import { boundModelsSchema, MODEL_SLOT_CAPS } from "./contract.ts";
import { answerTokens } from "./decision-logprobs.ts";
import {
	allowedOptions,
	hostModels,
	MAX_OUTPUT_TOKENS,
	modelCall,
	ModelBudget,
	reservationOf,
} from "./models-host.ts";

void test("the models file in the shape the server writes gives every slot a model", async () => {
	// PracticeScriptContractSyncTest proves that the server writes this file for the same four
	// models.
	const file = new URL("../../test/practice-script-models.json", import.meta.url);
	const bound = boundModelsSchema.parse(JSON.parse(await readFile(file, "utf8")));
	const models = hostModels(bound, "http://proxy.test/internal/llm", "token", "comment-quality");
	assert.deepEqual(Object.keys(models).toSorted(), ["chat", "decision", "embedding", "reranking"]);
});

const never = new AbortController().signal;

void describe("the attempt's direct model account", () => {
	void test("a call settles at the tokens it reports, and one that reports no usage keeps its whole reservation", async () => {
		const budget = new ModelBudget(1000, 1);
		assert.deepEqual(
			await budget.run(120, never, async () => ({ value: "answered", tokens: 30 })),
			{ value: "answered", tokens: 30 },
		);
		assert.equal(budget.left, 970);
		assert.deepEqual(
			await budget.run(120, never, async () => ({ value: "ranked", tokens: undefined })),
			{ value: "ranked", tokens: 120 },
		);
		assert.equal(budget.left, 850);
	});

	void test("every practice's direct calls spend one account, so a call that it cannot afford does not run", async () => {
		const budget = new ModelBudget(200, 2);
		// Two practices, one account: the first practice's call leaves too little for the second's.
		assert.ok(
			"value" in (await budget.run(150, never, async () => ({ value: "first", tokens: 150 }))),
		);
		let ran = false;
		const outcome = await budget.run(120, never, async () => {
			ran = true;
			return { value: "late", tokens: 1 };
		});
		assert.deepEqual(outcome, { reason: "budget" });
		assert.equal(ran, false);
		assert.equal(budget.left, 50);
	});

	void test("a call that fails keeps its reservation, because the provider may have billed it", async () => {
		const budget = new ModelBudget(1000, 1);
		const outcome = await budget.run(10, never, async () => {
			throw new InvalidResponseDataError({ data: {}, message: "no results" });
		});
		assert.deepEqual(outcome, { reason: "off-format" });
		assert.equal(budget.left, 990);
	});

	void test("a confirmed zero refunds a reservation, while implausible usage keeps it", async () => {
		const budget = new ModelBudget(1000, 1);
		await budget.run(120, never, async () => ({ value: "zero", tokens: 0 }));
		assert.equal(budget.left, 1000);
		for (const tokens of [-1, 0.5, Number.NaN, Number.POSITIVE_INFINITY]) {
			await budget.run(120, never, async () => ({ value: "unverifiable", tokens }));
		}
		assert.equal(budget.left, 520);
	});

	void test("an answer after cancellation keeps its reservation and returns no value", async () => {
		const budget = new ModelBudget(1000, 1);
		const cancelled = new AbortController();
		const outcome = await budget.run(120, cancelled.signal, async () => {
			cancelled.abort();
			return { value: "late", tokens: 0 };
		});
		assert.deepEqual(outcome, { reason: "deadline" });
		assert.equal(budget.left, 880);
	});

	void test("a call that waits for its turn and reaches its deadline does not run and holds nothing", async () => {
		const budget = new ModelBudget(1000, 1);
		const busy = Promise.withResolvers<{ value: string; tokens: number }>();
		const first = budget.run(10, never, async () => busy.promise);
		const deadline = new AbortController();
		let ran = false;
		const waiting = budget.run(10, deadline.signal, async () => {
			ran = true;
			return { value: "late", tokens: 1 };
		});
		deadline.abort();
		assert.deepEqual(await waiting, { reason: "deadline" });
		assert.equal(budget.left, 990);
		busy.resolve({ value: "first", tokens: 10 });
		assert.deepEqual(await first, { value: "first", tokens: 10 });
		assert.equal(ran, false);
		// The turn that the abandoned call gave up goes to the next call.
		assert.ok(
			"value" in (await budget.run(10, never, async () => ({ value: "next", tokens: 10 }))),
		);
	});

	void test("no more calls than the limit are in flight at once", async () => {
		const budget = new ModelBudget(1000, 2);
		let inFlight = 0;
		let most = 0;
		await Promise.all(
			Array.from({ length: 6 }, async () =>
				budget.run(1, never, async () => {
					inFlight += 1;
					most = Math.max(most, inFlight);
					await setImmediate();
					inFlight -= 1;
					return { value: "done", tokens: 1 };
				}),
			),
		);
		assert.equal(most, 2);
		assert.equal(budget.left, 994);
	});
});

void describe("the request allowlist", () => {
	for (const asked of [-1_000_000, 0.5, "4096"]) {
		void test(`an output limit of ${JSON.stringify(asked)} is the cap, so the reservation cannot shrink`, () => {
			const kept = allowedOptions("chat", { prompt: [], maxOutputTokens: asked });
			assert.equal(kept.maxOutputTokens, MAX_OUTPUT_TOKENS);
			assert.ok(reservationOf("chat", kept) > MAX_OUTPUT_TOKENS);
		});
	}

	void test("a decision reserves an answer line for each question", () => {
		const one = reservationOf("decision", { state: "", questions: { a: {} } });
		const thirty = reservationOf("decision", {
			state: "",
			questions: Object.fromEntries(Array.from({ length: 30 }, (_, i) => [`q${i}`, {}])),
		});
		assert.ok(thirty - one >= answerTokens(30) - answerTokens(1), `${one} → ${thirty}`);
	});
});

void describe("chat usage receipts from the bound provider", () => {
	for (const usage of [
		undefined,
		{},
		{ prompt_tokens: 10 },
		{ completion_tokens: 5 },
		{ prompt_tokens: -1, completion_tokens: 5 },
		{ prompt_tokens: 0.5, completion_tokens: 5 },
		{ prompt_tokens: 10, completion_tokens: 5, prompt_tokens_details: { cached_tokens: 11 } },
		{ prompt_tokens: 10, completion_tokens: 5, completion_tokens_details: { reasoning_tokens: 6 } },
	]) {
		void test(`an incomplete or implausible receipt ${JSON.stringify(usage)} retains the reservation`, async (t) => {
			t.mock.method(globalThis, "fetch", async () =>
				Response.json({
					choices: [{ message: { role: "assistant", content: "yes" }, finish_reason: "stop" }],
					usage,
				}),
			);
			const { chat } = hostModels(
				{ chat: { protocol: "openai-completions", modelId: "chat" } },
				"https://proxy.example/internal/llm",
				"test-token",
				"practice",
			);
			assert.ok(chat !== undefined);
			const call = modelCall(
				"chat",
				chat,
				{
					prompt: [{ role: "user", content: [{ type: "text", text: "Say yes." }] }],
					maxOutputTokens: 1000,
				},
				never,
			);
			assert.ok("run" in call);
			const budget = new ModelBudget(5000, 1);
			assert.ok("value" in (await budget.run(call.reserve, never, call.run)));
			assert.equal(budget.left, 5000 - call.reserve);
		});
	}

	void test("a complete receipt preserves a confirmed zero", async (t) => {
		t.mock.method(globalThis, "fetch", async () =>
			Response.json({
				choices: [{ message: { role: "assistant", content: "yes" }, finish_reason: "stop" }],
				usage: { prompt_tokens: 0, completion_tokens: 0 },
			}),
		);
		const { chat } = hostModels(
			{ chat: { protocol: "openai-completions", modelId: "chat" } },
			"https://proxy.example/internal/llm",
			"test-token",
			"practice",
		);
		assert.ok(chat !== undefined);
		const call = modelCall("chat", chat, { prompt: [], maxOutputTokens: 1000 }, never);
		assert.ok("run" in call);
		const budget = new ModelBudget(5000, 1);
		const answer = await budget.run(call.reserve, never, call.run);
		assert.ok("value" in answer);
		assert.equal(answer.tokens, 0);
		assert.equal(budget.left, 5000);
	});
});

void describe("logprob decision usage receipts from the bound provider", () => {
	for (const { usage, tokens } of [
		{ usage: undefined },
		{ usage: {} },
		{ usage: { prompt_tokens: 10 } },
		{ usage: { completion_tokens: 5 } },
		{ usage: { prompt_tokens: 0, completion_tokens: 0 }, tokens: 0 },
		{ usage: { prompt_tokens: 10, completion_tokens: 5 }, tokens: 15 },
	] satisfies { usage: unknown; tokens?: number }[]) {
		void test(`a decision adapter preserves the receipt ${JSON.stringify(usage)}`, async (t) => {
			t.mock.method(globalThis, "fetch", async () =>
				Response.json({
					id: "completion",
					model: "decision",
					created: 0,
					choices: [
						{
							index: 0,
							message: { role: "assistant", content: "q1: A" },
							finish_reason: "stop",
							logprobs: {
								content: [
									{ token: "q1", logprob: 0, top_logprobs: [] },
									{ token: ":", logprob: 0, top_logprobs: [] },
									{ token: " A", logprob: 0, top_logprobs: [{ token: " A", logprob: 0 }] },
								],
							},
						},
					],
					usage,
				}),
			);
			const { decision } = hostModels(
				{ decision: { protocol: "openai-completions", modelId: "decision" } },
				"https://proxy.example/internal/llm",
				"test-token",
				"practice",
			);
			assert.ok(decision !== undefined);
			const call = modelCall(
				"decision",
				decision,
				{ state: "work", questions: { q: { type: "boolean", instructions: "Is it met?" } } },
				never,
			);
			assert.ok("run" in call);
			const budget = new ModelBudget(5000, 1);
			const outcome = await budget.run(call.reserve, never, call.run);
			assert.ok("value" in outcome, JSON.stringify(outcome));
			assert.equal(budget.left, 5000 - (tokens ?? call.reserve));
			assert.ok(outcome.value.slot === "decision");
			assert.equal(outcome.value.result.usage === undefined, tokens === undefined);
		});
	}
});

const values = (count: number) => Array.from({ length: count }, (_, i) => `v${i}`);

void describe("a call to a slot's model", () => {
	const embedder = new MockEmbeddingModelV4({
		doEmbed: async ({ values: asked }) => ({
			embeddings: asked.map(() => [1, 0]),
			usage: { tokens: 7 },
			warnings: [],
		}),
	});

	void test("at the cap it runs, and reports the tokens that the model reports", async () => {
		const call = modelCall(
			"embedding",
			embedder,
			{ values: values(MODEL_SLOT_CAPS.embedding), providerOptions: { x: 1 } },
			never,
		);
		assert.ok("run" in call);
		const { value, tokens } = await call.run();
		assert.equal(tokens, 7);
		assert.equal(value.slot, "embedding");
		assert.equal(embedder.doEmbedCalls.at(-1)?.providerOptions, undefined);
	});

	for (const [slot, options] of [
		["embedding", { values: values(MODEL_SLOT_CAPS.embedding + 1) }],
		[
			"decision",
			{
				state: "s",
				questions: Object.fromEntries(
					values(MODEL_SLOT_CAPS.decision + 1).map((q) => [
						q,
						{ type: "boolean", instructions: q },
					]),
				),
			},
		],
		[
			"reranking",
			{ query: "q", documents: { type: "text", values: values(MODEL_SLOT_CAPS.reranking + 1) } },
		],
	] as const) {
		void test(`above the cap, the ${slot} call is too large and never starts`, () => {
			const model = hostModels(
				{
					decision: { protocol: "openai-decisions", modelId: "d" },
					embedding: { protocol: "openai-embeddings", modelId: "e" },
					reranking: { protocol: "cohere-rerank", modelId: "r" },
				},
				"http://proxy.test",
				"token",
				"comment-quality",
			)[slot];
			assert.ok(model !== undefined);
			assert.deepEqual(modelCall(slot, model, options, never), { reason: "too-large" });
		});
	}

	for (const [slot, options] of [
		["chat", { prompt: "Say hi." }],
		["decision", { state: "s" }],
		["decision", { state: 1, questions: { q: { type: "boolean", instructions: "q" } } }],
		["decision", { state: "s", questions: { q: { type: "free", instructions: "q" } } }],
		["decision", { state: "s", questions: { q: { type: "boolean" } } }],
		["embedding", { values: [1, 2] }],
		["reranking", { documents: { type: "text", values: ["a"] } }],
		["reranking", { query: "q", documents: { type: "text", values: [{ text: "a" }] } }],
		["reranking", { query: "q", documents: { type: "object", values: ["a"] } }],
	] as const) {
		void test(`options that are not a call to the ${slot} model never reach it`, () => {
			const model = hostModels(
				{
					chat: { protocol: "openai-completions", modelId: "c" },
					decision: { protocol: "openai-decisions", modelId: "d" },
					embedding: { protocol: "openai-embeddings", modelId: "e" },
					reranking: { protocol: "cohere-rerank", modelId: "r" },
				},
				"http://proxy.test",
				"token",
				"comment-quality",
			)[slot];
			assert.ok(model !== undefined);
			assert.throws(() => modelCall(slot, model, options, never), {
				message: `its options are not a ${slot} call`,
			});
		});
	}
});
