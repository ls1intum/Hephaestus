import { existsSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";

import { asRecord, asString, parseJson } from "./lib/json.ts";

const version = asString(
	asRecord(parseJson(readFileSync("package.json", "utf8")), "package.json").version,
	"package.json version",
);

const edits = [
	{
		file: "docs/admin/install.mdx",
		re: /^VERSION=\S+(\s+# the release you are installing.*)$/m,
		line: `VERSION=${version}$1`,
	},
	{
		file: "README.md",
		re: /^\s*VERSION=\S+(\s+# the release you are installing.*)$/m,
		line: `  VERSION=${version}$1`,
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
const pendingSections = [
	...migration.matchAll(/^### Next release\n([\s\S]*?)(?=^### |(?![\s\S]))/gm),
];
if (pendingSections.length !== 1) {
	throw new Error("sync-release-version: MIGRATION.md must contain exactly one ### Next release");
}
const pending = pendingSections[0]?.[1]?.trim() ?? "";
const fragmentDirectory = ".migration";
const fragmentFiles = existsSync(fragmentDirectory)
	? readdirSync(fragmentDirectory)
			.filter((file) => file.endsWith(".md") && file !== "README.md")
			.toSorted()
	: [];
if (pending !== "" || fragmentFiles.length > 0) {
	if (migration.split("\n").includes(`### v${version}`)) {
		throw new Error(`sync-release-version: MIGRATION.md already contains ### v${version}`);
	}
	const fragments = fragmentFiles.map((file) =>
		readFileSync(join(fragmentDirectory, file), "utf8").trim(),
	);
	const section = ["### Next release", `### v${version}`, pending, ...fragments].filter(Boolean);
	writeFileSync(
		migrationFile,
		migration.replace(pendingSections[0]?.[0] ?? "", `${section.join("\n\n")}\n\n`),
	);
	for (const file of fragmentFiles) rmSync(join(fragmentDirectory, file));
}

console.log(`Synced release version references to ${version}`);
