import { readCapturedCommits } from "../kits/scm/change.ts";
import { scm, expectKind } from "../kits/scm/index.ts";
import { DIRECTION_CHARS, oneLine } from "../lib/core/bounded-text.ts";
import { definePracticeScript } from "../lib/core/practice-script.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		const commits = await readCapturedCommits(ctx.evidence);
		return ctx.review({
			leads: [],
			facts: commits === null ? {} : { capturedCommitCount: commits.length },
			directions: [
				oneLine(
					`Captured source branch: ${domain.item.source_branch}; target branch: ${domain.item.target_branch}.`,
					DIRECTION_CHARS,
				),
				commits === null
					? "The commit range was not captured; its size is unknown, not zero."
					: `The captured pull request range contains ${String(commits.length)} commit(s); this is not a count of commits ahead of the integration branch.`,
				"A target name alone does not establish where this branch was created or which branch the team uses for integration. Inspect the captured branch and history evidence before deciding; do not infer a clean cut or an off-integration cut from a name or commit count.",
			],
		});
	},
});
