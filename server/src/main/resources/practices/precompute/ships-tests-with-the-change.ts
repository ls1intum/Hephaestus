// Precompute HINTS for ships-tests-with-the-change. Surfaces facts only — the LLM judges.
import { findFiles } from "../lib/grep";
import type { DiffFile, PullRequestMetadata } from "../lib/types";

const TEST =
	/(^|\/)(tests?|specs?|__tests__)(\/)|[._-](test|tests|spec|specs)\.[a-z]+$|Tests?\.[a-z0-9]+$|Spec\.[a-z0-9]+$/i;
const CODE = /\.(swift|ts|tsx|js|jsx|py|java|kt|go|rb|cs|cpp|cc|cxx|c|m|mm|h|hpp)$/i;
const isTest = (p: string) => TEST.test(p);

export default async function (repoPath: string, diffFiles: Map<string, DiffFile>, _m: PullRequestMetadata) {
	let repoTestFileCount = 0;
	for (const ext of [
		"swift",
		"ts",
		"tsx",
		"js",
		"jsx",
		"py",
		"java",
		"kt",
		"go",
		"rb",
		"cs",
		"cpp",
		"cc",
		"cxx",
		"c",
		"m",
		"mm",
		"h",
		"hpp",
	]) {
		repoTestFileCount += (await findFiles(repoPath, ext)).filter(isTest).length;
	}
	const changed = [...diffFiles.keys()];
	const diffTestFiles = changed.filter(isTest).length;
	const diffProdFiles = changed.filter((f) => !isTest(f) && CODE.test(f)).length;

	const directions: string[] = [];
	if (repoTestFileCount === 0) {
		directions.push(
			"No test files were found anywhere in the repository — there is no test target, so whether THIS change ships a covering test cannot be assessed here, and a 'tests pass' claim cannot be verified.",
		);
	} else if (diffProdFiles > 0 && diffTestFiles === 0) {
		directions.push(
			`The change touches ${diffProdFiles} production code file(s) and 0 test file(s) — investigate whether the added/changed behaviour has a covering test (the repo does have ${repoTestFileCount} test files, so adding one was possible).`,
		);
	} else if (diffTestFiles > 0) {
		directions.push(
			`The change adds/edits ${diffTestFiles} test file(s) alongside ${diffProdFiles} production file(s).`,
		);
	}
	return { hints: [], metrics: { repoTestFileCount, diffProductionFiles: diffProdFiles, diffTestFiles }, directions };
}
