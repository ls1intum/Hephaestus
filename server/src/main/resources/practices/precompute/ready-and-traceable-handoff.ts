// Precompute HINTS for ready-and-traceable-handoff: (1) traceability references to a motivating issue
// (closing OR non-closing — traceability does not require a closing keyword), and (2) the repo-wide
// test-absence negative (expensive for a model to prove). FACTS only — the LLM judges readiness.
import { findFiles } from "../lib/grep";
import type { DiffFile, PullRequestMetadata } from "../lib/types";
const isTest = (p: string) =>
	/(^|\/)(tests?|specs?|__tests__)(\/)|[._-](test|tests|spec|specs)\.[a-z]+$|Tests?\.[a-z0-9]+$|Spec\.[a-z0-9]+$/i.test(
		p,
	);

/** Bare `#N` mention (group 1 = number), rejecting `#1a2b` colours / `#1.2` versions / `#42px` units. */
const BARE_REF = /#(\d+)(?![\w.])/g;
/** Issue id at the start of a branch-slug segment, e.g. `1313-foo` or `feat/1313-foo`. */
const BRANCH_REF = /(?:^|\/)(\d{1,7})-/g;

export default async function (repoPath: string, _d: Map<string, DiffFile>, m: PullRequestMetadata) {
	const directions: string[] = [];

	// --- Traceability: does the handoff reference a motivating issue at all? ---
	const body = (m.body ?? "") + "\n" + (m.commits ?? []).map((c) => c.message ?? "").join("\n");
	const branch = m.source_branch ?? "";
	const bodyRefs = new Set<string>();
	for (const mt of body.matchAll(BARE_REF)) bodyRefs.add("#" + mt[1]);
	const branchRefs = new Set<string>();
	for (const mt of branch.matchAll(BRANCH_REF)) branchRefs.add("#" + mt[1]);
	const allRefs = new Set<string>([...bodyRefs, ...branchRefs]);
	if (allRefs.size > 0) {
		directions.push(
			`Traceability fact: a motivating-issue reference IS present — ${[...allRefs].join(", ")}` +
				(branchRefs.size ? ` (branch '${branch}' encodes ${[...branchRefs].join(", ")})` : "") +
				`. Traceability does NOT require a closing keyword: 'Refs #N', a bare '#N', or an issue-number branch prefix all establish the link, so do not read a closingRefCount of 0 as "untraceable".`,
		);
	} else {
		directions.push(
			`Traceability fact: no issue reference (#N, 'Refs #N', closing keyword, or issue-number branch prefix) was found in the body, commits, or branch '${branch}' — confirm in the body before concluding the handoff is untraceable.`,
		);
	}

	// --- Test-absence negative (worktree-reliability guarded) ---
	let repoTestFileCount = 0;
	let repoCodeFileCount = 0;
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
		const all = await findFiles(repoPath, ext);
		repoCodeFileCount += all.length;
		repoTestFileCount += all.filter(isTest).length;
	}
	const worktreeVisible = repoCodeFileCount > 0;
	if (worktreeVisible && repoTestFileCount === 0) {
		directions.push(
			"The repository contains NO test files anywhere (worktree was readable). If the PR's Definition-of-Done checklist ticks an item asserting tests pass / are added, that tick is a vacuous done-claim — there is nothing to verify it against.",
		);
	}

	return {
		hints: [],
		metrics: {
			traceabilityRefCount: allRefs.size,
			repoTestFileCount,
			worktreeVisible: worktreeVisible ? 1 : 0,
		},
		directions,
	};
}
