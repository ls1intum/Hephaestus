/**
 * Live: the host library runs custom programs that call every direct model slot against real models
 * behind the stand-in proxy routes. `docs/contributor/testing.mdx` § Live models for practice scripts
 * names the keys; without them the tests skip. They assert what the host guarantees, never what a
 * model judges.
 */
import assert from "node:assert/strict";
import { cp, readFile, rm } from "node:fs/promises";
import path from "node:path";
import { after, before, test } from "node:test";

import { parseDiff } from "./kits/scm/diff-parser.ts";
import { MODEL_CONCURRENCY } from "./lib/core/concurrency.ts";
import {
	PRACTICE_SCRIPT_PRACTICE_HEADER,
	practiceInputsSchema,
	type BoundModels,
	type Candidate,
	type ReviewOptions,
} from "./lib/core/contract.ts";
import { EvidenceReader, noReceipts, type EvidenceSources } from "./lib/core/evidence-reader.ts";
import { isJsonObject } from "./lib/core/json.ts";
import { resolveLeads } from "./lib/core/leads.ts";
import { hostModels, ModelBudget } from "./lib/core/models-host.ts";
import { runPracticeProgram, type ProgramOutcome } from "./lib/core/program-host.ts";
import { COMMIT, pullRequestRecord, REVIEWED } from "./run-bundled.ts";
import { REQUESTED_PULL_REQUEST, stagePracticeScripts } from "./stage.ts";
import { startStandIn, type StandInRequest } from "./test/proxy-stand-in.ts";

const { env } = process;
const chatBase = env.HEPHAESTUS_LIVE_LLM_BASE_URL ?? "";
const chatKey = env.HEPHAESTUS_LIVE_LLM_API_KEY ?? "";
const chatModel = env.HEPHAESTUS_LIVE_LLM_MODEL ?? "";
const decisionBase = env.HEPHAESTUS_LIVE_DECISION_BASE_URL ?? "";
const decisionKey = env.HEPHAESTUS_LIVE_DECISION_API_KEY ?? "";
const decisionModel = env.HEPHAESTUS_LIVE_DECISION_MODEL ?? "";
const embeddingModel = env.HEPHAESTUS_LIVE_EMBEDDING_MODEL ?? "";
const rerankingModel = env.HEPHAESTUS_LIVE_RERANKING_MODEL ?? "";
const live = chatBase !== "" && chatKey !== "" && chatModel !== "";
const TOKEN = "live-practice-script-token";
/** How long one program may run: a self-hosted model can be slow to answer. */
const PROGRAM_MS = 170_000;

const roots: string[] = [];
after(async () =>
	Promise.all(roots.map(async (root) => rm(root, { recursive: true, force: true }))),
);

/** The change both live suites review: added comments, which every slot of the fixture reads. */
const CHANGE = await readFile(path.join(import.meta.dirname, "fixtures/cart-change.diff"), "utf8");

/** The every-slot fixture, the program the server's PracticeScriptLiveLlmTest runs too. */
const EVERY_SLOT = await readFile(path.join(import.meta.dirname, "fixtures/every-slot.ts"), "utf8");

/**
 * A program that rates the added comments with a decision model and returns its own result: what it
 * could not settle, with the coverage it claims.
 */
const DECIDES = `import { addedComments, commentKinds, expectKind, requireChange, scm } from "../kits/scm/index.ts";
import { classify, definePracticeScript } from "../lib/core/practice-script.ts";

export default definePracticeScript({
	meta: { models: { decision: "required" } },
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const { files } = scm(ctx);
		const comments = addedComments(requireChange(files));
		const { ratings, unrated } = await classify({
			model: ctx.models.decision,
			set: commentKinds,
			items: comments,
			what: "added comments",
		});
		const [first] = comments;
		if (first === undefined) {
			throw new Error("the change adds no comment");
		}
		const citation = await files.change.cite(first.at.change, "NEW", first.at.line, first.at.endLine);
		return {
			outcome: "COULD_NOT_DETERMINE",
			summary: "The comments were rated, and the practice needs a reviewer",
			explanation: ratings.size + " of " + comments.length + " added comments were rated.",
			citations: [citation],
			evidence: {
				openQuestions: [
					{ openQuestion: "Does each rated comment meet the practice?", wouldSettleIt: "A review of the rated comments." },
				],
			},
			coverage:
				unrated.length === 0
					? { source: "claimed", selected: comments.length, checked: ratings.size, total: comments.length, complete: true }
					: { source: "claimed", selected: comments.length, checked: ratings.size, total: comments.length, complete: false, omitted: unrated },
		};
	},
});
`;

