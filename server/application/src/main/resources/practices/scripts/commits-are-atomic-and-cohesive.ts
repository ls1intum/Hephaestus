import { readCapturedCommits } from "../kits/scm/change.ts";
import { commitLeads, describeCommitCount, subjectFacts } from "../kits/scm/commit-subjects.ts";
import { scm, expectKind } from "../kits/scm/index.ts";
import { definePracticeScript } from "../lib/core/practice-script.ts";

export const LABELS = {
	commit:
		"An authored commit of the reviewed range, with the shape of its subject and the files that it touched.",
};

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		const captured = await readCapturedCommits(ctx.evidence);
		const facts = subjectFacts(captured ?? []);
		const authored = facts.filter((f) => !f.merge);
		let directions = [
			describeCommitCount(facts),
			"`joinedClauses` marks a subject that joins clauses with 'and', '&', '+', a comma, a semicolon or a sentence break, or a body with two or more bullets; `moved` counts files renamed with no line changed. Both describe the record, not how many changes a commit holds.",
		];
		if (captured === null) {
			directions = [
				"commits.json is missing or malformed: a collection gap, not a history without authored commits.",
			];
		} else if (authored.length === 0) {
			directions = ["No authored commit in the reviewed range: only merge commits, or none."];
		}
		return ctx.review({
			leads: commitLeads(facts, LABELS.commit).slice(0, 200),
			...(captured === null
				? {}
				: {
						facts: {
							authoredCommits: authored.length,
							mergeCommits: facts.length - authored.length,
							joinedSubjects: authored.filter((f) => f.joinedClauses).length,
						},
					}),
			directions,
		});
	},
});
