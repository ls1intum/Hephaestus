import path from "node:path";
import { fileURLToPath } from "node:url";

import { storybookTest } from "@storybook/addon-vitest/vitest-plugin";
import babel from "@rolldown/plugin-babel";
import tailwindcss from "@tailwindcss/vite";
import viteReact, { reactCompilerPreset } from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { playwright } from "@vitest/browser-playwright";

const dirname =
	typeof __dirname !== "undefined"
		? __dirname
		: path.dirname(fileURLToPath(import.meta.url));

const reactCompiler = await babel({ presets: [reactCompilerPreset()] });

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
	test: {
		name: "storybook",
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
