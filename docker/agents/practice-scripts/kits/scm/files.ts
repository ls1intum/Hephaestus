import { readdirSync } from "node:fs";
import nodePath from "node:path";

import { makeRe } from "minimatch";

/**
 * Return matching regular files, including hidden paths, without following symlinks.
 * The checkout's `.git` folder holds the clone's configuration, not the work, so it is never walked.
 * `fs.globSync` would replace this walker, but it has no option to match hidden paths such as `.github/`.
 */
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
