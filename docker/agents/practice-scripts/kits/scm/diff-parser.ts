import type { DiffFile, DiffHunk } from "./types.ts";

// Hunk header: @@ -oldStart,oldCount +newStart,newCount @@
// Both counts are omitted from the header when they are 1, so they default; both start line
// numbers are required for the header to be a hunk header at all.
const HUNK_HEADER =
	/^@@\s+-(?<oldStart>\d+)(?:,(?<oldCount>\d+))?\s+\+(?<newStart>\d+)(?:,(?<newCount>\d+))?\s+@@/u;

export function parseDiff(diffContent: string): Map<string, DiffFile> {
	const files = new Map<string, DiffFile>();

	const fileDiffs = diffContent.split(/^diff --git /mu).filter(Boolean);

	for (const fileDiff of fileDiffs) {
		const lines = fileDiff.split("\n");

		const filePath = /a\/.+?\s+b\/(?<path>.+)/u.exec(lines[0] ?? "")?.groups?.path;
		if (filePath === undefined) {
			continue;
		}

		const addedLines = new Map<number, string>();
		const removedLines = new Map<number, string>();
		const hunks: DiffHunk[] = [];

		let currentHunk: DiffHunk | null = null;
		let newLineNum = 0;
		let oldLineNum = 0;
		let oldLeft = 0;
		let newLeft = 0;

		for (const annotated of lines) {
			const line = annotated.replace(/^\[L\d+\] /u, "");
			if (currentHunk === null || (oldLeft === 0 && newLeft === 0)) {
				const {
					oldStart,
					oldCount = "1",
					newStart,
					newCount = "1",
				} = HUNK_HEADER.exec(line)?.groups ?? {};
				if (oldStart !== undefined && newStart !== undefined) {
					currentHunk = {
						oldStart: Number.parseInt(oldStart, 10),
						oldCount: Number.parseInt(oldCount, 10),
						newStart: Number.parseInt(newStart, 10),
						newCount: Number.parseInt(newCount, 10),
						lines: [],
					};
					hunks.push(currentHunk);
					oldLineNum = currentHunk.oldStart;
					newLineNum = currentHunk.newStart;
					oldLeft = currentHunk.oldCount;
					newLeft = currentHunk.newCount;
				}
				continue;
			}
			if (line.startsWith("+")) {
				addedLines.set(newLineNum, line.slice(1));
				currentHunk.lines.push(line);
				newLineNum += 1;
				newLeft -= 1;
			} else if (line.startsWith("-")) {
				removedLines.set(oldLineNum, line.slice(1));
				currentHunk.lines.push(line);
				oldLineNum += 1;
				oldLeft -= 1;
			} else if (!line.startsWith("\\")) {
				currentHunk.lines.push(line);
				newLineNum += 1;
				oldLineNum += 1;
				newLeft -= 1;
				oldLeft -= 1;
			}
		}

		const normalizedPath = filePath.replace(/^\.\//u, "");
		files.set(normalizedPath, { path: normalizedPath, addedLines, removedLines, hunks });
	}

	return files;
}
