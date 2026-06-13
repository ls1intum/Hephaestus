// Precompute HINTS for avoids-unsafe-panics-and-chosen-crashes: locate deliberate-crash constructs ADDED in
// the diff. These are candidates to investigate — the LLM decides whether each is a real, unsafe crash.
import type { DiffFile, PullRequestMetadata, Hint } from "../lib/types";

const PATTERNS: Array<[string, RegExp]> = [
  ["try!", /\btry!\s/],
  ["fatalError", /\bfatalError\s*\(/],
  ["force-cast as!", /\bas!\s/],
  ["preconditionFailure", /\bpreconditionFailure\s*\(/],
  ["assertionFailure", /\bassertionFailure\s*\(/],
  ["force-unwrap", /[A-Za-z0-9_\)\]]\!(\.|\s|$|\))/],
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
      ? [`Found ${hints.length} deliberate-crash / force-unwrap construct(s) added in the diff — investigate whether each can be triggered by realistic input/state and should handle the failure instead.`]
      : [];
  return { hints: hints.slice(0, 40), metrics: { crashConstructsAdded: hints.length }, directions };
}
