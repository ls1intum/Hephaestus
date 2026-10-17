import { tool, type ToolSet } from "ai";
import { z } from "zod";

import type { FileTree } from "./index.ts";

const MAX_LINES = 200;
const MAX_ENTRIES = 200;
const MAX_MATCHES = 50;
const MAX_MATCH_CHARS = 300;

export function evidenceTools(tree: FileTree | null): ToolSet {
	if (tree === null) {
		return {};
	}
	return {
		readFile: tool({
			description: `Read lines of a repository file, numbered. At most ${MAX_LINES} lines per call.`,
			inputSchema: z.object({
				path: z.string().describe("Path relative to the repository root"),
				startLine: z.int().min(1).optional(),
				endLine: z.int().min(1).optional(),
			}),
			execute: async ({ path, startLine = 1, endLine }) => {
				// Receipts cover only the lines shown to the model.
				const maxLines = Math.max(
					1,
					Math.min(endLine ?? Infinity, startLine + MAX_LINES - 1) - startLine + 1,
				);
				const file = await tree.read(path, { offsetLine: startLine, maxLines });
				if (file.status === "ABSENT") {
					return `${path} is not a file of the repository`;
				}
				if (file.status === "BINARY") {
					return `${path} is a binary file`;
				}
				if (file.text === "") {
					return "(no lines in that range)";
				}
				const lines = file.text.split("\n");
				const shown = lines.map((text, i) => `${startLine + i}: ${text}`).join("\n");
				const cut = file.truncated && (endLine ?? Infinity) >= startLine + lines.length;
				return cut ? `${shown}\n(more lines follow; read them with a later startLine)` : shown;
			},
		}),
		listFiles: tool({
			description: `List the files of a repository folder. At most ${MAX_ENTRIES}.`,
			inputSchema: z.object({ dir: z.string().describe("Folder relative to the repository root") }),
			execute: async ({ dir }) => {
				const prefix = dir.replace(/^\.?\/*/u, "").replace(/\/*$/u, "");
				const { paths, truncated } = await tree.list(prefix === "" ? "**" : `${prefix}/**`);
				const shown = paths.slice(0, MAX_ENTRIES);
				return `${shown.join("\n")}${truncated || paths.length > MAX_ENTRIES ? "\n(more files are not listed)" : ""}`;
			},
		}),
		grep: tool({
			description: `Search repository files with an extended regular expression. At most ${MAX_MATCHES} matches, as path:line: text. Matched text is limited to ${MAX_MATCH_CHARS} characters; … marks a shortened line.`,
			inputSchema: z.object({
				pattern: z.string(),
				glob: z.string().optional().describe('A file glob such as "*.ts"'),
			}),
			execute: async ({ pattern, glob }) => {
				const { matches, truncated } = await tree.grep(pattern, { glob, maxResults: MAX_MATCHES });
				const shown =
					matches
						.map((m) => {
							const text =
								m.content.length > MAX_MATCH_CHARS
									? `${m.content.slice(0, MAX_MATCH_CHARS - 1)}…`
									: m.content;
							return `${m.file}:${m.line}: ${text}`;
						})
						.join("\n") || "(no matches)";
				return truncated ? `${shown}\n(more matches are not listed)` : shown;
			},
		}),
	};
}
