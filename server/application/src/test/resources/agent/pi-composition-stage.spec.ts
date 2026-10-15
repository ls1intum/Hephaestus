import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import nodePath from "node:path";
import { test } from "node:test";
import { parseAdmission } from "../../../main/resources/agent/pi-admitted-results.ts";
import {
	createReviewRecorder,
	buildReviewTool,
	closeUnroutedPrivateDestinations,
	persistComposedFeedback,
	type ComposedFeedback,
	type PublicReviewState,
} from "../../../main/resources/agent/pi-composition-stage.ts";

void test("a review becomes final only after its complete envelope is written", async () => {
	const cwd = mkdtempSync(nodePath.join(tmpdir(), "pi-composition-stage-"));
	try {
		const output = nodePath.join(cwd, "out/feedback.json");
		const feedback: ComposedFeedback = {
			compositionFailures: [],
			contractVersion: 3,
			admissionDigest: "a".repeat(64),
			results: [],
			preparedTargets: [],
			feedback: [],
			review: null,
			preparation: null,
		};
		const state: PublicReviewState = { final: false, reviewContext: "HELD" };
		const resultId = "123e4567-e89b-42d3-a456-426614174000";
		const record = createReviewRecorder(
			false,
			new Map([
				[
					resultId,
					{
						practiceSlug: "secure-authentication",
						outcome: "NOT_MET",
						problems: [{ problemId: "problem-1", citations: [] }],
					},
				],
			]),
			new Map(),
			state,
			() => "",
			{
				admitted: () => true,
				feedback,
				persist: () => persistComposedFeedback(feedback, output),
				onStored: () => undefined,
				refusal: async (_id, text) => ({
					content: [{ type: "text", text }],
					details: { stored: 0 },
				}),
			},
		);
		const quiet = { decisions: [{ resultId, disposition: "BELOW_BAR" }] };
		await assert.rejects(record("first", quiet), { code: "ENOENT" });
		assert.equal(feedback.review, null);
		assert.equal(state.final, false);

		mkdirSync(nodePath.dirname(output));
		await record("retry", quiet);
		const stored = readFileSync(output, "utf8");
		assert.deepEqual(feedback.review, {
			summary: null,
			inline: [],
			withheld: [{ basedOn: [{ resultId }], reason: "BELOW_BAR", witnessIds: [] }],
		});
		assert.equal(state.final, true);
		const repeated = await record("repeat", { decisions: [] });
		assert.equal(repeated.details.stored, 0);
		assert.equal(readFileSync(output, "utf8"), stored);
	} finally {
		rmSync(cwd, { recursive: true, force: true });
	}
});

void test("missing private routing closes only that destination and records its own failure", () => {
	const response: unknown = JSON.parse(
		readFileSync(new URL("admission-response.json", import.meta.url), "utf8"),
	);
	const admission = parseAdmission(response);
	admission.practicePageSupport = null;
	admission.conversationEligibility = {
		state: "COMPLETE",
		readAt: "2026-10-14T00:00:00Z",
		results: admission.results.map((result) => ({
			resultId: result.resultId,
			decision: "ADMIT",
			stopReason: null,
		})),
	};
	const request = {
		contractVersion: 3 as const,
		destinations: {
			ON_WORK: { maxPieces: 1 },
			PRACTICE_PAGE: { maxPieces: 1 },
			CONVERSATION: { maxPieces: 1 },
		},
		minDistinctItems: 2,
		onWorkCopyForms: [],
	};
	const feedback: ComposedFeedback = {
		compositionFailures: [],
		contractVersion: 3,
		admissionDigest: admission.digest,
		results: [],
		preparedTargets: [],
		feedback: [],
		review: null,
		preparation: null,
	};
	const routes = closeUnroutedPrivateDestinations(request, admission, feedback);
	assert.equal("PRACTICE_PAGE" in request.destinations, false);
	assert.deepEqual(request.destinations.CONVERSATION, { maxPieces: 1 });
	assert.deepEqual(request.destinations.ON_WORK, { maxPieces: 1 });
	assert.deepEqual(routes.get("handles-errors"), ["CONVERSATION"]);
	assert.deepEqual(feedback.compositionFailures, [
		{ phase: "PRIVATE_FEEDBACK", reason: "RUNTIME_ERROR" },
	]);
});

