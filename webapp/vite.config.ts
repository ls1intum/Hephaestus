import path, { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import babel from "@rolldown/plugin-babel";
import tailwindcss from "@tailwindcss/vite";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import viteReact, { reactCompilerPreset } from "@vitejs/plugin-react";
import { defineConfig, type PluginOption, type ViteDevServer } from "vite";
import Terminal from "vite-plugin-terminal";
import * as fs from "node:fs";

const __dirname = fileURLToPath(new URL(".", import.meta.url));

// React Compiler runs through @rolldown/plugin-babel using the preset's
// pre-baked Rolldown filters (default compilationMode "infer" compiles every
// PascalCase function using JSX/hooks; no "use memo" opt-ins are used).
const reactCompiler = await babel({ presets: [reactCompilerPreset()] });

// https://vitejs.dev/config/
export default defineConfig(({ command }) => {
	const isDevelopment = command !== "build";

	return {
		plugins: ([
			tanstackRouter({ autoCodeSplitting: true }),
			viteReact(),
			reactCompiler,
			tailwindcss(),
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
		].filter(Boolean) as unknown as PluginOption[]),
		build: {
			sourcemap: false,
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
			},
		},
		server: {
			port: parseInt(process.env.WEBAPP_PORT ?? "", 10) || 4200,
			strictPort: true,
			fs: {
				allow: [resolve(__dirname, "..")],
			},
		},
	};
});