/** Each slot's real endpoint: the stand-in forwards a call there with the real key. */
function upstream(slot: string, operation: string): { url: string; key: string } {
	if (slot === "decision" && operation === "decisions") {
		return { url: `${decisionBase.replace(/\/+$/u, "")}/decisions`, key: decisionKey };
	}
	return { url: `${chatBase.replace(/\/+$/u, "")}/${operation}`, key: chatKey };
}

/**
 * One small request to each self-hosted model before the runs. A host can take minutes to load a
 * model after a pause, which is longer than a program's deadline: the host then reports the slot as
 * unrated for its deadline, which is correct, but is not what these tests prove.
 */
async function warmUp(): Promise<void> {
	const calls: [string, object][] = [
		[
			"chat/completions",
			{ model: chatModel, messages: [{ role: "user", content: "Hi" }], max_tokens: 1 },
		],
		...(embeddingModel === ""
			? []
			: [["embeddings", { model: embeddingModel, input: ["Hi"] }] as [string, object]]),
		...(rerankingModel === ""
			? []
			: [
					["rerank", { model: rerankingModel, query: "Hi", documents: ["Hi"] }] as [string, object],
				]),
	];
	await Promise.all(
		calls.map(async ([operation, body]) => {
			const { url, key } = upstream("chat", operation);
			await fetch(url, {
				method: "POST",
				headers: { authorization: `Bearer ${key}`, "content-type": "application/json" },
				body: JSON.stringify(body),
				signal: AbortSignal.timeout(170_000),
			});
		}),
	);
}

before(
	async () => {
		if (live) {
			await warmUp();
		}
	},
	{ timeout: 180_000 },
);

interface LiveRun {
	outcome: ProgramOutcome;
	reviews: ReviewOptions[];
	requests: StandInRequest[];
	log: string;
}

/** Run the program `source` as the practice `slug` through the host, with `bound` behind the stand-in. */
async function runLive(slug: string, source: string, bound: BoundModels): Promise<LiveRun> {
	const standIn = await startStandIn(TOKEN, async (request) => {
		const { url, key } = upstream(request.slot, request.operation);
		const response = await fetch(url, {
			method: "POST",
			headers: { authorization: `Bearer ${key}`, "content-type": "application/json" },
			body: JSON.stringify(request.body),
		});
		const body: unknown = await response.json();
		return response.ok ? body : { status: response.status, body };
	});
	try {
		const { root, practices } = await stagePracticeScripts({ [slug]: source }, {});
		roots.push(root);
		const repo = path.join(root, "repo");
		await cp(path.join(import.meta.dirname, "lib"), path.join(repo, "lib"), { recursive: true });
		const change = { base: "b".repeat(40), head: COMMIT, files: parseDiff(CHANGE) };
		const sources: EvidenceSources = {
			repo,
			headRevision: COMMIT,
			treeSourceFile: "repos/reviewed/.git/HEAD",
			contextDir: null,
			contextReference: "context",
			change,
			sourceKindOf: () => undefined,
		};
		const { inputs: stagedInputs } = REQUESTED_PULL_REQUEST;
		assert.ok(isJsonObject(stagedInputs));
		const inputs = practiceInputsSchema.parse({
			...stagedInputs,
			previous: null,
			item: pullRequestRecord(),
		});
		const script = path.join(practices, `${slug}.ts`);
		const receipts = noReceipts();
		const reviews: ReviewOptions[] = [];
		const log: string[] = [];
		const outcome = await runPracticeProgram(
			{ script: { path: script, source: await readFile(script) } },
			{
				slug,
				inputs,
				files: { head: { revision: COMMIT }, change },
				now: new Date().toISOString(),
				deadline: Date.now() + PROGRAM_MS,
			},
			{
				reader: new EvidenceReader(sources, receipts),
				models: hostModels(bound, standIn.url, TOKEN, slug),
				budget: new ModelBudget(200_000, MODEL_CONCURRENCY),
				// The review session is the host's, never a direct call: this fake answers it over IPC.
				review: async (options): Promise<Candidate> => {
					await resolveLeads(options.leads ?? [], sources);
					reviews.push(options);
					return REVIEWED;
				},
				signal: new AbortController().signal,
				log: (line) => {
					log.push(line);
				},
				receipts,
			},
		);
		return { outcome, reviews, requests: standIn.requests, log: log.join("\n") };
	} finally {
		await standIn.close();
	}
}

