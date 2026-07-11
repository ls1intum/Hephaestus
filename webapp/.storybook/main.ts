import type { StorybookConfig } from '@storybook/react-vite';

import { join, dirname } from "path";
import { createRequire } from "module";

const require = createRequire(import.meta.url);

/**
* Resolves a package's absolute path — needed for Yarn PnP and monorepo setups.
*/
function getAbsolutePath(value: string): string {
  return dirname(require.resolve(join(value, 'package.json')))
}

const config: StorybookConfig = {
  stories: [
    "../src/**/*.mdx",
    "../src/**/*.stories.@(js|jsx|mjs|ts|tsx)"
  ],
  addons: [
    // addon-essentials functionality is built into Storybook 9+ core; no separate addon needed.
    getAbsolutePath('@storybook/addon-docs'),
    getAbsolutePath('@storybook/addon-onboarding'),
    getAbsolutePath('@chromatic-com/storybook'),
    getAbsolutePath("@storybook/addon-vitest"),
    getAbsolutePath('@storybook/addon-themes')
  ],
  framework: {
    name: getAbsolutePath('@storybook/react-vite'),
    options: {}
  },
  // Pre-bundle the dependencies Storybook's Vite would otherwise discover the first time a story
  // imports them. Under the browser-mode test runner (@storybook/addon-vitest) a mid-run discovery
  // re-optimizes and reloads the page ("optimized dependencies changed. reloading"), which aborts
  // whichever test is in flight with "Failed to fetch dynamically imported module". Declaring them
  // up front keeps the module graph stable for the whole run. Keep this list in sync with what Vite
  // reports as "new dependencies optimized" if the suite ever flakes on a reload again.
  viteFinal: async (viteConfig) => {
    viteConfig.optimizeDeps ??= {};
    viteConfig.optimizeDeps.include = [
      ...(viteConfig.optimizeDeps.include ?? []),
      "@ai-sdk/react",
      "@dnd-kit/core",
      "@dnd-kit/modifiers",
      "@dnd-kit/sortable",
      "@dnd-kit/utilities",
      "@sentry/react",
      "@tanstack/react-query-devtools",
      "@tanstack/react-router-devtools",
      "ai",
      "posthog-js/react",
      "uuid",
      "web-vitals"
    ];
    return viteConfig;
  }
};
export default config;
