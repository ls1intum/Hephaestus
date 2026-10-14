// A `meta` the host refuses: the program fails before it says which models it uses.
import { definePracticeScript } from "../lib/core/practice-script.ts";

export default definePracticeScript({
	// @ts-expect-error -- "sometimes" is not a need, which is the point of this fixture.
	meta: { models: { decision: "sometimes" } },
	run: async (ctx) => ctx.review(),
});
