#!/usr/bin/env node
/**
 * Parses every committed diagram with the mermaid the docs site actually resolves — both the standalone
 * `.mmd` files and the ```mermaid blocks fenced inside `.md` and `.mdx`.
 *
 * Docusaurus renders mermaid in the browser, so a diagram it cannot parse builds green and publishes
 * as an error box on the page. That is how the generated ER schema shipped unreadable: the resolved
 * mermaid rejected `NUMERIC(12,6)`, and nothing in the toolchain had an opinion about it.
 *
 * The fenced blocks are the ones a person edits by hand, and for a long time this gate did not read
 * them: it reported "1 diagram(s) parse" while ten hand-written diagrams went unchecked, so a
 * hand-edited flowchart could publish as an error box with every gate green. Whatever narrows the scope
 * of this file again, it must not be that distinction.
 *
 * Two regressions are in scope, which is why this reads the resolved package rather than pinning a
 * version here: a column type the generator emits that mermaid cannot read, and a transitive bump
 * that takes the parser backwards.
 */
import { readdir, readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { JSDOM } from "jsdom";

/** Resolved from this file, so the gate answers the same whatever the working directory is. */
const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const ROOTS = [join(REPO_ROOT, "docs")];

const dom = new JSDOM("<!DOCTYPE html><body></body>", { pretendToBeVisual: true });
globalThis.window = dom.window;
globalThis.document = dom.window.document;

const require = createRequire(import.meta.url);
const { version } = require("mermaid/package.json");
const { default: mermaid } = await import("mermaid");

/** ```mermaid … ``` inside a Markdown or MDX page. Non-greedy, so two blocks on one page stay two. */
const FENCED = /```mermaid[^\n]*\n([\s\S]*?)```/g;

/** Each entry is `{ label, source }`, so a failure names the page and which block on it. */
const diagrams = [];
for (const root of ROOTS) {
	for (const entry of await readdir(root, { recursive: true })) {
		const path = join(root, entry);
		if (entry.endsWith(".mmd")) {
			diagrams.push({ label: path, source: await readFile(path, "utf8") });
			continue;
		}
		if (!entry.endsWith(".md") && !entry.endsWith(".mdx")) continue;
		const text = await readFile(path, "utf8");
		let block = 0;
		for (const match of text.matchAll(FENCED)) {
			block += 1;
			diagrams.push({ label: `${path} (mermaid block ${block})`, source: match[1] });
		}
	}
}

if (diagrams.length === 0) {
	console.error("No diagrams found — this check would pass without checking anything.");
	process.exit(1);
}

let failed = false;
for (const { label, source } of diagrams) {
	try {
		await mermaid.parse(source);
	} catch (error) {
		failed = true;
		const detail = String(error?.message ?? error).split("\n")[0];
		console.error(`${label}\n  ${detail}\n`);
	}
}

if (failed) {
	console.error(`Diagrams above fail to parse under mermaid ${version}, so they publish as an`);
	console.error("error box rather than a diagram. The docs build cannot see this — mermaid runs");
	console.error("in the browser.");
	process.exit(1);
}

console.log(`check-mermaid-diagrams: ${diagrams.length} diagram(s) parse under mermaid ${version}.`);
