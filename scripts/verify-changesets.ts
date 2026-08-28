import { basename } from "node:path";

import { asArray, asRecord, asString, readJsonFile } from "./lib/json.ts";

export const verifyChangesets = (
	status: unknown,
	files: readonly string[],
	preOne = true,
): void => {
	const entries = asArray(
		asRecord(status, "changeset status").changesets,
		"changeset status.changesets",
	);
	const byId = new Map(
		entries.map((value, index) => {
			const entry = asRecord(value, `changeset status.changesets[${index}]`);
			return [asString(entry.id, `changeset status.changesets[${index}].id`), entry];
		}),
	);

	for (const file of files) {
		const entry = byId.get(basename(file, ".md"));
		if (!entry) throw new Error(`${file}: Changesets did not parse this file`);

		const summary = asString(entry.summary, `${file} summary`).trim();
		const releases = asArray(entry.releases, `${file} releases`);
		if (summary === "") {
			throw new Error(
				releases.length === 0
					? `${file}: an empty changeset must explain why no release note is needed`
					: `${file}: a release changeset needs a user- or operator-facing summary`,
			);
		}
		if (/^(?:Co-authored-by|Claude-Session):/im.test(summary)) {
			throw new Error(
				`${file}: release notes must not contain Co-authored-by or Claude-Session metadata`,
			);
		}
		if (releases.length === 0) continue;
		if (releases.length !== 1)
			throw new Error(`${file}: must release only the root hephaestus package`);

		const release = asRecord(releases[0], `${file} release`);
		const name = asString(release.name, `${file} release name`);
		const bump = asString(release.type, `${file} release type`);
		if (name !== "hephaestus")
			throw new Error(`${file}: must release only the root hephaestus package`);
		if (preOne && bump === "major") {
			throw new Error(
				`${file}: pre-1.0 major changesets are reserved for the deliberate 1.0 release; use minor`,
			);
		}
	}
};

if (import.meta.main) {
	try {
		const [statusJson, ...files] = process.argv.slice(2);
		if (!statusJson)
			throw new Error("usage: verify-changesets.ts <changeset-status-json> <files...>");
		const status = await readJsonFile(statusJson);
		const root = asRecord(await readJsonFile("package.json"), "package.json");
		verifyChangesets(
			status,
			files,
			asString(root.version, "package.json version").startsWith("0."),
		);
	} catch (error) {
		console.error(`::error::${error instanceof Error ? error.message : String(error)}`);
		process.exitCode = 1;
	}
}
