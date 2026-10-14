// Finds each authored commit subject in the commit record, with its shapes: bare, repeated or cut off.
// A shape is a place to check, not a lapse.
import { readCapturedCommits } from "../kits/scm/change.ts";
import { describeCommitCount, subjectFacts, subjectLeads } from "../kits/scm/commit-subjects.ts";
import { scm, expectKind } from "../kits/scm/index.ts";
import { definePracticeScript } from "../lib/core/practice-script.ts";

export const LABELS = {
	commit:
		"An authored commit of the reviewed range, with the shapes of its subject: bare, repeated or cut off.",
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
			"Judge every subject on whether it names a specific action and object for a reader of the history. A flag names a shape to check, not a lapse.",
		];
		if (captured === null) {
			directions = [
				"commits.json is missing or malformed: a collection gap, not a history without authored commits.",
			];
		} else if (authored.length === 0) {
			directions = ["No authored commit in the reviewed range: only merge commits, or none."];
		}
		return ctx.review({
			leads: subjectLeads(facts, LABELS.commit).slice(0, 200),
			...(captured === null
				? {}
				: {
						facts: {
							authoredCommits: authored.length,
							mergeCommits: facts.length - authored.length,
							bareSubjects: authored.filter((f) => f.bare).length,
							repeatedSubjects: authored.filter((f) => f.repeat).length,
							cutOffSubjects: authored.filter((f) => f.cutOff).length,
						},
					}),
			directions,
		});
	},
});
