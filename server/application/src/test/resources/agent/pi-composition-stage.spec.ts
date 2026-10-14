import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import nodePath from "node:path";
import { test } from "node:test";
import {
	createReviewRecorder,
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