void test("preparation retains exact copy support, the second citation, quiet witnesses and page fields", async (t) => {
	const { prepareComposedFeedback } =
		await import("../../../main/resources/agent/pi-composition-stage.ts");
	const resultId = "00000000-0000-4000-8000-000000000001";
	const quietId = "00000000-0000-4000-8000-000000000002";
	const problemId = "00000000-0000-4000-8000-000000000003";
	const previousId = "00000000-0000-4000-8000-000000000004";
	const support = [{ resultId, problemId }];
	const feedback: ComposedFeedback = {
		contractVersion: 3,
		admissionDigest: "a".repeat(64),
		compositionFailures: [],
		preparedTargets: [
			{
				threadKey: "queued-token",
				feedbackId: previousId,
				destination: "CONVERSATION",
				practiceSlug: "tokens",
			},
		],
		preparation: null,
		results: [
			{ resultId, practiceSlug: "tokens", outcome: "NOT_MET", problems: [], citations: [] },
		],
		review: {
			summary: null,
			inline: [
				{
					body: "Check the token here.",
					basedOn: support,
					anchor: { problemId, citationIndex: 1 },
				},
			],
			withheld: [
				{
					basedOn: [{ resultId: quietId }],
					reason: "ALREADY_SAID",
					witnessIds: ["github:review-comment:7"],
				},
			],
		},
		feedback: [
			{
				destination: "PRACTICE_PAGE",
				practiceSlug: "tokens",
				action: "NEW",
				basedOn: support,
				title: "Guard the token boundary",
				body: "Unchecked tokens reach the handler across these changes.",
				nextStep: "Check invalid tokens before forwarding.",
			},
			{
				destination: "CONVERSATION",
				practiceSlug: "tokens",
				action: "SUPERSEDE",
				basedOn: support,
				title: "Check token boundaries",
				supersedesThreadKey: "queued-token",
				notes: {
					situation: "Unchecked tokens reach the handler.",
					capability: "Recognize the trust boundary.",
					evidenceSummary: "The handler forwards an unchecked token.",
					inConversationLearning: "Explain where token checks belong.",
				},
			},
		],
	};
	const requests: unknown[] = [];
	t.mock.method(globalThis, "fetch", async (_input: unknown, init: RequestInit) => {
		assert.ok(typeof init.body === "string");
		requests.push(JSON.parse(init.body));
		return Response.json({
			feedbackIds: [],
			contentDigest: "a".repeat(64),
			preparedAt: "2026-10-15T00:00:00Z",
		});
	});
	await prepareComposedFeedback({
		feedback,
		admitted: true,
		expired: false,
		url: "https://example.test/prepare",
		token: "synthetic",
		task: { reviewId: resultId, jobId: quietId, attempt: 0 },
		timeoutMs: 1000,
		persist: () => undefined,
		recordFailure: () => assert.fail("valid packages must be prepared"),
	});
	assert.equal(requests.length, 3);
	const text = JSON.stringify(requests);
	assert.match(text, /"citationIndex":1/u);
	assert.match(text, /"witnessIds":\["github:review-comment:7"\]/u);
	assert.match(
		text,
		/"page":\{"title":"Guard the token boundary","body":"Unchecked tokens reach the handler across these changes\.","nextStep":"Check invalid tokens before forwarding\."\}/u,
	);
	assert.match(text, new RegExp(`"replacesId":"${previousId}"`, "u"));
});

