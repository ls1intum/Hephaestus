// Finds each authored commit subject in the commit record, with its shapes: bare, repeated or cut off.
// A shape is a place to check, not a lapse.
import { readCapturedCommits } from "../lib/change.ts";
import {
	COMMIT_KIND,
	describeCommitCount,
	subjectFacts,
	subjectLeads,
} from "../lib/commit-subjects.ts";
import { definePracticeScript, expectKind } from "../lib/practice-script.ts";

export default definePracticeScript({
	meta: {
		version: 1,
		kinds: {
			[COMMIT_KIND]:
				"An authored commit of the reviewed range, with the shapes of its subject: bare, repeated or cut off.",
		},
	},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const captured = await readCapturedCommits(ctx.context.dir);
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
		return {
			leads: subjectLeads(facts).slice(0, 200),
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
		};
	},
});
