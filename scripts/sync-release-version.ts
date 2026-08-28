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

for (const { file, re, line } of edits) {
	const text = readFileSync(file, "utf8");
	if (!re.test(text)) {
		throw new Error(`sync-release-version: no version reference matched in ${file}`);
	}
	writeFileSync(file, text.replace(re, line));
}

const migrationFile = "MIGRATION.md";
const migration = readFileSync(migrationFile, "utf8");
const pendingHeading = /^### Next release$/m;
if (pendingHeading.test(migration)) {
	writeFileSync(migrationFile, migration.replace(pendingHeading, `### v${version}`));
}

console.log(`Synced release version references to ${version}`);