void test("a refused private package preserves accepted public feedback", async (t) => {
	const { prepareComposedFeedback } =
		await import("../../../main/resources/agent/pi-composition-stage.ts");
	const resultId = "00000000-0000-4000-8000-000000000001";
	const feedback: ComposedFeedback = {
		contractVersion: 3,
		admissionDigest: "a".repeat(64),
		compositionFailures: [],
		preparedTargets: [],
		preparation: null,
		results: [
			{ resultId, practiceSlug: "tokens", outcome: "NOT_MET", problems: [], citations: [] },
		],
		review: {
			summary: { body: "Check the token before forwarding.", basedOn: [{ resultId }] },
			inline: [],
			withheld: [],
		},
		feedback: [
			{
				destination: "CONVERSATION",
				practiceSlug: "tokens",
				action: "NEW",
				basedOn: [{ resultId }],
				title: "Token boundaries",
				notes: {
					situation: "Unchecked tokens.",
					capability: "Locate the boundary.",
					evidenceSummary: "The handler forwards unchecked tokens.",
					inConversationLearning: "Explain the boundary.",
				},
			},
		],
	};
	let calls = 0;
	t.mock.method(globalThis, "fetch", async () => {
		calls += 1;
		return calls === 1
			? Response.json({
					feedbackIds: [],
					contentDigest: "a".repeat(64),
					preparedAt: "2026-10-15T00:00:00Z",
				})
			: new Response(null, { status: 409 });
	});
	const failures: string[] = [];
	await prepareComposedFeedback({
		feedback,
		admitted: true,
		expired: false,
		url: "https://example.test/prepare",
		token: "synthetic",
		task: { reviewId: resultId, jobId: resultId, attempt: 0 },
		timeoutMs: 1000,
		persist: () => undefined,
		recordFailure: (phase, reason) => {
			failures.push(`${phase}:${reason}`);
		},
	});
	assert.ok(feedback.review?.summary);
	assert.equal(feedback.preparation?.contentDigest, "a".repeat(64));
	assert.deepEqual(feedback.feedback, []);
	assert.deepEqual(failures, ["PRIVATE_FEEDBACK:SERVER_REFUSED"]);
});

void test("the card tool and preparation share the server's title/body/nextStep fixture", async (t) => {
	const { buildFeedbackTool, prepareComposedFeedback } =
		await import("../../../main/resources/agent/pi-composition-stage.ts");
	const fixture: unknown = JSON.parse(
		readFileSync(new URL("composer-card.json", import.meta.url), "utf8"),
	);
	const { z } =
		await import("../../../main/resources/agent/work/practice-scripts/lib/core/practice-script.ts");
	const card = z
		.object({
			reported: z.record(z.string(), z.unknown()),
			prepared: z.record(z.string(), z.unknown()),
		})
		.parse(fixture);
	const admission = parseAdmission(
		JSON.parse(readFileSync(new URL("admission-response.json", import.meta.url), "utf8")),
	);
	const result = admission.results[0];
	assert.ok(result);
	const prepared = structuredClone(card.prepared);
	// This admission fixture supplies the result identity used by the shared card fixture.
	assert.equal(result.resultId, "30000000-0000-4000-8000-000000000001");
	result.practiceSlug = "tokens";
	const feedback: ComposedFeedback = {
		contractVersion: 3,
		admissionDigest: admission.digest,
		compositionFailures: [],
		preparedTargets: [],
		preparation: null,
		results: [
			{
				resultId: result.resultId,
				practiceSlug: "tokens",
				outcome: "NOT_MET",
				problems: [],
				citations: [],
			},
		],
		review: null,
		feedback: [],
	};
	const tool = buildFeedbackTool(
		["tokens"],
		{
			contractVersion: 3,
			destinations: { PRACTICE_PAGE: { maxPieces: 1 } },
			minDistinctItems: 2,
			onWorkCopyForms: [],
		},
		[result],
		[],
		new Set(),
		{
			feedback,
			admitted: () => true,
			routes: () => ["PRACTICE_PAGE"],
			destinationRefusal: () => "",
			negativeItems: () => 2,
			documentedShape: (schema) => schema,
			listSchema: (items) => ({ type: "array", items }),
			submittedList: (value) =>
				Array.isArray(value) ? { items: value } : { error: "Expected list" },
			refusal: async (_id, text) => ({ content: [{ type: "text", text }], details: { stored: 0 } }),
			persist: () => undefined,
			paid: () => true,
			onStored: () => undefined,
		},
	);
	assert.ok(tool.prepareArguments);
	for (const wrapper of ["args", "arguments", "input"]) {
		assert.deepEqual(tool.prepareArguments({ [wrapper]: { feedback: [card.reported] } }), {
			feedback: [card.reported],
		});
	}
	assert.throws(
		() => tool.prepareArguments?.({ feedback: "card" }),
		/feedback.*Expected array.*Corrected field example/u,
	);
	const quietPiece = {
		destination: "PRACTICE_PAGE",
		practiceSlug: "tokens",
		basedOn: [result.resultId],
		action: "WITHHOLD",
		withholdReason: "BELOW_BAR",
		witnessIds: null,
		title: null,
		body: null,
		nextStep: null,
		notes: null,
	};
	assert.deepEqual(tool.prepareArguments({ feedback: [quietPiece] }), {
		feedback: [
			{
				destination: "PRACTICE_PAGE",
				practiceSlug: "tokens",
				basedOn: [result.resultId],
				action: "WITHHOLD",
				withholdReason: "BELOW_BAR",
				witnessIds: [],
			},
		],
	});
	const reply = z
		.object({ details: z.object({ stored: z.number() }) })
		.parse(
			await Reflect.apply(tool.execute.bind(tool), tool, ["card", { feedback: [card.reported] }]),
		);
	assert.equal(reply.details.stored, 1, JSON.stringify(reply));
	t.mock.method(globalThis, "fetch", async (_input: unknown, init: RequestInit) => {
		assert.ok(typeof init.body === "string");
		const posted = z
			.object({ composition: z.array(z.record(z.string(), z.unknown())) })
			.parse(JSON.parse(init.body));
		assert.deepEqual(posted.composition, [prepared]);
		return Response.json({
			feedbackIds: [],
			contentDigest: "a".repeat(64),
			preparedAt: "2026-10-15T00:00:00Z",
		});
	});
	await prepareComposedFeedback({
		feedback,
		admitted: true,
		expired: false,
		url: "https://example.test/prepare",
		token: "synthetic",
		task: { reviewId: result.resultId, jobId: result.resultId, attempt: 0 },
		timeoutMs: 1000,
		persist: () => undefined,
		recordFailure: () => assert.fail("the card should prepare"),
	});
});

