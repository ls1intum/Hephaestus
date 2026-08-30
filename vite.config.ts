import { readFileSync } from "node:fs";

import { parse } from "jsonc-parser";
import type { OxfmtConfig } from "oxfmt";
import { defineConfig } from "vite-plus";

// oxlint-disable-next-line typescript/no-unsafe-type-assertion
const formatConfig = parse(
	readFileSync(new URL(".oxfmtrc.json", import.meta.url), "utf8"),
) as OxfmtConfig;
const fmt = {
	...formatConfig,
	ignorePatterns: [...(formatConfig.ignorePatterns ?? []), "**/*.md", "**/*.html"],
};

const cached = (command: string) => ({ command });
const uncached = (command: string | string[]) => ({ command, cache: false as const });

const checkTasks = [
	"gate:package-manager",
	"gate:agent-runtime-pins",
	"gate:java-nullness",
	"gate:server",
	"gate:lint-contract",
	"gate:webapp",
	"gate:agents",
	"gate:load-format",
	"gate:agent-tests",
	"gate:stories",
	"gate:story-sort",
	"gate:components",
	"gate:diagrams",
	"gate:docs-tokens",
	"gate:preview-stack",
	"gate:env",
	"gate:contracts",
	"gate:instructions",
	"gate:changesets",
	"gate:docs",
] as const;

export default defineConfig({
	fmt,
	defaultPackage: "./webapp",
	run: {
		tasks: {
			"gate:package-manager": uncached("node scripts/check-package-manager.ts"),
			"gate:agent-runtime-pins": cached("node scripts/check-agent-runtime-pins.ts"),
			"gate:java-nullness": uncached(
				"node scripts/check-java-nullness.ts && node --test scripts/check-java-nullness.test.ts",
			),
			"gate:lint-contract": uncached("node --test scripts/lint-contract.test.ts"),
			"gate:webapp": cached("vp -C webapp check"),
			"gate:agents": uncached("pnpm run check:agents"),
			"gate:load-format": cached("vp fmt --check 'load-tests/**/*.js'"),
			"gate:agent-tests": uncached(
				"node --test server/application/src/test/resources/agent/*.spec.ts docker/agents/precompute/*.test.ts docker/agents/precompute/lib/*.test.ts",
			),
			"gate:stories": cached("node scripts/check-story-prose.ts"),
			"gate:story-sort": cached("node scripts/check-story-sort.ts"),
			"gate:components": cached("node scripts/check-presentational-components.ts"),
			"gate:diagrams": cached("node scripts/check-mermaid-diagrams.ts"),
			"gate:docs-tokens": cached(
				"node scripts/check-docs-tokens.ts && node --test scripts/check-docs-tokens.test.ts",
			),
			"gate:preview-stack": uncached(
				"node scripts/check-preview-stack.ts && node --test scripts/check-preview-stack.test.ts",
			),
			"gate:contracts": uncached(
				"node scripts/validate-artifact-source-contracts.ts && node scripts/check-artifact-source-contract-immutability.ts && node --test scripts/check-artifact-source-contract-immutability.test.ts",
			),
			"gate:instructions": uncached(
				"node scripts/check-agent-instructions.ts && node --test scripts/check-agent-instructions.test.ts",
			),
			"gate:changesets": cached(
				"node --test scripts/verify-changesets.test.ts scripts/sync-release-version.test.ts",
			),
			"gate:docs": uncached("pnpm run format:docs:check && pnpm run docs:lint"),

			"gate:server": uncached("pnpm run check:server"),
			"gate:env": uncached(
				"node scripts/check-env-defaults.ts && node scripts/check-env-roles.ts && node --test scripts/check-env-roles.test.ts scripts/self-host-setup.test.ts",
			),

			"affected:agents": uncached(["vp run gate:agents", "vp run gate:agent-tests"]),
			"affected:docs": uncached([
				"vp run gate:docs",
				"vp run gate:diagrams",
				"vp run gate:docs-tokens",
				"vp run gate:instructions",
			]),
			"affected:server": uncached(["vp run gate:java-nullness", "vp run gate:server"]),
			"affected:webapp": uncached([
				"vp run gate:webapp",
				"vp run gate:components",
				"vp run gate:stories",
				"vp run gate:story-sort",
			]),
			quality: uncached(checkTasks.map((task) => `vp run ${task}`)),

			"gate:webapp-tests": uncached("pnpm run test:webapp"),
			"gate:webapp-build": uncached("vp -C webapp build"),
			"gate:load-syntax": uncached("pnpm run test:load:syntax"),
			"gate:verify:storybook-tests": uncached(
				"vp -C webapp test --run --config vitest.config.storybook.ts",
			),
			"gate:verify:webapp-build": uncached("node scripts/verify-webapp-build.ts"),
			"gate:verify:storybook-build": uncached("vp -C webapp exec storybook build --stats-json"),
			"gate:verify:docs-build": uncached("vp run --filter docs build"),
			"gate:verify:server": uncached("pnpm run test:server:verification"),
			verification: uncached([
				"vp run quality",
				"vp run gate:webapp-tests",
				"vp run gate:verify:storybook-tests",
				"vp run gate:verify:server",
				"vp run gate:verify:webapp-build",
				"vp run gate:verify:storybook-build",
				"vp run gate:verify:docs-build",
			]),
		},
	},
});
