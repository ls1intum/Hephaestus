import { ruleTester } from "../rule-tester.ts";
import { noNonAsciiFilename } from "./no-non-ascii-filename.ts";

const code = "export const value = 1;";

ruleTester.run("no-non-ascii-filename", noNonAsciiFilename, {
	valid: [
		{ code, filename: "src/components/common/relative-time.tsx" },
		{ code, filename: "src/lib/utils.ts" },
		// Every character the repo's own names actually use.
		{ code, filename: "src/routes/_authenticated/w/$workspaceSlug/index.tsx" },
		{ code, filename: "tools/oxlint/rules/no-non-ascii-filename.test.ts" },
		// The checkout path is the machine's, not the repo's, and is not this rule's business.
		{ code, filename: "/home/josé/héphaïstos/src/lib/utils.ts", cwd: "/home/josé/héphaïstos" },
	],
	invalid: [
		{
			// A Latin-1 letter: `café.ts` and `café.ts` are different byte strings that most
			// terminals draw identically.
			code,
			filename: "src/lib/café.ts",
			errors: [{ messageId: "nonAscii", data: { segment: "café.ts" }, line: 1, column: 1 }],
		},
		{
			// A directory is a path segment the repo owns just as much as the file.
			code,
			filename: "src/lib/naïve/utils.ts",
			errors: [{ messageId: "nonAscii", data: { segment: "naïve" } }],
		},
		{
			// A zero-width space is invisible in every editor and diff.
			code,
			filename: "src/lib/utils​.ts",
			errors: [{ messageId: "nonAscii" }],
		},
		{
			// An em dash reads as the ASCII hyphen the neighbouring files use.
			code,
			filename: "src/components/common/relative—time.tsx",
			errors: [{ messageId: "nonAscii" }],
		},
	],
});