void test("report_review accepts one wrapper and null unused lists, and explains wrong text before storing a correction", async () => {
	for (const wrapper of ["args", "arguments", "input"]) {
		const resultId = "123e4567-e89b-42d3-a456-426614174000";
		const feedback: ComposedFeedback = {
			contractVersion: 3,
			admissionDigest: "a".repeat(64),
			compositionFailures: [],
			results: [],
			preparedTargets: [],
			feedback: [],
			review: null,
			preparation: null,
		};
		const state: PublicReviewState = { final: false, reviewContext: "HELD" };
		const tool = buildReviewTool(
			false,
			new Map([[resultId, { practiceSlug: "authentication", outcome: "NOT_MET", problems: [] }]]),
			new Map(),
			state,
			() => "",
			{
				admitted: () => true,
				feedback,
				persist: () => undefined,
				onStored: () => undefined,
				refusal: async (_id, text) => {
					throw new Error(text);
				},
			},
		);
		assert.ok(tool.prepareArguments);
		const quiet = {
			decisions: [{ resultId, disposition: "BELOW_BAR", witnessIds: null }],
			inline: null,
		};
		assert.throws(
			() =>
				tool.prepareArguments?.({
					...quiet,
					decisions: [{ resultId, disposition: "BELOW_BAR", witnessIds: [] }],
					inline: [],
					summary: "No concern.",
				}),
			/summary.*Expected.*Corrected field example/u,
		);
		assert.throws(
			() =>
				tool.prepareArguments?.({
					...quiet,
					decisions: [{ resultId, disposition: "BELOW_BAR", witnessIds: [] }],
					inline: [],
					summary: null,
					unknown: true,
				}),
			/unknown.*only.*decisions.*Corrected field example/u,
		);
		const prepared = tool.prepareArguments({ [wrapper]: quiet });
		const reply: unknown = await Reflect.apply(tool.execute.bind(tool), tool, ["quiet", prepared]);
		assert.ok(typeof reply === "object" && reply !== null);
		const details: unknown = Reflect.get(reply, "details");
		assert.ok(typeof details === "object" && details !== null);
		assert.equal(Reflect.get(details, "stored"), 1);
		assert.equal(state.final, true);
		assert.deepEqual(feedback.review, {
			summary: null,
			inline: [],
			withheld: [{ basedOn: [{ resultId }], reason: "BELOW_BAR", witnessIds: [] }],
		});
	}
});
