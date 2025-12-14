import { resolve } from "node:path";
import tailwindcss from "@tailwindcss/vite";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import viteReact from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import Terminal from "vite-plugin-terminal";

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
				// Alias to share TS sources from the intelligence-service project
				"@intelligence-service": resolve(
					__dirname,
					"../server/intelligence-service/src/mentor",
				),
				"@intelligence-service-utils": resolve(
					__dirname,
					"../server/intelligence-service/src/shared",
				),
			},
		},
		server: {
			fs: {
				// Allow serving files from the monorepo root and sibling server directory
				allow: [
					resolve(__dirname, ".."),
					resolve(__dirname, "../server/intelligence-service"),
				],
			},
		},
	};
});
