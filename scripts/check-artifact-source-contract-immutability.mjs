import { execFileSync } from "node:child_process";

const root = "server/src/main/resources/contracts/artifact-source";
const baseRef = process.env.GITHUB_BASE_REF ? `origin/${process.env.GITHUB_BASE_REF}` : "origin/main";
const base = execFileSync("git", ["merge-base", "HEAD", baseRef], { encoding: "utf8" }).trim();

let publishedVersions = [];
try {
	publishedVersions = execFileSync("git", ["ls-tree", "-d", "--name-only", `${base}:${root}`], {
		encoding: "utf8",
		stdio: ["ignore", "pipe", "ignore"],
	})
		.trim()
		.split("\n")
		.filter(Boolean);
} catch {
	// The first contract release has no published directory in the merge base.
}

for (const version of publishedVersions) {
	const versionPath = `${root}/${version}`;
	try {
		execFileSync("git", ["diff", "--quiet", base, "--", versionPath]);
	} catch {
		throw new Error(`Published artifact-source contract ${version} is immutable; add a new version.`);
	}
}
