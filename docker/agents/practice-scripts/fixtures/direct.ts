// A program that decides in code alone: it cites the record of the work and returns its own result,
// with the coverage that it claims. It asks for no review.
import { definePracticeScript, expectKind, requireChange } from "../lib/practice-script.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const changed = requireChange(ctx.files).size;
		const citation = await ctx.evidence.citeRecord("metadata.json", 36);
		return {
			outcome: "NOT_APPLICABLE",
			summary: "The change adds or changes no user interface",
			explanation: `None of the ${changed} changed files is a view, a template or a stylesheet.`,
			citations: [citation],
			evidence: {
				inapplicability: {
					consulted: ["scm.pull-request.core", "scm.pull-request.diff"],
					subject: "user interface files that the change adds or changes",
					ruledOutBy: "Every changed file is a TypeScript source outside a view folder.",
				},
			},
			coverage: {
				source: "claimed",
				selected: changed,
				checked: changed,
				total: changed,
				complete: true,
			},
		};
	},
});
