import { readdirSync } from "node:fs";
import nodePath from "node:path";

import { makeRe } from "minimatch";

// Hidden paths are reviewed code; exclude .git and symlinks. fs.globSync cannot include hidden paths.
export function globFilesSync(pattern: string, cwd: string): string[] {
	const matcher = makeRe(pattern, { dot: true });
	if (matcher === false) {
		return [];
	}
	const files: string[] = [];
	const directories = [cwd];
	for (const directory of directories) {
		for (const entry of readdirSync(directory, { withFileTypes: true })) {
			const path = nodePath.join(directory, entry.name);
			if (entry.isDirectory()) {
				if (entry.name !== ".git") {
					directories.push(path);
				}
			} else if (entry.isFile()) {
				const name = nodePath.relative(cwd, path);
				if (matcher.test(name.split(nodePath.sep).join("/"))) {
					files.push(name);
				}
			}
		}
	}
	return files.toSorted();
}
