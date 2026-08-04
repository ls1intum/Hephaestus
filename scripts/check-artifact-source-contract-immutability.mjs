import { execFileSync } from "node:child_process";

const root = "server/src/main/resources/contracts/artifact-source";
const baseRef = process.env.GITHUB_BASE_REF ? `origin/${process.env.GITHUB_BASE_REF}` : "origin/main";
const base = execFileSync("git", ["merge-base", "HEAD", baseRef], { encoding: "utf8" }).trim();

const rootExists =
	execFileSync("git", ["ls-tree", "-d", "--name-only", base, "--", root], {
		encoding: "utf8",
	})
		.trim() === root;
const publishedVersions = rootExists
	? execFileSync("git", ["ls-tree", "-d", "--name-only", `${base}:${root}`], {
			encoding: "utf8",
		})
			.trim()
			.split("\n")
			.filter(Boolean)
	: [];

for (const version of publishedVersions) {
	const versionPath = `${root}/${version}`;
	try {
		execFileSync("git", ["diff", "--quiet", base, "--", versionPath]);
	} catch {
		throw new Error(`Published artifact-source contract ${version} is immutable; add a new version.`);
	}
}
