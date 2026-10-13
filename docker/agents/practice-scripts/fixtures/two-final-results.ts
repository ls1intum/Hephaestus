// A program that sends a result of its own before it returns: two final messages from one program.
import { definePracticeScript } from "../lib/practice-script.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const candidate = await ctx.review();
		process.send?.(JSON.stringify({ kind: "result", value: candidate }));
		return candidate;
	},
});
