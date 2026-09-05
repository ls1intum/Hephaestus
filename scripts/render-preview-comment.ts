import { execFileSync } from "node:child_process";
import { readdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import { asRecord, asString, isRecord, readJsonFile } from "./lib/json.ts";
import { CAPTURE_LIMIT_BYTES } from "./lib/process.ts";

function argument(index: number): string {
	const value = process.argv[index];
	if (!value) {
		throw new Error(
			"Usage: render-preview-comment <docs|storybook> <artifact-directory> <preview-url> <base-sha> <output>",
		);
	}
	return value;
}

const kind = argument(2);
const artifactDirectory = argument(3);
const previewUrl = argument(4);
const baseSha = argument(5);
const output = argument(6);
const MAX_LINKS = 25;

const changedFiles = new Set(
	execFileSync("git", ["diff", "--name-only", "--diff-filter=ACMRT", "-z", `${baseSha}...HEAD`], {
		encoding: "utf8",
		maxBuffer: CAPTURE_LIMIT_BYTES,
	})
		.split("\0")
		.filter(Boolean),
);

const baseUrl = new URL(previewUrl);

function markdown(value: string): string {
	return value
		.replaceAll("\r", " ")
		.replaceAll("\n", " ")
		.replaceAll("\\", "\\\\")
		.replaceAll("[", "\\[")
		.replaceAll("]", "\\]")
		.replaceAll("<", "&lt;")
		.replaceAll(">", "&gt;");
}

async function renderDocs(): Promise<string> {
	const links = new Map<string, string>();
	for (const file of await readdir(artifactDirectory, { recursive: true })) {
		if (!file.endsWith(".json")) continue;
		const metadata = await readJsonFile(resolve(artifactDirectory, file));
		if (!isRecord(metadata)) continue;
		const { source: sourcePath, permalink, title } = metadata;
		if (
			typeof sourcePath !== "string" ||
			typeof permalink !== "string" ||
			typeof title !== "string"
		)
			continue;
		const source = `docs/${sourcePath.replace(/^@site\//, "")}`;
		if (changedFiles.has(source)) {
			const url = new URL(permalink, baseUrl);
			const existing = links.get(url.href);
			if (url.origin === baseUrl.origin && (existing === undefined || title < existing)) {
				links.set(url.href, title);
			}
		}
	}
	return comment(
		"📚 Documentation preview",
		"Open full documentation preview",
		[...links].map(([url, title]) => ({ title, url })).toSorted(compareLinks),
		"Changed pages",
		"No published pages found in changed files.",
	);
}

async function renderStorybook(): Promise<string> {
	const index = asRecord(
		await readJsonFile(resolve(artifactDirectory, "index.json")),
		"Storybook index",
	);
	const links = Object.entries(asRecord(index.entries, "Storybook index.entries"))
		.flatMap(([id, value]) => {
			const entry = asRecord(value, `Storybook entry ${id}`);
			if (entry.type !== "story") return [];
			const importPath = asString(entry.importPath, `Storybook entry ${id}.importPath`);
			const name = asString(entry.name, `Storybook entry ${id}.name`);
			const title = asString(entry.title, `Storybook entry ${id}.title`);
			if (!changedFiles.has(`webapp/${importPath.replace(/^\.\//, "")}`)) return [];
			return [
				{
					title: `${title} — ${name}`,
					url: new URL(`?path=/story/${encodeURIComponent(id)}`, baseUrl).href,
				},
			];
		})
		.toSorted(compareLinks);
	return comment(
		"🧩 Storybook preview",
		"Open full Storybook preview",
		links,
		"Stories in changed files",
		"No published stories found in changed files.",
	);
}

function compareLinks(left: { title: string; url: string }, right: { title: string; url: string }) {
	return left.title.localeCompare(right.title) || left.url.localeCompare(right.url);
}

function comment(
	heading: string,
	previewLabel: string,
	links: readonly { title: string; url: string }[],
	linksHeading: string,
	emptyMessage: string,
): string {
	const sections = [`## ${heading}`, `[${previewLabel}](<${baseUrl.href}>)`];
	if (links.length === 0) {
		sections.push(`### ${linksHeading}\n\n${emptyMessage}`);
	} else {
		const count = links.length > MAX_LINKS ? ` (${MAX_LINKS} of ${links.length})` : "";
		const list = links
			.slice(0, MAX_LINKS)
			.map(({ title, url }) => `- [${markdown(title)}](<${url}>)`);
		if (links.length > MAX_LINKS) {
			list.push("", `${links.length - MAX_LINKS} more are available in the full preview.`);
		}
		sections.push(`### ${linksHeading}${count}\n\n${list.join("\n")}`);
	}
	const { GITHUB_SERVER_URL, GITHUB_REPOSITORY, GITHUB_RUN_ID } = process.env;
	if (GITHUB_SERVER_URL && GITHUB_REPOSITORY && GITHUB_RUN_ID) {
		const sha = execFileSync("git", ["rev-parse", "HEAD"], {
			encoding: "utf8",
			maxBuffer: CAPTURE_LIMIT_BYTES,
		}).trim();
		const repositoryUrl = `${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}`;
		sections.push(
			`Built from [\`${sha.slice(0, 7)}\`](<${repositoryUrl}/commit/${sha}>) · [Build logs](<${repositoryUrl}/actions/runs/${GITHUB_RUN_ID}>). Updates after successful preview builds.`,
		);
	}
	return `${sections.join("\n\n")}\n`;
}

const rendered =
	kind === "docs" ? await renderDocs() : kind === "storybook" ? await renderStorybook() : undefined;
if (!rendered) throw new Error(`Unknown preview kind: ${kind}`);
await writeFile(output, rendered);
