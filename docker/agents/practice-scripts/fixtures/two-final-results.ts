import { definePracticeScript } from "../lib/core/practice-script.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const candidate = await ctx.review();
		process.send?.(JSON.stringify({ kind: "result", value: candidate }));
		return candidate;
	},
});
