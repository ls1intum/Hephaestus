// While its review runs, the program sends a second review request past `ctx.review`, as forged code
// can. The host refuses it: one review of a practice runs at a time.
import { definePracticeScript } from "../lib/core/practice-script.ts";

const FORGED_ID = 1_000_000;

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const answered = Promise.withResolvers<string>();
		process.on("message", (message: unknown) => {
			if (
				typeof message === "object" &&
				message !== null &&
				"id" in message &&
				message.id === FORGED_ID
			) {
				answered.resolve("reason" in message ? String(message.reason) : "answered");
			}
		});
		const review = ctx.review();
		process.send?.(JSON.stringify({ kind: "review", id: FORGED_ID, options: {} }));
		ctx.log(`nested review: ${await answered.promise}`);
		return review;
	},
});
