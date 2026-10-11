// The example of `docs/admin/practice-scripts.mdx` § Different work for each cause: one script that
// does different work on a request, a recheck, and each moment of the practice.
import {
	addedComments,
	definePracticeScript,
	expectKind,
	type DiffFile,
	type Lead,
} from "../lib/practice-script.ts";

const OPEN_WORK = /\b(?:TODO|FIXME)\b/u;
const TRACKING = /#\d+|\b[A-Z][A-Z0-9]+-\d+\b|https?:\/\/\S+/u;

export default definePracticeScript({
	meta: {
		version: 1,
		kinds: { "untracked-todo": "A TODO that the change adds, with no tracking reference." },
	},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const todos = addedComments(ctx.change).filter(
			(comment) => OPEN_WORK.test(comment.text) && !TRACKING.test(comment.text),
		);
		// The leads in the files of `only`, or in the whole change.
		const leads = (only?: ReadonlyMap<string, DiffFile>): Lead[] =>
			todos
				.filter((comment) => only === undefined || only.has(comment.at.change))
				.map((comment) => ({ at: comment.at, kind: "untracked-todo" }));

		const { cause } = ctx;
		if (cause.type === "requested") {
			return {
				leads: leads(),
				directions: ["A person asked for this review. Judge the work as it is now."],
			};
		}
		if (cause.type === "recheck") {
			return {
				leads: leads(),
				directions: ["The last review found untracked TODOs. Check if the new commits track them."],
			};
		}
		if (cause.signal === "scm.pull_request.synchronized") {
			// Only the files that the new commits changed, when that change is known.
			return { leads: leads(ctx.previous?.changeSince) };
		}
		if (cause.signal === "scm.pull_request.merged") {
			return {
				leads: leads(),
				directions: ["The work is merged. Each untracked TODO is now on the target branch."],
			};
		}
		return { leads: leads() };
	},
});
