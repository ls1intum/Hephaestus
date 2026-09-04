import { execFileSync } from "node:child_process";
import { readdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

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

function record(value: unknown): object | undefined {
	return typeof value === "object" && value !== null && !Array.isArray(value) ? value : undefined;
}

function field(value: object | undefined, key: string): unknown {
	return value === undefined ? undefined : Reflect.get(value, key);
}

function entries(value: object | undefined): [string, unknown][] {
	return value === undefined ? [] : Object.keys(value).map((key) => [key, Reflect.get(value, key)]);
}

async function renderDocs(): Promise<string> {
	const links = new Map<string, string>();
	for (const file of await readdir(artifactDirectory, { recursive: true })) {
		if (!file.endsWith(".json")) continue;
		const metadata = record(JSON.parse(await readFile(resolve(artifactDirectory, file), "utf8")));
		const sourcePath = field(metadata, "source");
		const permalink = field(metadata, "permalink");
		const title = field(metadata, "title");
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
		"No documentation pages changed.",
	);
}

async function renderStorybook(): Promise<string> {
	const index = record(
		JSON.parse(await readFile(resolve(artifactDirectory, "index.json"), "utf8")),
	);
	const links = entries(record(field(index, "entries")))
		.flatMap(([id, value]) => {
			const entry = record(value);
			const type = field(entry, "type");
			const importPath = field(entry, "importPath");
			const name = field(entry, "name");
			const title = field(entry, "title");
			if (
				type !== "story" ||
				typeof importPath !== "string" ||
				typeof name !== "string" ||
				typeof title !== "string"
			)
				return [];
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
		"No story files changed.",
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
	return `${sections.join("\n\n")}\n`;
}

const rendered =
	kind === "docs" ? await renderDocs() : kind === "storybook" ? await renderStorybook() : undefined;
if (!rendered) throw new Error(`Unknown preview kind: ${kind}`);
await writeFile(output, rendered);
