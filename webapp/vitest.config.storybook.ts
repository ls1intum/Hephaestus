import path from "node:path";
import { fileURLToPath } from "node:url";

import { storybookTest } from "@storybook/addon-vitest/vitest-plugin";
import babel from "@rolldown/plugin-babel";
import tailwindcss from "@tailwindcss/vite";
import viteReact, { reactCompilerPreset } from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { playwright } from "@vitest/browser-playwright";
import pkg from "./package.json" with { type: "json" };

const dirname =
	typeof __dirname !== "undefined"
		? __dirname
		: path.dirname(fileURLToPath(import.meta.url));

const reactCompiler = await babel({ presets: [reactCompilerPreset()] });

// Packages in `dependencies` that are NOT imported as JS by app/story code, so force-including
// them below would make esbuild fail to resolve a valid entry point (or would just be dead weight).
const NON_RUNTIME_DEPS = new Set([
	"@tailwindcss/vite", // Vite plugin (used only in vite.config.ts), not imported by app code
	"@tanstack/router-plugin", // build-time route codegen plugin
	"tailwindcss", // PostCSS tool wired up via `@import`/`@plugin` in styles.css, no browser entry
	"tailwindcss-animate", // Tailwind CSS plugin, consumed the same way via styles.css
	"@primer/primitives", // design tokens (CSS/JSON), consumed via styles.css
	"@gitlab/svgs" // icon source data is copied into gitlab-icons.tsx, never imported live
]);

// Runtime deps that are (mis)declared under devDependencies in package.json instead of dependencies,
// so they don't show up in the `dependencies` crawl below, but app code does import them at runtime:
// `web-vitals` is dynamically imported from src/reportWebVitals.ts.
const MISCLASSIFIED_RUNTIME_DEPS = ["web-vitals"];

// Every remaining runtime dependency, pre-bundled up front (see optimizeDeps.include below for why).
const runtimeDeps = [
	...Object.keys(pkg.dependencies).filter((name) => !NON_RUNTIME_DEPS.has(name)),
	...MISCLASSIFIED_RUNTIME_DEPS
];

export default defineConfig({
	plugins: [
		viteReact(),
		reactCompiler,
		tailwindcss(),
		storybookTest({
			configDir: path.join(dirname, ".storybook"),
			storybookScript: "pnpm run storybook -- --ci"
		})
	],
	resolve: {
		alias: {
			"@": path.resolve(dirname, "./src")
		}
	},
	// Root cause of the "Failed to fetch dynamically imported module" / "Vitest failed to find the
	// runner" deadlock (vitest-dev/vitest#9509, #8447; storybookjs/storybook#33067): Vite's dependency
	// optimizer discovers bare imports lazily as the browser actually requests modules. 124 story files
	// pull in wildly different transitive deps, so partway through the run Vite finds a dep it hasn't
	// pre-bundled yet, logs "✨ new dependencies optimized: ...", and fires "optimized dependencies
	// changed. reloading" — a full module-graph invalidation of the (shared, isolate:false) page. Any
	// fetch in flight when that happens comes back 404/reset, which surfaces as one of the two errors
	// above; with isolate:false every file sharing that page then fails too. This is a race, so it is
	// load-dependent (confirmed locally: reproduces reliably under `taskset -c 0,1`, i.e. a 2-vCPU
	// runner, and even unconstrained often enough to fail one run in a handful).
	// A first attempt used `optimizeDeps.entries` to make the esbuild scanner crawl every story file
	// up front — it still missed deps that only became reachable through a shared component (e.g.
	// @dnd-kit/*, @sentry/react, posthog-js/react, the TanStack devtools, `ai`) rather than a direct
	// story import, so the reload still fired mid-run. The scanner's static crawl is fundamentally
	// best-effort; the only way to guarantee zero mid-run discoveries is to skip scanning and force
	// every runtime dependency into the pre-bundle unconditionally via optimizeDeps.include.
	optimizeDeps: {
		include: [...runtimeDeps, "posthog-js/react"]
	},
	// Pre-transform the shared setup module on top of the pre-bundle above: warms Vite's per-file
	// transform cache so the first suite's import of vitest.setup.ts doesn't pay cold-transform cost.
	server: {
		warmup: {
			clientFiles: ["./.storybook/vitest.setup.ts"]
		}
	},
	test: {
		name: "storybook",
		// Parallel suite scheduling in browser mode deadlocked this run (~10 files in flight stall
		// after ~520 tests, locally and in CI). Serial file execution trades a few minutes of wall
		// clock for a run that always finishes.
		fileParallelism: false,
		// Browser mode's default (isolate: true) tears down and recreates a fresh iframe for every
		// test FILE — ~124 teardown/recreate cycles for this suite, each one a window where the
		// dependency-reload race above (or ordinary CI scheduling jitter) can strand a request and
		// wedge the runner ("Vitest failed to find the runner" fires when a file's runner never
		// re-registers after its iframe is replaced). Reusing one iframe for the whole run collapses
		// that to a single window. Documented mitigation: storybook.js.org/docs/writing-tests/
		// integrations/vitest-addon ("Known flakiness" — disable isolation on resource-constrained CI).
		isolate: false,
		browser: {
			enabled: true,
			headless: true,
			provider: playwright(),
			instances: [{ browser: "chromium" }]
		},
		setupFiles: [".storybook/vitest.setup.ts"],
		reporters: ["verbose", "junit"],
		outputFile: {
			junit: "./test-results/junit-storybook.xml"
		}
	}
});
