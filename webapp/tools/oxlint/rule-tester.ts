import { RuleTester } from "oxlint/plugins-dev";

/**
 * `RuleTester` picks up `describe`/`it` from the global scope, which `vite.config.ts` provides via
 * `test.globals`. `eslintCompat` makes reported columns 1-based, matching every other error location
 * in this repo.
 */
export const ruleTester = new RuleTester({
	eslintCompat: true,
	languageOptions: { parserOptions: { lang: "tsx" } },
});