/** Every request left the host with the practice's token and its name. */
function attributed(requests: readonly StandInRequest[], slug: string): void {
	assert.ok(requests.every((r) => r.headers.authorization === `Bearer ${TOKEN}`));
	assert.ok(requests.every((r) => r.headers[PRACTICE_SCRIPT_PRACTICE_HEADER] === slug));
}

void test(
	"live: every slot through the host against real models, and one review through IPC",
	{ skip: !live && "set HEPHAESTUS_LIVE_LLM_* to run", timeout: 200_000 },
	async () => {
		const bound: BoundModels = {
			chat: { protocol: "openai-completions", modelId: chatModel },
			// Without a decision endpoint, the chat model serves the decision slot through its logprobs.
			decision:
				decisionBase === ""
					? { protocol: "openai-completions", modelId: chatModel }
					: { protocol: "openai-decisions", modelId: decisionModel },
			...(embeddingModel === ""
				? {}
				: { embedding: { protocol: "openai-embeddings" as const, modelId: embeddingModel } }),
			...(rerankingModel === ""
				? {}
				: { reranking: { protocol: "cohere-rerank" as const, modelId: rerankingModel } }),
		};
		const { outcome, reviews, requests, log } = await runLive("every-slot", EVERY_SLOT, bound);
		assert.equal(outcome.status, "ok", `${outcome.error ?? ""}\n${log}`);
		assert.deepEqual(outcome.candidate, REVIEWED);
		assert.equal(outcome.calls.review, 1);
		for (const slot of Object.keys(bound)) {
			assert.ok(
				requests.some((r) => r.slot === slot),
				`${slot} was called\n${log}`,
			);
		}
		assert.deepEqual(
			outcome.models
				.filter((report) => report.bound)
				.map((report) => [report.slot, report.failures]),
			Object.keys(bound).map((slot) => [slot, {}]),
			`each bound slot answered every call\n${log}`,
		);
		const [review] = reviews;
		assert.ok(review !== undefined && reviews.length === 1);
		assert.deepEqual(review.limitations, [], log);
		assert.equal(review.facts?.comments, 5);
		// The agent read the repository through the host, which kept a receipt of each read.
		assert.ok(outcome.coverageReached.reads > 0, log);
		attributed(requests, "every-slot");
	},
);

void test(
	"live: a chat model serves the decision slot through its logprobs, for a program that returns its own result",
	{ skip: !live && "set HEPHAESTUS_LIVE_LLM_* to run", timeout: 200_000 },
	async () => {
		const { outcome, reviews, requests, log } = await runLive("decides", DECIDES, {
			decision: { protocol: "openai-completions", modelId: chatModel },
		});
		assert.equal(outcome.status, "ok", `${outcome.error ?? ""}\n${log}`);
		assert.equal(requests.filter((r) => r.slot === "decision").length, 1, log);
		assert.deepEqual(outcome.models, [
			{ slot: "decision", need: "required", bound: true, failures: {} },
		]);
		assert.partialDeepStrictEqual(outcome.candidate, {
			outcome: "COULD_NOT_DETERMINE",
			citations: [{ sourceKind: "scm.pull-request.diff", path: "src/cart.ts", startLine: 2 }],
			coverage: { source: "claimed", selected: 5, checked: 5, total: 5, complete: true },
		});
		assert.deepEqual(outcome.calls, {
			review: 0,
			chat: 0,
			decision: 1,
			embedding: 0,
			reranking: 0,
		});
		assert.deepEqual(reviews, []);
		attributed(requests, "decides");
	},
);
