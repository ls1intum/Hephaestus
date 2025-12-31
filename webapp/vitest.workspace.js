// DEPRECATED: This file is kept for backward compatibility.
// Use vitest.config.storybook.ts instead for Storybook tests.
// Run: npx vitest --config vitest.config.storybook.ts
//
// This workspace file was originally created for Storybook 8.x with Vitest 2.x.
// Vitest 4.x deprecated `defineWorkspace` in favor of `test.projects`.
// See: https://vitest.dev/guide/migration

console.warn(
  "vitest.workspace.js is deprecated. Use vitest.config.storybook.ts for Storybook tests.",
);

// Re-export vite.config.js for basic Vitest usage
export { default } from "./vite.config.js";
