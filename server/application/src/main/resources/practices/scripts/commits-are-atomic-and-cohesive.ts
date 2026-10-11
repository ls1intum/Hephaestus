// Finds each authored commit in the commit record: its subject, if the subject joins clauses, and the
// files that the commit touched with their line counts and moves. The criteria tell what one logical
// change is, and the review judges the partition. A fact is a place to look, never a verdict.
import { readCapturedCommits } from "../lib/change.ts";
import {
	COMMIT_KIND,
	commitLeads,
	describeCommitCount,
	subjectFacts,
} from "../lib/commit-subjects.ts";
import { definePracticeScript, expectKind } from "../lib/practice-script.ts";

export default definePracticeScript({
	meta: {
		version: 1,
		kinds: {
			[COMMIT_KIND]:
				"An authored commit of the reviewed range, with the shape of its subject and the files that it touched.",
		},
	},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const captured = await readCapturedCommits(ctx.context.dir);
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
		return {
			leads: commitLeads(facts).slice(0, 200),
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
		};
	},
});
