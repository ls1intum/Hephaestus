import { globSync } from "node:fs";
import { join, relative } from "node:path";

/** Return matching files in stable order. */
export function globFilesSync(pattern: string, cwd: string): string[] {
	return globSync(pattern, { cwd, withFileTypes: true })
		.filter((entry) => entry.isFile())
		.map((entry) => relative(cwd, join(entry.parentPath, entry.name)))
		.toSorted();
}
