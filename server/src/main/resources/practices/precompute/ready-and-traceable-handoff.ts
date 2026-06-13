// Precompute HINT for ready-and-traceable-handoff: the repo-wide test-absence negative (expensive for a model to prove).
import { findFiles } from "../lib/grep";
import type { DiffFile, PullRequestMetadata } from "../lib/types";
const isTest = (p: string) =>
  /(^|\/)(tests?|specs?|__tests__)(\/)|[._-](test|tests|spec|specs)\.[a-z]+$|Tests?\.[a-z0-9]+$|Spec\.[a-z0-9]+$/i.test(p);

export default async function (repoPath: string, _d: Map<string, DiffFile>, _m: PullRequestMetadata) {
  let repoTestFileCount = 0;
  for (const ext of ["swift", "ts", "tsx", "js", "jsx", "py", "java", "kt", "go", "rb", "cs"]) {
    repoTestFileCount += (await findFiles(repoPath, ext)).filter(isTest).length;
  }
  const directions: string[] = [];
  if (repoTestFileCount === 0) {
    directions.push(
      "The repository contains NO test files anywhere. If the PR's Definition-of-Done checklist ticks an item asserting tests pass / are added, that tick is a vacuous done-claim — there is nothing to verify it against. Check the DoD checklist in the PR body."
    );
  }
  return { hints: [], metrics: { repoTestFileCount }, directions };
}
