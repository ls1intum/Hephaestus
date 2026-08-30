import * as fs from "node:fs";
import path, { resolve } from "node:path";

import { tanstackRouter } from "@tanstack/router-plugin/vite";
import { parse } from "jsonc-parser";
import type { OxfmtConfig } from "oxfmt";
import type { OxlintConfig } from "oxlint";
import Terminal from "vite-plugin-terminal";
import type { ViteDevServer } from "vite-plus";
import { configDefaults } from "vitest/config";

import { appSourcePlugins } from "./vite.shared.ts";

// oxlint-disable-next-line typescript/no-unsafe-type-assertion
const formatConfig = parse(
	fs.readFileSync(new URL("../.oxfmtrc.json", import.meta.url), "utf8"),
) as OxfmtConfig;
const fmt = {
	...formatConfig,
	ignorePatterns: [
		"**/*.md",
		"**/*.html",
		"src/api/**",
		"src/routeTree.gen.ts",
		"public/mockServiceWorker.js",
	],
};

// oxlint-disable-next-line typescript/no-unsafe-type-assertion
const lintConfig = parse(
	fs.readFileSync(new URL(".oxlintrc.json", import.meta.url), "utf8"),
) as OxlintConfig;
const lint = {
	...lintConfig,
	options: { ...lintConfig.options, typeAware: true, typeCheck: true },
};

const viteConfig = {
	root: import.meta.dirname,
	fmt,
	lint,
	plugins: [
		tanstackRouter({ autoCodeSplitting: true }),
		...appSourcePlugins(),
		...Terminal({ output: ["terminal", "console"] }).map(
			(plugin) => plugin && { ...plugin, apply: "serve" as const },
		),
		{
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
		exclude: [...configDefaults.exclude, "e2e/**"],
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

export default viteConfig;
