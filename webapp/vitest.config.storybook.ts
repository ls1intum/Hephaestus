import path from "node:path";
import { fileURLToPath } from "node:url";

import { storybookTest } from "@storybook/addon-vitest/vitest-plugin";
import tailwindcss from "@tailwindcss/vite";
import viteReact from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { playwright } from "@vitest/browser-playwright";

const dirname =
  typeof __dirname !== "undefined"
    ? __dirname
    : path.dirname(fileURLToPath(import.meta.url));

// Vitest 4.x configuration for running Storybook interaction tests
// Run with: npx vitest --config vitest.config.storybook.ts
// See: https://storybook.js.org/docs/writing-tests/integrations/vitest-addon
export default defineConfig({
  plugins: [
    viteReact({
      babel: {
        plugins: ["babel-plugin-react-compiler"],
      },
    }),
    tailwindcss(),
    // The plugin will run tests for the stories defined in your Storybook config
    storybookTest({
      configDir: path.join(dirname, ".storybook"),
      // The --ci flag will skip prompts and not open a browser
      storybookScript: "npm run storybook -- --ci",
    }),
  ],
  resolve: {
    alias: {
      "@": path.resolve(dirname, "./src"),
      "@intelligence-service": path.resolve(
        dirname,
        "../server/intelligence-service/src/mentor",
      ),
      "@intelligence-service-utils": path.resolve(
        dirname,
        "../server/intelligence-service/src/shared",
      ),
    },
  },
  test: {
    name: "storybook",
    browser: {
      enabled: true,
      headless: true,
      provider: playwright(),
      instances: [{ browser: "chromium" }],
    },
    setupFiles: [".storybook/vitest.setup.ts"],
  },
});
