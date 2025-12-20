/**
 * Session History Tool
 *
 * Past mentor sessions - Continuity and goal tracking.
 */

import { tool } from "ai";
import { and, desc, eq } from "drizzle-orm";
import { z } from "zod";
import db from "@/shared/db";
import { chatMessage, chatThread } from "@/shared/db/schema";
import type { ToolContext } from "./context";

// ─────────────────────────────────────────────────────────────────────────────
// Tool Factory
// ─────────────────────────────────────────────────────────────────────────────

export function createGetSessionHistoryTool(ctx: ToolContext) {
	return tool({
		description: `Get summaries of past mentor conversations for continuity and goal follow-up.

**When to use:**
- When following up on previous discussions ("last time we talked about...")
- When checking on goals or commitments they made
- To maintain continuity across sessions

**When NOT to use:**
- For first-time users (no history exists)
- When discussing current activity (use activity tools)

**Output includes:**
- Session titles and dates
- First user message (topic indicator)
- Whether the session may contain goals/commitments

CRITICAL: Use this to reference past conversations naturally, not robotically.`,

		inputSchema: z.object({
			limit: z
				.number()
				.min(1)
				.max(20)
				.describe("Number of past sessions to retrieve (1-20). Use 5 for recent context."),
		}),
		strict: true,

		execute: async ({ limit }) => {
			const threads = await db
				.select({
					id: chatThread.id,
					title: chatThread.title,
					createdAt: chatThread.createdAt,
				})
				.from(chatThread)
				.where(and(eq(chatThread.userId, ctx.userId), eq(chatThread.workspaceId, ctx.workspaceId)))
				.orderBy(desc(chatThread.createdAt))
				.limit(limit);

			const sessionsWithContext = await Promise.all(
				threads.map(async (thread) => {
					const firstUserMessage = await db
						.select({ parts: chatMessage.parts })
						.from(chatMessage)
						.where(and(eq(chatMessage.threadId, thread.id), eq(chatMessage.role, "user")))
						.orderBy(chatMessage.createdAt)
						.limit(1);

					const extractText = (message: { parts: unknown } | undefined): string => {
						if (!message?.parts) {
							return "";
						}
						const parts = message.parts as Array<{ type: string; text?: string }>;
						const textPart = parts.find((p) => p.type === "text");
						return textPart?.text?.slice(0, 150) ?? "";
					};

					return {
						id: thread.id,
						title: thread.title ?? "Untitled",
						date: thread.createdAt,
						firstMessage: extractText(firstUserMessage[0]),
					};
				}),
			);

			return {
				user: ctx.userLogin,
				sessionCount: sessionsWithContext.length,
				sessions: sessionsWithContext,
			};
		},

		toModelOutput({ output }) {
			if (output.sessionCount === 0) {
				return { type: "text" as const, value: `No previous sessions found for ${output.user}.` };
			}

			const lines: string[] = [`**Past Sessions for ${output.user}** (${output.sessionCount})`, ""];

			for (const s of output.sessions) {
				const dateStr = s.date ? new Date(s.date).toLocaleDateString() : "Unknown date";
				lines.push(`- **${s.title}** (${dateStr})`);
				if (s.firstMessage) {
					lines.push(`  _"${s.firstMessage}..."_`);
				}
			}

			return { type: "text" as const, value: lines.join("\n") };
		},
	});
}
