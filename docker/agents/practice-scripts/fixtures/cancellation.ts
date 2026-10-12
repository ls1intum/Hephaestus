// A program that ignores the host's abort: it logs what its review answered, and every reply that
// reaches it later, then never returns. The host ends its process.
import { definePracticeScript, reasonOf } from "../lib/practice-script.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		process.on("message", (message: unknown) => {
			if (
				typeof message === "object" &&
				message !== null &&
				"kind" in message &&
				message.kind === "review-result"
			) {
				ctx.log("late reply: review-result");
			}
		});
		await ctx.review().then(
			() => {
				ctx.log("review: answered");
			},
			(error: unknown) => {
				ctx.log(`review: ${reasonOf(error)}`);
			},
		);
		return Promise.withResolvers<never>().promise;
	},
});
