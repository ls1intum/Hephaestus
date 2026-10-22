import path from "node:path";

import { storybookTest } from "@storybook/addon-vitest/vitest-plugin";
import { defineConfig } from "vite-plus";
import { playwright } from "vite-plus/test/browser-playwright";

import { loadLintConfig } from "../webapp/tools/oxlint/load-config.ts";
import { extensionAliases, extensionVitePlugins } from "./vite.shared.ts";

const lint = loadLintConfig(new URL(".oxlintrc.json", import.meta.url));

/**
 * `vp -C extension check` inherits root formatting and reads this file’s `lint`;
 * `vp test --project unit|storybook` reads `test`.
 * The WXT build does not read this file — `wxt.config.ts` shares the plugins through
 * `vite.shared.ts` instead.
 */
export default defineConfig({
	root: import.meta.dirname,
	lint,
	resolve: { alias: extensionAliases },
	plugins: extensionVitePlugins(),
	test: {
		projects: [
			{
				extends: true,
				test: {
					name: "unit",
					include: ["src/**/*.test.ts"],
					environment: "node",
				},
			},
			{
				extends: true,
				plugins: [
					storybookTest({
						configDir: path.join(import.meta.dirname, ".storybook"),
						storybookScript: "vp run storybook:dev --ci",
					}),
				],
				optimizeDeps: {
					include: ["react", "react-dom", "react-dom/client", "lucide-react", "cn"],
				},
				test: {
					name: "storybook",
					maxWorkers: 2,
					browser: {
						enabled: true,
						headless: true,
						viewport: { width: 1024, height: 768 },
						provider: playwright({ contextOptions: { reducedMotion: "reduce" } }),
						instances: [{ browser: "chromium" }],
					},
					setupFiles: [".storybook/vitest.setup.ts"],
				},
			},
		],
	},
});
