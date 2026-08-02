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

const runtimeDeps = Object.keys(pkg.dependencies);

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
		alias: [
			{
				find: "@monaco-editor/react",
				replacement: path.resolve(dirname, "./src/test/monaco-editor-react.mock.tsx")
			},
			{ find: "@", replacement: path.resolve(dirname, "./src") }
		]
	},
	optimizeDeps: {
		noDiscovery: true,
		include: [
			...runtimeDeps.filter((dependency) => dependency !== "@monaco-editor/react"),
			"posthog-js/react",
			"use-sync-external-store/shim",
			"use-sync-external-store/shim/with-selector"
		]
	},
	test: {
		name: "storybook",
		fileParallelism: false,
		browser: {
			enabled: true,
			headless: true,
			provider: playwright({
				contextOptions: { reducedMotion: "reduce" }
			}),
			instances: [{ browser: "chromium" }]
		},
		setupFiles: [".storybook/vitest.setup.ts"],
		reporters: ["verbose", "junit"],
		outputFile: {
			junit: "./test-results/junit-storybook.xml"
		}
	}
});
