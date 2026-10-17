import { scm, expectKind, requireChange } from "../kits/scm/index.ts";
import { isTestPath, languageOf } from "../kits/scm/languages.ts";
import { DIRECTION_CHARS, listWithin } from "../lib/core/bounded-text.ts";
import { definePracticeScript } from "../lib/core/practice-script.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		const source = [...requireChange(domain.files).keys()].filter(
			(path) => languageOf(path) !== null,
		);
		const tests = source.filter((path) => isTestPath(path));
		const production = source.filter((path) => !isTestPath(path));

		const directions: string[] = [];
		if (source.length > 0) {
			const counts = `The diff changes ${String(production.length)} production source file(s) and ${String(tests.length)} test file(s), by path`;
			directions.push(
				tests.length > 0
					? `${counts}: ${listWithin(tests, DIRECTION_CHARS - counts.length - 3)}.`
					: `${counts}.`,
				"Whether the repository already has a test target does not decide this practice: judge the changed behaviour against the tests in this diff.",
			);
		}
		return ctx.review({
			leads: [],
			facts: {
				diffProductionFiles: production.length,
				diffTestFiles: tests.length,
			},
			directions,
		});
	},
});
