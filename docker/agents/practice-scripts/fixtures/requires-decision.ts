// A program that cannot run without its decision model: without one, the practice is skipped.
import { definePracticeScript } from "../lib/practice-script.ts";

export default definePracticeScript({
	meta: { models: { decision: "required" } },
	run: async (ctx) => ctx.review(),
});
