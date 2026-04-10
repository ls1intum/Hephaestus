import type { DiffFile, DiffHunk } from "./types";

/**
 * Parse a unified diff (with optional [L<n>] annotations) into structured DiffFile objects.
 * Returns a Map of file path -> DiffFile.
 */
export function parseDiff(diffContent: string): Map<string, DiffFile> {
  const files = new Map<string, DiffFile>();

  // Split on "diff --git" boundaries
  const fileDiffs = diffContent.split(/^diff --git /m).filter(Boolean);

  for (const fileDiff of fileDiffs) {
    const lines = fileDiff.split("\n");

    // Extract file path from "a/path b/path"
    const headerMatch = lines[0]?.match(/a\/(.+?)\s+b\/(.+)/);
    if (!headerMatch) continue;
    const filePath = headerMatch[2];

    const addedLines = new Map<number, string>();
    const removedLines = new Map<number, string>();
    const hunks: DiffHunk[] = [];

    let currentHunk: DiffHunk | null = null;
    let newLineNum = 0;
    let oldLineNum = 0;

    for (const line of lines) {
      // Hunk header: @@ -oldStart,oldCount +newStart,newCount @@
      const hunkMatch = line.match(/^@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@/);
      if (hunkMatch) {
        currentHunk = {
          oldStart: parseInt(hunkMatch[1]),
          oldCount: parseInt(hunkMatch[2] ?? "1"),
          newStart: parseInt(hunkMatch[3]),
          newCount: parseInt(hunkMatch[4] ?? "1"),
          lines: [],
        };
        hunks.push(currentHunk);
        oldLineNum = currentHunk.oldStart;
        newLineNum = currentHunk.newStart;
        continue;
      }

      if (!currentHunk) continue;

      // Strip [L<n>] annotation if present
      const stripped = line.replace(/^\[L\d+\]\s*/, "");

      if (stripped.startsWith("+") && !stripped.startsWith("+++")) {
        addedLines.set(newLineNum, stripped.slice(1));
        currentHunk.lines.push(stripped);
        newLineNum++;
      } else if (stripped.startsWith("-") && !stripped.startsWith("---")) {
        removedLines.set(oldLineNum, stripped.slice(1));
        currentHunk.lines.push(stripped);
        oldLineNum++;
      } else if (!stripped.startsWith("\\")) {
        // Context line
        currentHunk.lines.push(stripped);
        newLineNum++;
        oldLineNum++;
      }
    }

    // Normalize path: strip leading ./
    const normalizedPath = filePath.replace(/^\.\//, "");
    files.set(normalizedPath, { path: normalizedPath, addedLines, removedLines, hunks });
  }

  return files;
}

/** Check if a given file path + line number is in the diff (on a + line) */
export function isInDiff(diffFiles: Map<string, DiffFile>, filePath: string, lineNum: number): boolean {
  // Try exact match first, then suffix match
  const df = diffFiles.get(filePath) ?? [...diffFiles.values()].find(f => filePath.endsWith(f.path) || f.path.endsWith(filePath));
  if (!df) return false;
  return df.addedLines.has(lineNum);
}
