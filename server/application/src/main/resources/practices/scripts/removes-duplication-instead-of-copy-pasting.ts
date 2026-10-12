// Finds each pair of added blocks whose lines have the same shape when names and literals are set
// aside. The review decides if a pair is one unit of knowledge written twice, or an idiom that the
// platform makes look alike.
import { factsOf } from "../lib/bounded-text.ts";
import { duplicatePairs, MIN_RUN_LINES } from "../lib/duplication.ts";
import { isTestPath, languageOf } from "../lib/languages.ts";
import {
	definePracticeScript,
	expectKind,
	requireChange,
	type ReviewLead,
} from "../lib/practice-script.ts";

const MAX_PAIRS = 20;

export const LABELS = {
	"same-shaped-blocks":
		"An added block whose lines have the same shape as another added block when names and literals are set aside.",
};

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const change = requireChange(ctx.files);
		const sourceFiles = [...change.values()].filter(
			(file) => languageOf(file.path) !== null && !isTestPath(file.path),
		);
		const linesAdded = sourceFiles.reduce((sum, file) => sum + file.addedLines.size, 0);
		const pairs = duplicatePairs(change);
		const leads: ReviewLead[] = pairs.slice(0, MAX_PAIRS).map((pair) => ({
			at: { change: pair.a.path, line: pair.a.startLine },
			label: LABELS["same-shaped-blocks"],
			facts: factsOf({
				endLine: pair.a.endLine,
				otherPath: pair.b.path,
				otherStart: pair.b.startLine,
				otherEnd: pair.b.endLine,
				lines: pair.lines,
				identicalLines: pair.identicalLines,
				differing: pair.differing.join(", "),
			}),
		}));
		const directions =
			pairs.length === 0
				? [
						`No two runs of ${String(MIN_RUN_LINES)} or more added lines share a shape once names and literals are set aside. A copy this pairing cannot see — the same computation spelled with different statements, or shorter than ${String(MIN_RUN_LINES)} lines — is still yours to read for.`,
					]
				: [
						`${String(pairs.length)} pair(s) of added blocks with the same line shapes, one row each with both spans and the names that differ. These are structural candidates, not established copies of maintained knowledge. Assess them under the practice standard.`,
					];
		return ctx.review({
			leads,
			facts: { linesAdded, filesScanned: sourceFiles.length, pairs: pairs.length },
			directions,
		});
	},
});
