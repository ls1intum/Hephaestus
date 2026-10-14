import { readCapturedCommits } from "../kits/scm/change.ts";
import { scm, expectKind } from "../kits/scm/index.ts";
import { branchIssueReferences, issueNumberReferences } from "../kits/scm/references.ts";
import { checkableItems } from "../kits/scm/review.ts";
import { DIRECTION_CHARS, listWithin, oneLine } from "../lib/core/bounded-text.ts";
import { definePracticeScript } from "../lib/core/practice-script.ts";

const BRANCH_CHARS = 80;

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		const description = domain.item.body ?? "";
		const directions: string[] = [];

		const commits = await readCapturedCommits(ctx.evidence);
		const body = `${description}\n${(commits ?? []).map((c) => c.message).join("\n")}`;
		const branch = domain.item.source_branch;
		const quotedBranch = oneLine(branch, BRANCH_CHARS);
		const bodyRefs = new Set(issueNumberReferences(body).map((n) => `#${n}`));
		const branchRefs = new Set(branchIssueReferences(branch).map((n) => `#${n}`));
		const allRefs = new Set<string>([...bodyRefs, ...branchRefs]);
		if (allRefs.size > 0) {
			const encoded =
				branchRefs.size > 0
					? ` (branch '${quotedBranch}' encodes ${listWithin([...branchRefs], 40)})`
					: "";
			const opening = "Issue-mention syntax candidates: ";
			directions.push(
				`${opening}${listWithin([...allRefs], DIRECTION_CHARS - opening.length - encoded.length - 1)}${encoded}.`,
				"Inspect each mention in context before treating it as the author's motivating issue: templates, examples and branch numbers may be unrelated. A genuine reference need not contain a closing keyword.",
			);
		} else {
			directions.push(
				commits === null
					? `Traceability fact: no issue reference (#N, 'Refs #N', closing keyword, or issue-number branch prefix) was found in the body or branch '${quotedBranch}'. The commits were not captured, so their messages were not searched.`
					: `Traceability fact: no issue reference (#N, 'Refs #N', closing keyword, or issue-number branch prefix) was found in the body, the ${commits.length} commit message(s), or branch '${quotedBranch}'.`,
				"Confirm in the body before concluding the handoff is untraceable.",
			);
		}

		const items = checkableItems(description);
		const ticked = items.filter((item) => item.checked).length;
		const unticked = items.length - ticked;
		if (ticked + unticked > 0) {
			directions.push(
				`Checklist fact: the description carries ${ticked} ticked and ${unticked} unticked checkbox line(s). These counts say neither whose lines they are nor what was done.`,
				"Where work/change/description.authored.md exists, it separates the form's lines from the author's; otherwise compare description.md with the form.",
				"Judge whether the author adopted the checklist or wrote equivalent prose; a form's line left unticked as supplied is boilerplate, not unfinished work, and a tick is a claim, not a result.",
			);
		}

		return ctx.review({
			leads: [],
			facts: {
				issueMentionSyntaxCandidateCount: allRefs.size,
				...(commits === null ? {} : { commitCount: commits.length }),
				checklistTicked: ticked,
				checklistUnticked: unticked,
			},
			directions,
		});
	},
});
