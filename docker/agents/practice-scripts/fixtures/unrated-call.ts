// One embedding call, reported with the reason it was not rated. When the script cancels it, the
// runner stops waiting and the reason is "deadline".
import { definePracticeScript, embedMany, step } from "../lib/practice-script.ts";

export default definePracticeScript({
	meta: { version: 1, models: { embedding: "required" }, kinds: { unused: "Not used." } },
	async run(ctx) {
		const started = performance.now();
		const outcome = await step("slow", async () =>
			embedMany({
				model: ctx.models.embedding,
				values: ["x"],
				abortSignal: AbortSignal.timeout(1000),
			}),
		);
		return {
			leads: [],
			facts: {
				reason: "unrated" in outcome ? outcome.unrated : "rated",
				fast: performance.now() - started < 3000,
			},
		};
	},
});
