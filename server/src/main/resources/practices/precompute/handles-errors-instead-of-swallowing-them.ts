// Precompute HINTS for handles-errors-instead-of-swallowing-them: locate ADDED lines that discard/silence
// errors. Candidates only — the LLM decides whether the error is genuinely being swallowed.
import type { DiffFile, PullRequestMetadata, Hint } from "../lib/types";

const PATTERNS: Array<[string, RegExp]> = [
  ["try? (discards error)", /\btry\?\s/],
  ["empty catch", /\bcatch\s*\{\s*\}/],
  ["catch with only print", /\bcatch\s*\{[^}]*\bprint\s*\([^}]*\}/],
  ["empty completion error ignored", /\bif\s+let\s+error\b[^{]*\{\s*\}/],
];

export default async function (_repo: string, diffFiles: Map<string, DiffFile>, _m: PullRequestMetadata) {
  const hints: Hint[] = [];
  for (const [path, df] of diffFiles) {
    if (!/\.(swift|m|mm)$/i.test(path)) continue;
    for (const [line, content] of df.addedLines) {
      if (content.trimStart().startsWith("//")) continue;
      for (const [name, re] of PATTERNS) {
        if (re.test(content)) {
          hints.push({ file: path, line, pattern: name, context: content.trim().slice(0, 160), inDiff: true, flags: {} });
          break;
        }
      }
    }
  }
  const directions =
    hints.length > 0
      ? [`Found ${hints.length} construct(s) added in the diff that may discard or silence an error — investigate whether the failure is genuinely swallowed (no surfacing, logging, or recovery).`]
      : [];
  return { hints: hints.slice(0, 40), metrics: { errorSwallowCandidates: hints.length }, directions };
}
