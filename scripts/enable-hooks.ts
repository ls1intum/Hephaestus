/**
 * Points Git at the Vite+ hook dispatcher in `.vite-hooks/`, which `package.json#scripts.prepare`
 * runs on every install. `vp hooks enable` keeps whatever `core.hooksPath` a clone already has, so
 * a checkout made before the dispatcher moved would keep the old path and run none of the project
 * hooks; clearing it first is what makes the install repair such a clone rather than pass over it.
 */
import { execFileSync, spawnSync } from "node:child_process";

import { CAPTURE_LIMIT_BYTES } from "./lib/process.ts";

const HOOKS_DIR = ".vite-hooks";

/**
 * The repository refuses a push whose commits are unsigned, and an install is the last moment a
 * contributor is in front of the machine that has to sign. It is a warning rather than a failure:
 * a clone with no signing key still has to be able to build.
 */
const SIGNING_WARNING = `
warning: commits pushed to a branch in this repository must be signed, and this checkout is not
configured to sign. Sign with the SSH key you already push with:

  git config --global gpg.format ssh
  git config --global user.signingkey ~/.ssh/id_ed25519.pub
  git config --global commit.gpgsign true

Then register that public key on GitHub as a signing key — Settings > SSH and GPG keys > New SSH
key, key type "Signing Key", or: gh ssh-key add ~/.ssh/id_ed25519.pub --type signing

CONTRIBUTING.md, under "Signed Commits", has the rest.
`;

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

	// `--type=bool` so `1`, `yes` and `on` count as configured; every `gpg.format` signs.
	const signs =
		git("config", "--get", "--type=bool", "commit.gpgsign") === "true" &&
		git("config", "--get", "user.signingkey") !== "";
	if (!signs) process.stderr.write(SIGNING_WARNING);
}
