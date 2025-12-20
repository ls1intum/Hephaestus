/**
 * Documents Tool
 *
 * Documents created in past sessions.
 */

import { tool } from "ai";
import { and, desc, eq, sql } from "drizzle-orm";
import { z } from "zod";
import db from "@/shared/db";
import { document } from "@/shared/db/schema";
import type { ToolContext } from "./context";
import { defineToolMeta } from "./define-tool";

// ═══════════════════════════════════════════════════════════════════════════
// TOOL DEFINITION (Single Source of Truth)
// ═══════════════════════════════════════════════════════════════════════════

const inputSchema = z.object({
	limit: z
		.number()
		.min(1)
		.max(20)
		.describe("Maximum documents to retrieve (1-20). Use 5 for recent, 10 for comprehensive."),
});

const { definition: getDocumentsDefinition, TOOL_DESCRIPTION } = defineToolMeta({
	name: "getDocuments",
	description: `Get documents (reflection notes, summaries) created in past mentor sessions.

**When to use:**
- When referencing past reflections or learnings
- When the user wants to review previous session summaries

**When NOT to use:**
- For code or PR content (use getPullRequests)

**Output includes:**
- Document titles and types
- Creation dates
- Content previews`,
	inputSchema,
});

export { getDocumentsDefinition };

// ═══════════════════════════════════════════════════════════════════════════
// TOOL FACTORY
// ═══════════════════════════════════════════════════════════════════════════

export function createGetDocumentsTool(ctx: ToolContext) {
	return tool({
		description: TOOL_DESCRIPTION,
		inputSchema,
		strict: true,

		execute: async ({ limit }) => {
			const docs = await db
				.select({
					id: document.id,
					title: document.title,
					kind: document.kind,
					createdAt: document.createdAt,
					preview: sql<string>`LEFT(${document.content}, 200)`,
				})
				.from(document)
				.where(and(eq(document.userId, ctx.userId), eq(document.workspaceId, ctx.workspaceId)))
				.orderBy(desc(document.createdAt))
				.limit(limit);

			return {
				user: ctx.userLogin,
				count: docs.length,
				documents: docs.map((d) => ({
					id: d.id,
					title: d.title,
					kind: d.kind,
					createdAt: d.createdAt,
					preview: d.preview,
				})),
			};
		},

		toModelOutput({ output }) {
			if (output.count === 0) {
				return { type: "text" as const, value: `No documents found for ${output.user}.` };
			}

			const lines: string[] = [`**Documents for ${output.user}** (${output.count})`, ""];

			for (const d of output.documents) {
				const dateStr = d.createdAt ? new Date(d.createdAt).toLocaleDateString() : "Unknown date";
				const kind = d.kind ? `[${d.kind}]` : "";
				lines.push(`- **${d.title}** ${kind} (${dateStr})`);
				if (d.preview) {
					lines.push(`  _${d.preview.slice(0, 100)}..._`);
				}
			}

			return { type: "text" as const, value: lines.join("\n") };
		},
	});
}
