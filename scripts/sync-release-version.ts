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

// A version can only gain a history section by being released, and `changeset version` computes
// the lowest unreleased version — so any section for this version or a later one is unreleased
// content that ships now. Sections directly below the pending anchor whose headings say otherwise
// (hand-written before fragments existed, or this script's own output when a regeneration re-reads
// it) are absorbed into the section being stamped instead of failing the release.
const semverAtLeast = (candidate: string, reference: string): boolean => {
	const left = candidate.split(".").map(Number);
	const right = reference.split(".").map(Number);
	for (let index = 0; index < 3; index += 1) {
		if ((left[index] ?? 0) !== (right[index] ?? 0)) return (left[index] ?? 0) > (right[index] ?? 0);
	}
	return true;
};
const regionStart = pendingSections[0]?.index ?? 0;
let regionLength = pendingSections[0]?.[0]?.length ?? 0;
const unreleased: string[] = [];
for (;;) {
	const tail = migration.slice(regionStart + regionLength);
	const next = /^### v(\d+\.\d+\.\d+)\n([\s\S]*?)(?=^### |(?![\s\S]))/m.exec(tail);
	if (!next || next.index !== 0 || !semverAtLeast(next[1] ?? "", version)) break;
	unreleased.push(next[2]?.trim() ?? "");
	regionLength += next[0].length;
}

if (pending !== "" || unreleased.length > 0 || fragmentFiles.length > 0) {
	const remainder = migration.slice(0, regionStart) + migration.slice(regionStart + regionLength);
	if (remainder.split("\n").includes(`### v${version}`)) {
		throw new Error(`sync-release-version: MIGRATION.md already contains ### v${version}`);
	}
	const fragments = fragmentFiles.map((file) =>
		readFileSync(join(fragmentDirectory, file), "utf8").trim(),
	);
	const section = ["### Next release", `### v${version}`, pending, ...unreleased, ...fragments]
		.filter(Boolean)
		.join("\n\n");
	// Splicing by offset keeps `$&`/`$$` in migration notes literal.
	writeFileSync(
		migrationFile,
		`${migration.slice(0, regionStart)}${section}\n\n${migration.slice(regionStart + regionLength)}`,
	);
	for (const file of fragmentFiles) rmSync(join(fragmentDirectory, file));
}

console.log(`Synced release version references to ${version}`);
