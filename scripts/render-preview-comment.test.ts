import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdir, mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import { test } from "node:test";

import { environmentForGitFixture } from "./lib/git-environment.ts";

const script = resolve("scripts/render-preview-comment.ts");

function git(root: string, ...arguments_: string[]): string {
	return execFileSync("git", arguments_, {
		cwd: root,
		encoding: "utf8",
		env: environmentForGitFixture(),
	}).trim();
}

async function repository(changedFile: string): Promise<{ base: string; root: string }> {
	const root = await mkdtemp(resolve(tmpdir(), "preview-comment-"));
	git(root, "init", "--quiet");
	git(root, "config", "user.email", "test@example.invalid");
	git(root, "config", "user.name", "Test");
	await writeFile(resolve(root, "seed"), "seed");
	git(root, "add", ".");
	git(root, "commit", "--quiet", "-m", "seed");
	const base = git(root, "rev-parse", "HEAD");
	await mkdir(dirname(resolve(root, changedFile)), { recursive: true });
	await writeFile(resolve(root, changedFile), "changed");
	git(root, "add", ".");
	git(root, "commit", "--quiet", "-m", "change");
	return { base, root };
}

async function render(
	root: string,
	base: string,
	kind: string,
	directory: string,
): Promise<string> {
	execFileSync("node", [script, kind, directory, "https://preview.example/", base, "comment.md"], {
		cwd: root,
		stdio: "pipe",
		env: environmentForGitFixture({
			GITHUB_SERVER_URL: "https://github.com",
			GITHUB_REPOSITORY: "example/project",
			GITHUB_RUN_ID: "123",
			GITHUB_SHA: "not-the-checked-out-commit",
		}),
	});
	return readFile(resolve(root, "comment.md"), "utf8");
}

void test("links only stories from changed files to their canvases", async () => {
	const { base, root } = await repository("webapp/src/Button.stories.tsx");
	await mkdir(resolve(root, "build"));
	await writeFile(
		resolve(root, "build/index.json"),
		JSON.stringify({
			entries: {
				"button--primary": {
					importPath: "./src/Button.stories.tsx",
					name: "Primary [default]",
					title: "UI/Button",
					type: "story",
				},
				"button--unsafe": {
					importPath: "./src/Button.stories.tsx",
					name: "Unsafe\n<img>",
					title: "UI/Button",
					type: "story",
				},
				"button--docs": {
					importPath: "./src/Button.stories.tsx",
					name: "Docs",
					title: "UI/Button",
					type: "docs",
				},
				"other--unchanged": {
					importPath: "./src/Other.stories.tsx",
					name: "Unchanged",
					title: "Other",
					type: "story",
				},
			},
		}),
	);
	const comment = await render(root, base, "storybook", "build");
	assert.match(comment, /Stories in changed files/);
	assert.ok(comment.includes("UI/Button — Primary \\[default\\]"));
	assert.match(comment, /\?path=\/story\/button--primary/);
	assert.doesNotMatch(comment, /button--docs/);
	assert.doesNotMatch(comment, /other--unchanged/);
	assert.ok(comment.includes("Unsafe &lt;img&gt;"));
	assert.doesNotMatch(comment, /Unsafe\n/);
	const sha = git(root, "rev-parse", "HEAD");
	assert.ok(
		comment.includes(
			`Built from [\`${sha.slice(0, 7)}\`](<https://github.com/example/project/commit/${sha}>)`,
		),
	);
	assert.ok(
		comment.includes("[Build logs](<https://github.com/example/project/actions/runs/123>)"),
	);
	assert.doesNotMatch(comment, /not-the-checked-out-commit/);
});

void test("limits long story lists", async () => {
	const { base, root } = await repository("webapp/src/Button.stories.tsx");
	await mkdir(resolve(root, "build"));
	await writeFile(
		resolve(root, "build/index.json"),
		JSON.stringify({
			entries: Object.fromEntries(
				Array.from({ length: 28 }, (_, index) => [
					`button--${index}`,
					{
						importPath: "./src/Button.stories.tsx",
						name: `Example ${index}`,
						title: "Button",
						type: "story",
					},
				]),
			),
		}),
	);
	const comment = await render(root, base, "storybook", "build");
	assert.match(comment, /Stories in changed files \(25 of 28\)/);
	assert.match(comment, /3 more are available in the full preview/);
});

void test("does not claim files are unchanged when they have no published stories", async () => {
	const { base, root } = await repository("webapp/src/Button.stories.tsx");
	await mkdir(resolve(root, "build"));
	await writeFile(resolve(root, "build/index.json"), JSON.stringify({ entries: {} }));
	const comment = await render(root, base, "storybook", "build");
	assert.match(
		comment,
		/Stories in changed files\n\nNo published stories found in changed files\./,
	);
});

void test("renders safe changed Docusaurus routes once", async () => {
	const { base, root } = await repository("docs/user/getting-started.mdx");
	await mkdir(resolve(root, "metadata/nested"), { recursive: true });
	await writeFile(
		resolve(root, "metadata/nested/page.json"),
		JSON.stringify({
			permalink: "/user/start-(here)",
			source: "@site/user/getting-started.mdx",
			title: "Start [here]",
		}),
	);
	await writeFile(
		resolve(root, "metadata/nested/duplicate.json"),
		JSON.stringify({
			permalink: "/user/start-(here)",
			source: "@site/user/getting-started.mdx",
			title: "Start [here]",
		}),
	);
	await writeFile(
		resolve(root, "metadata/nested/unchanged.json"),
		JSON.stringify({
			permalink: "/user/other",
			source: "@site/user/other.mdx",
			title: "Unchanged",
		}),
	);
	await writeFile(
		resolve(root, "metadata/nested/external.json"),
		JSON.stringify({
			permalink: "https://attacker.example/phishing",
			source: "@site/user/getting-started.mdx",
			title: "External",
		}),
	);
	const comment = await render(root, base, "docs", "metadata");
	assert.match(comment, /Changed pages/);
	const link = "[Start \\[here\\]](<https://preview.example/user/start-(here)>)";
	assert.equal(comment.split(link).length - 1, 1);
	assert.doesNotMatch(comment, /attacker|External/);
	assert.doesNotMatch(comment, /Unchanged/);
});

void test("rejects an invalid Storybook index instead of publishing an empty result", async () => {
	const { base, root } = await repository("webapp/src/Button.stories.tsx");
	await mkdir(resolve(root, "build"));
	for (const index of [
		{},
		{ entries: [] },
		{ entries: null },
		{ entries: { "button--primary": { type: "story" } } },
	]) {
		await writeFile(resolve(root, "build/index.json"), JSON.stringify(index));
		await assert.rejects(
			render(root, base, "storybook", "build"),
			/Storybook (index\.entries|entry button--primary\.importPath) must be/,
		);
	}
});

void test("does not claim docs are unchanged when a changed page is unpublished", async () => {
	const { base, root } = await repository("docs/user/draft.mdx");
	await mkdir(resolve(root, "metadata"));
	const comment = await render(root, base, "docs", "metadata");
	assert.match(comment, /Changed pages\n\nNo published pages found in changed files\./);
});
