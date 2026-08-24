// Rewrites the release version wherever an operator copies it out of the docs — `IMAGE_TAG` in the
// self-host `.env.example`, `VERSION=` in the install guide — from the root package version. Run by
// `changeset:version`, so the Version PR carries them; `ci-compose-validate` fails if they diverge.
//
// A pattern that matches nothing throws rather than writing: a silent no-op here ships an install
// guide pinned to the previous release.
import { readFileSync, writeFileSync } from "node:fs";
import { asRecord, asString, parseJson } from "./lib/json.ts";

const version = asString(
	asRecord(parseJson(readFileSync("package.json", "utf8")), "package.json").version,
	"package.json version",
);

const edits = [
	{
		file: "docker/self-host/.env.example",
		re: /^IMAGE_TAG=.*$/m,
		line: `IMAGE_TAG=${version}`,
	},
	{
		file: "docs/admin/install.mdx",
		re: /^VERSION=\S+(\s+# the release you are installing.*)$/m,
		line: `VERSION=${version}$1`,
	},
];

/** The file may not exist in every checkout; skip quietly when it does not. */
const readIfPresent = (file: string): string | undefined => {
	try {
		return readFileSync(file, "utf8");
	} catch {
		return undefined;
	}
};

for (const { file, re, line } of edits) {
	const text = readIfPresent(file);
	if (text === undefined) continue;
	if (!re.test(text)) {
		throw new Error(`sync-selfhost-version: no version literal matched in ${file}`);
	}
	writeFileSync(file, text.replace(re, line));
}

console.log(`Synced self-host version literals to ${version}`);
