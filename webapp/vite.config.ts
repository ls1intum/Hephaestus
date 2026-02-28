import path, { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import viteReact from "@vitejs/plugin-react";
import { defineConfig, type ViteDevServer } from "vite";
import Terminal from "vite-plugin-terminal";
import * as fs from "node:fs";

const __dirname = fileURLToPath(new URL(".", import.meta.url));

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
			// Dev only plugin to serialize the achievement node layout from the dev mode into a json file for consistency
			isDevelopment && ({
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
								// Adjust path if your structure differs slightly
								const filePath = path.resolve(__dirname, "src/components/achievements/coordinates.json");
								fs.writeFileSync(filePath, body);
								res.statusCode = 200;
								res.end("Layout saved successfully");
							} catch (e) {
								res.statusCode = 400;
								res.end("Invalid JSON");
							}
						})
						;
					});
				},
			}),
		].filter(Boolean), // Filter out falsy values
		build: {
			sourcemap: false, // Disable sourcemaps for now to reduce build memory usage
		},
		optimizeDeps: {
			exclude: ["storybook-static"],
		},
		test: {
			globals: true,
			environment: "jsdom",
			reporters: ["default", "junit"],
			outputFile: {
				junit: "./test-results/junit-webapp.xml",
			},
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
})
;
;
