// A model call that never answers, next to a line that plain code found. The call ends unrated when
// the host stops the program, and the program still has the time it takes to return its own result.
import { setTimeout as sleep } from "node:timers/promises";

import { definePracticeScript, embedMany, step } from "../lib/practice-script.ts";

export default definePracticeScript({
	meta: { models: { embedding: "required" } },
	async run(ctx) {
		const found = await ctx.files?.change?.cite("src/cart.ts", "NEW", 2);
		if (found === undefined) {
			throw new Error("the change was not captured");
		}
		const outcome = await step("slow", async () =>
			embedMany({ model: ctx.models.embedding, values: ["x"] }),
		);
		await sleep(500);
		const reason = "unrated" in outcome ? outcome.unrated : "selection";
		return {
			outcome: "UNDETERMINED",
			summary: "The added comment was not compared with the others",
			explanation: "Plain code found the comment. The embedding that compares it gave no answer.",
			citations: [found],
			evidence: {
				undecidability: [
					{
						openQuestion: "Does the comment repeat another one?",
						wouldSettleIt: "An embedding of the added comments.",
						citation: found,
					},
				],
			},
			coverage: {
				source: "claimed",
				selected: 1,
				checked: 0,
				total: 1,
				complete: false,
				omitted: [{ what: "embedding of the added comments", count: 1, reason }],
			},
		};
	},
});
