export interface DiffFile {
	path: string;
	addedLines: Map<number, string>;
	removedLines: Map<number, string>;
	hunks: DiffHunk[];
}

export interface DiffHunk {
	oldStart: number;
	oldCount: number;
	newStart: number;
	newCount: number;
	lines: string[];
}
