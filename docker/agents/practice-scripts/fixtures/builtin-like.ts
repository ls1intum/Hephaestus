// A custom program that ends as the built-in does, with its review's result, but prepares the
// review's input in code first: a lead for each added TODO, a fact, a direction and a limitation.
import {
	addedComments,
	definePracticeScript,
	expectKind,
	requireChange,
} from "../lib/practice-script.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const comments = addedComments(requireChange(ctx.files));
		return ctx.review({
			leads: comments
				.filter((comment) => /\bTODO\b/u.test(comment.text))
				.map((comment) => ({ at: comment.at, label: "An added TODO comment." })),
			facts: { comments: comments.length },
			directions: ["Check each added TODO against the criteria."],
			limitations: [{ what: "comments in generated files", count: 1, reason: "selection" }],
		});
	},
});
