import { scm, addedComments, expectKind, requireChange } from "../kits/scm/index.ts";
import { definePracticeScript } from "../lib/core/practice-script.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		const comments = addedComments(requireChange(domain.files));
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
