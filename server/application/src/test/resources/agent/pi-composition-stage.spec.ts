import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import nodePath from "node:path";
import { test } from "node:test";
import { parseAdmission } from "../../../main/resources/agent/pi-admitted-results.ts";
import {
	createReviewRecorder,
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
			withheld: [{ basedOn: [{ resultId }], reason: "BELOW_BAR" }],
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
