import { RuleTester } from "oxlint/plugins-dev";

/**
 * `RuleTester` takes `describe`/`it` off the global scope (`test.globals` in `vite.config.ts`).
 * `eslintCompat` makes the columns it asserts on 1-based.
 */
export const ruleTester = new RuleTester({
	eslintCompat: true,
	languageOptions: { parserOptions: { lang: "tsx" } },
});
