#!/usr/bin/env node
/**
 * Parses every committed `.mmd` with the mermaid the docs site actually resolves.
 *
 * Docusaurus renders mermaid in the browser, so a diagram it cannot parse builds green and publishes
 * as an error box on the page. That is how the generated ER schema shipped unreadable: the resolved
 * mermaid rejected `NUMERIC(12,6)`, and nothing in the toolchain had an opinion about it.
 *
 * Two regressions are in scope, which is why this reads the resolved package rather than pinning a
 * version here: a column type the generator emits that mermaid cannot read, and a transitive bump
 * that takes the parser backwards.
 */
import { readdir, readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { join } from "node:path";
import { JSDOM } from "jsdom";

const ROOTS = ["docs"];

const dom = new JSDOM("<!DOCTYPE html><body></body>", { pretendToBeVisual: true });
globalThis.window = dom.window;
globalThis.document = dom.window.document;

const require = createRequire(import.meta.url);
const { version } = require("mermaid/package.json");
const { default: mermaid } = await import("mermaid");

const diagrams = [];
for (const root of ROOTS) {
	for (const entry of await readdir(root, { recursive: true })) {
		if (entry.endsWith(".mmd")) diagrams.push(join(root, entry));
	}
}

if (diagrams.length === 0) {
	console.error("No .mmd files found — this check would pass without checking anything.");
	process.exit(1);
}

let failed = false;
for (const path of diagrams) {
	try {
		await mermaid.parse(await readFile(path, "utf8"));
	} catch (error) {
		failed = true;
		const detail = String(error?.message ?? error).split("\n")[0];
		console.error(`${path}\n  ${detail}\n`);
	}
}

if (failed) {
	console.error(`Diagrams above fail to parse under mermaid ${version}, so they publish as an`);
	console.error("error box rather than a diagram. The docs build cannot see this — mermaid runs");
	console.error("in the browser.");
	process.exit(1);
}

console.log(`check-mermaid-diagrams: ${diagrams.length} diagram(s) parse under mermaid ${version}.`);
