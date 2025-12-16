import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import tailwindcss from "@tailwindcss/vite";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import viteReact from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import Terminal from "vite-plugin-terminal";

// Read version from package.json for build-time injection
// This ensures the JS bundle content-hash changes when the version changes,
// which is critical for proper cache invalidation
const packageJson = JSON.parse(
	readFileSync(resolve(__dirname, "package.json"), "utf-8"),
);
const appVersion = packageJson.version;

// https://vitejs.dev/config/
export default defineConfig(({ command }) => {
	const isDevelopment = command !== "build";

	return {
		plugins: [
			tanstackRouter({ autoCodeSplitting: true }),
			viteReact({
				babel: {
					plugins: ["babel-plugin-react-compiler"],
				},
			}),
			tailwindcss(),
			// Only use the terminal plugin during development
			isDevelopment &&
				Terminal({
					output: ["terminal", "console"],
				}),
		].filter(Boolean), // Filter out falsy values
		build: {
			sourcemap: true,
		},
		optimizeDeps: {
			exclude: ["storybook-static"],
		},
		test: {
			globals: true,
			environment: "jsdom",
		},
		resolve: {
			alias: {
				"@": resolve(__dirname, "./src"),
			},
		},
		define: {
			// Inject version at build time for cache-busting
			// This ensures JavaScript bundles have different content-hashes between versions
			__APP_VERSION__: JSON.stringify(appVersion),
		},
	};
});
