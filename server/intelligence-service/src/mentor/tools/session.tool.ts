/**
 * Session History Tool
 *
 * Past mentor sessions - Continuity and goal tracking.
 */

import { tool } from "ai";
import { and, asc, desc, eq, inArray } from "drizzle-orm";
import pino from "pino";
import { z } from "zod";
import db from "@/shared/db";
import { chatMessage, chatThread } from "@/shared/db/schema";
import { MAX_SESSIONS, MESSAGE_PREVIEW_LENGTH } from "./constants";
import type { ToolContext } from "./context";
import { defineToolMeta } from "./define-tool";

const logger = pino({ name: "session-tool" });

// ═══════════════════════════════════════════════════════════════════════════
// TOOL DEFINITION (Single Source of Truth)
// ═══════════════════════════════════════════════════════════════════════════

const inputSchema = z.object({
	limit: z
		.number()
		.min(1)
		.max(MAX_SESSIONS)
		.describe(`Number of past sessions to retrieve (1-${MAX_SESSIONS}). Use 5 for recent context.`),
});

const { definition: getSessionHistoryDefinition, TOOL_DESCRIPTION } = defineToolMeta({
	name: "getSessionHistory",
	description: `Get summaries from past mentor sessions.

**When to use:**
- When referencing previous conversations
- When the user asks "what did we discuss last time?"
- When building on previous reflections

**When NOT to use:**
- For the current session (context already available)
- For document content (use getDocuments)

**Output includes:**
- Session dates and topics
- Key takeaways from past conversations`,
	inputSchema,
});

export { getSessionHistoryDefinition };

// ═══════════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

function extractTextFromParts(parts: unknown): string {
	if (!(parts && Array.isArray(parts))) {
		return "";
	}
	const textPart = (parts as Array<{ type: string; text?: string }>).find((p) => p.type === "text");
	return textPart?.text?.slice(0, MESSAGE_PREVIEW_LENGTH) ?? "";
}

// ═══════════════════════════════════════════════════════════════════════════
// TOOL FACTORY
// ═══════════════════════════════════════════════════════════════════════════

export function createGetSessionHistoryTool(ctx: ToolContext) {
	return tool({
		description: TOOL_DESCRIPTION,
		inputSchema,
		strict: true,

		execute: async ({ limit }) => {
			try {
				// Fetch threads
				const threads = await db
					.select({
						id: chatThread.id,
						title: chatThread.title,
						createdAt: chatThread.createdAt,
					})
					.from(chatThread)
					.where(
						and(eq(chatThread.userId, ctx.userId), eq(chatThread.workspaceId, ctx.workspaceId)),
					)
					.orderBy(desc(chatThread.createdAt))
					.limit(limit);

				if (threads.length === 0) {
					return {
						user: ctx.userLogin,
						sessionCount: 0,
						sessions: [],
					};
				}

				// Efficient batch query: get first user message per thread using DISTINCT ON
				// This fetches only 1 row per thread instead of all messages
				const threadIds = threads.map((t) => t.id);
				const firstMessages = await db
					.selectDistinctOn([chatMessage.threadId], {
						threadId: chatMessage.threadId,
						parts: chatMessage.parts,
					})
					.from(chatMessage)
					.where(and(inArray(chatMessage.threadId, threadIds), eq(chatMessage.role, "user")))
					.orderBy(chatMessage.threadId, asc(chatMessage.createdAt));

				// Build lookup map from first messages
				const messageByThread = new Map<string, string>();
				for (const msg of firstMessages) {
					messageByThread.set(msg.threadId, extractTextFromParts(msg.parts));
				}

				const sessions = threads.map((thread) => ({
					id: thread.id,
					title: thread.title ?? "Untitled",
					date: thread.createdAt,
					firstMessage: messageByThread.get(thread.id) ?? "",
				}));

				return {
					user: ctx.userLogin,
					sessionCount: sessions.length,
					sessions,
				};
			} catch (error) {
				logger.error({ error, userId: ctx.userId }, "Failed to fetch session history");
				return {
					user: ctx.userLogin,
					sessionCount: 0,
					sessions: [],
					_error: "Data temporarily unavailable. Session history may be incomplete.",
				};
			}
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
