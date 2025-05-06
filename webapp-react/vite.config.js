import tailwindcss from "@tailwindcss/vite";
import viteReact from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { resolve } from "node:path";
import Terminal from 'vite-plugin-terminal'
import { TanStackRouterVite } from "@tanstack/router-plugin/vite";

// https://vitejs.dev/config/
export default defineConfig(({ command, mode }) => {
  const isDevelopment = command !== 'build';
  
  return {
    plugins: [
      TanStackRouterVite({ autoCodeSplitting: true }),
      viteReact(),
      tailwindcss(),
      // Only use the terminal plugin during development
      isDevelopment && Terminal({
        console: 'terminal'
      })
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
