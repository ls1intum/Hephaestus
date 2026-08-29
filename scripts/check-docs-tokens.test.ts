import { describe, expect, test } from "bun:test";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { findDrift, readTokens } from "./check-docs-tokens";

/**
 * The mutation cases run against the real stylesheets, because the shapes that defeat a CSS parser
 * — a `:root` reopened three times, `@theme` blocks, a dark selector that is not `.dark` — are
 * properties of those files, and a synthetic fixture is exactly where they would go missing.
 */
const repositoryRoot = resolve(import.meta.dirname, "..");
const [docsCss, appCss] = await Promise.all([
	readFile(resolve(repositoryRoot, "docs/src/css/custom.css"), "utf8"),
	readFile(resolve(repositoryRoot, "webapp/src/styles.css"), "utf8"),
]);

describe("readTokens", () => {
	test("takes the last declaration, the way the cascade does", () => {
		const tokens = readTokens(":root { --a: 1rem; }\n:root {\n\t--a: 2rem;\n}");
		expect(tokens.light.get("--a")).toBe("2rem");
	});

	test("files a declaration under the theme of the block it sits in", () => {
		const tokens = readTokens(
			':root {\n\t--a: 1rem;\n}\n[data-theme="dark"] {\n\t--a: 2rem;\n}\n:root {\n\t--b: 3rem;\n}',
		);
		expect(tokens.light.get("--a")).toBe("1rem");
		expect(tokens.dark.get("--a")).toBe("2rem");
		expect(tokens.light.get("--b")).toBe("3rem");
	});
});

describe("findDrift", () => {
	test("reports a recoloured app token the docs did not follow", () => {
		const recoloured = appCss.replace(
			"--mentor: oklch(0.707 0.165 254.624)",
			"--mentor: oklch(0.6 0.2 30)",
		);
		expect(findDrift(docsCss, recoloured)).toContain(
			"dark --ifm-color-primary is oklch(0.707 0.165 254.624), but the dark --mentor it copies is oklch(0.6 0.2 30).",
		);
	});

	test("catches a re-declaration appended below the block it overrides", () => {
		expect(findDrift(`${docsCss}\n:root {\n\t--ifm-global-radius: 0.5rem;\n}\n`, appCss)).toEqual([
			"light --ifm-global-radius is 0.5rem, but the light --radius it copies is 0.625rem.",
		]);
	});

	test("reports a copied token renamed out of the docs stylesheet", () => {
		expect(findDrift(docsCss.replaceAll("--ifm-global-radius", "--ifm-radius"), appCss)).toEqual([
			"docs/src/css/custom.css no longer declares --ifm-global-radius for the light theme.",
		]);
	});

	test("reports a source token renamed out of the web app stylesheet", () => {
		expect(findDrift(docsCss, appCss.replaceAll("--radius:", "--corner:"))).toEqual([
			"webapp/src/styles.css no longer declares --radius for the light theme.",
		]);
	});
});
