import { readFileSync } from "node:fs";

import { parse } from "jsonc-parser";
import type { OxlintConfig } from "oxlint";
import { defineConfig } from "vite-plus";

// oxlint-disable-next-line typescript/no-unsafe-type-assertion
const lintConfig = parse(
	readFileSync(new URL(".oxlintrc.json", import.meta.url), "utf8"),
) as OxlintConfig;
const lint = {
	...lintConfig,
	jsPlugins: ["../webapp/tools/oxlint/index.ts"],
	options: { ...lintConfig.options, typeAware: true, typeCheck: true },
};

export default defineConfig({ root: import.meta.dirname, lint });
