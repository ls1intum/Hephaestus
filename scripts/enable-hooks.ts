/**
 * Points Git at the Vite+ hook dispatcher in `.vite-hooks/`, which `package.json#scripts.prepare`
 * runs on every install. `vp hooks enable` keeps whatever `core.hooksPath` a clone already has, so
 * a checkout made before the dispatcher moved would keep the old path and run none of the project
 * hooks; clearing it first is what makes the install repair such a clone rather than pass over it.
 */
import { execFileSync, spawnSync } from "node:child_process";

import { CAPTURE_LIMIT_BYTES } from "./lib/process.ts";

const HOOKS_DIR = ".vite-hooks";

function git(...args: string[]): string {
	try {
		return execFileSync("git", args, {
			encoding: "utf8",
			stdio: ["ignore", "pipe", "ignore"],
			maxBuffer: CAPTURE_LIMIT_BYTES,
		}).trim();
	} catch {
		return "";
	}
}

// An image build or a source tarball has no work tree, and no hooks to enable.
if (git("rev-parse", "--is-inside-work-tree") === "true") {
	const current = git("config", "--local", "core.hooksPath");
	if (current !== "" && current !== `${HOOKS_DIR}/_`)
		git("config", "--local", "--unset", "core.hooksPath");
	const enabled = spawnSync("vp", ["hooks", "enable", "--hooks-dir", HOOKS_DIR], {
		stdio: "inherit",
	});
	process.exitCode = enabled.status ?? 1;
}
