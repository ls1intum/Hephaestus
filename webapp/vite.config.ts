import * as fs from "node:fs";
import path, { resolve } from "node:path";

import { tanstackRouter } from "@tanstack/router-plugin/vite";
import { defineConfig, type ViteDevServer } from "vite";
import Terminal from "vite-plugin-terminal";
import { configDefaults } from "vitest/config";

import { appSourcePlugins } from "./vite.shared.ts";

// https://vitejs.dev/config/
export default defineConfig(({ command }) => {
	const isDevelopment = command !== "build";

	return {
		plugins: [
			tanstackRouter({ autoCodeSplitting: true }),
			...appSourcePlugins(),
			isDevelopment &&
				Terminal({
					output: ["terminal", "console"],
				}),
			// Dev only plugin to serialize the achievement node layout from the dev mode into a json file for consistency
			isDevelopment && {
				name: "save-achievement-layout",
				apply: "serve" as const,
				configureServer(server: ViteDevServer) {
					server.middlewares.use("/__save-coordinates", (req, res) => {
						if (req.method !== "POST") {
							res.statusCode = 405;
							res.end("Method Not Allowed");
							return;
						}

						let body = "";
						req.on("data", (chunk: Buffer) => {
							body += chunk.toString();
						});

						req.on("end", () => {
							try {
								JSON.parse(body);
								const filePath = path.resolve(
									import.meta.dirname,
									"src/components/achievements/coordinates.json",
								);
								fs.writeFileSync(filePath, body);
								res.statusCode = 200;
								res.end("Layout saved successfully");
							} catch {
								res.statusCode = 400;
								res.end("Invalid JSON");
							}
						});
					});
				},
			},
		],
		build: {
			sourcemap: false,
		},
		optimizeDeps: {
			exclude: ["storybook-static"],
		},
		test: {
			globals: true,
			environment: "jsdom",
			// Keep Vitest out of the Playwright harness — e2e/*.spec.ts is browser-driven and must run via
			// `playwright test`, not Vitest (it imports @playwright/test and has no jsdom equivalent).
			exclude: [...configDefaults.exclude, "e2e/**"],
			// Stand up the MSW Node server (handlers shared with Storybook) for the query-driven
			// auth component tests; harmless for tests that issue no requests (unhandled = bypass).
			setupFiles: ["./src/test/setup-msw.ts"],
			reporters: ["default", "junit"],
			outputFile: {
				junit: "./test-results/junit-webapp.xml",
			},
		},
		resolve: {
			alias: {
				"@": resolve(import.meta.dirname, "./src"),
			},
		},
		server: {
			port: Number.parseInt(process.env.WEBAPP_PORT ?? "", 10) || 4200,
			strictPort: true,
			fs: {
				allow: [resolve(import.meta.dirname, "..")],
			},
		},
	};
});
