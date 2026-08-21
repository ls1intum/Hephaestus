import tailwindcss from "@tailwindcss/vite";
import viteReact from "@vitejs/plugin-react";
import type { PluginOption } from "vite";

/**
 * Add new app-source plugins here, not to one config: a story compiled differently from the app
 * stops being evidence about the app.
 */
export const appSourcePlugins = (): PluginOption[] => [
	viteReact({ compiler: true }),
	tailwindcss(),
];
