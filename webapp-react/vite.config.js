import { resolve } from "node:path";
import tailwindcss from "@tailwindcss/vite";
import { TanStackRouterVite } from "@tanstack/router-plugin/vite";
import viteReact from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import Terminal from "vite-plugin-terminal";

// https://vitejs.dev/config/
export default defineConfig(({ command, mode }) => {
	const isDevelopment = command !== "build";

	return {
		plugins: [
			TanStackRouterVite({ autoCodeSplitting: true }),
			viteReact(),
			tailwindcss(),
			// Only use the terminal plugin during development
			isDevelopment &&
				Terminal({
					output: ['terminal', 'console']
				}),
		].filter(Boolean), // Filter out falsy values
		test: {
			globals: true,
			environment: "jsdom",
		},
		resolve: {
			alias: {
				"@": resolve(__dirname, "./src"),
			},
		},
	};
});
