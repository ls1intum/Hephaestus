// Keeps the self-hosted install's pinned release version in step with the root
// package version. Run by the `changeset:version` script (see package.json) as
// part of the Version PR, so `IMAGE_TAG` in the self-host .env.example and the
// `VERSION=` example in the install guide always match the release being cut —
// no manual bump, no drift. CI (ci-compose-validate) fails if they diverge.
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
