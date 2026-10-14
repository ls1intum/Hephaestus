import { definePracticeScript, reasonOf } from "../lib/core/practice-script.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const first = ctx.review({ directions: ["The first review."] });
		const second = await ctx.review({ directions: ["The second review."] }).then(
			() => "answered",
			(error: unknown) => reasonOf(error),
		);
		ctx.log(`second review: ${second}`);
		return first;
	},
});
