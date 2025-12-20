/**
 * Document update tool for the AI mentor.
 *
 * Updates existing documents based on user instructions and conversation context.
 * Creates a new version in the database, preserving history.
 */

import {
	type ModelMessage,
	smoothStream,
	streamText,
	tool,
	type UIMessage,
	type UIMessageStreamWriter,
} from "ai";
import { desc, eq } from "drizzle-orm";
import { z } from "zod";
import env from "@/env";
import { formatConversationForDocument } from "@/shared/ai/messages";
import { getTelemetryOptions } from "@/shared/ai/telemetry";
import db from "@/shared/db";
import { document as docTable } from "@/shared/db/schema";

// ─────────────────────────────────────────────────────────────────────────────
// Schema
// ─────────────────────────────────────────────────────────────────────────────

const inputSchema = z.object({
	id: z.string().uuid().describe("The document ID to update."),
	description: z.string().describe("What changes to make to the document."),
});

type Input = z.infer<typeof inputSchema>;
type DocumentRow = typeof docTable.$inferSelect;

// ─────────────────────────────────────────────────────────────────────────────
// Database Operations
// ─────────────────────────────────────────────────────────────────────────────

async function getLatestDocumentRow(id: string): Promise<DocumentRow | undefined> {
	const rows = await db
		.select()
		.from(docTable)
		.where(eq(docTable.id, id))
		.orderBy(desc(docTable.versionNumber))
		.limit(1);
	return rows[0];
}

async function persistNextVersion(params: {
	id: string;
	base: DocumentRow | undefined;
	content: string;
}): Promise<DocumentRow | undefined> {
	const { id, base, content } = params;
	if (!base) {
		return undefined;
	}

	const nextVersion = (base.versionNumber ?? 0) + 1;

	const rows = await db
		.insert(docTable)
		.values({
			id,
			versionNumber: nextVersion,
			createdAt: new Date().toISOString(),
			title: base.title ?? "",
			content,
			kind: (base.kind as "text" | undefined) ?? "text",
			userId: base.userId ?? 0,
			workspaceId: base.workspaceId,
		})
		.returning();

	return rows[0];
}

// ─────────────────────────────────────────────────────────────────────────────
// Document Update
// ─────────────────────────────────────────────────────────────────────────────

interface StreamParams {
	id: string;
	currentContent: string;
	description: string;
	conversationContext: string;
	dataStream: UIMessageStreamWriter<UIMessage>;
}

/**
 * Streams updated document content to the client.
 *
 * Uses predicted outputs for faster streaming when supported by the provider.
 */
async function streamUpdatedContent(params: StreamParams): Promise<string> {
	const { id, currentContent, description, conversationContext, dataStream } = params;
	let content = "";

	// Build system prompt with current document
	const systemPrompt = `You are updating an existing document based on the user's instructions.

**Current Document:**
${currentContent}

**Rules:**
- Apply the requested changes precisely
- Preserve structure and formatting unless asked to change it
- Keep existing content that isn't affected by the change
- Use markdown formatting`;

	// Build user prompt with context and instructions
	let userPrompt = `**Requested Changes:** ${description}`;
	if (conversationContext) {
		userPrompt += `\n\n**Recent Conversation (for context):**\n${conversationContext}`;
	}

	// Enable telemetry for document updates
	const telemetryOptions = getTelemetryOptions({
		operation: "document:update",
	});

	const { fullStream } = streamText({
		model: env.defaultModel,
		system: systemPrompt,
		prompt: userPrompt,
		experimental_transform: smoothStream({ chunking: "word" }),
		// Use predicted outputs for faster streaming (OpenAI feature)
		providerOptions: {
			openai: {
				prediction: { type: "content", content: currentContent },
			},
		},
		...telemetryOptions,
	});

	for await (const delta of fullStream) {
		if (delta.type === "text-delta") {
			const text = (delta as { type: "text-delta"; text: string }).text;
			if (text) {
				content += text;
				dataStream.write({
					type: "data-document-delta",
					data: { id, kind: "text", delta: text },
					transient: true,
				});
			}
		}
	}

	return content;
}

// ─────────────────────────────────────────────────────────────────────────────
// Tool Export
// ─────────────────────────────────────────────────────────────────────────────

interface ToolFactoryParams {
	dataStream: UIMessageStreamWriter<UIMessage>;
}

export const updateDocument = ({ dataStream }: ToolFactoryParams) =>
	tool({
		description: `Update an existing document with new content or changes.

**When to use:**
- User asks to modify, update, or add to an existing document
- User wants to incorporate new information into a document
- User asks to fix or improve document content

**What it does:**
- Retrieves the current document version
- Applies the requested changes while preserving structure
- Creates a new version (keeps history)
- Streams updates to the UI in real-time`,

		inputSchema,
		strict: true, // Ensure model follows schema strictly

		execute: async ({ id, description }: Input, context) => {
			const messages: ModelMessage[] = context?.messages ?? [];

			// Signal update start
			dataStream.write({
				type: "data-document-update",
				data: { id, kind: "text" },
				transient: true,
			});

			const latestRow = await getLatestDocumentRow(id);
			const currentContent = latestRow?.content ?? "";

			// Get conversation context for additional info
			const conversationContext = formatConversationForDocument(messages);

			let content = "";
			try {
				content = await streamUpdatedContent({
					id,
					currentContent,
					description,
					conversationContext,
					dataStream,
				});
			} finally {
				dataStream.write({
					type: "data-document-finish",
					data: { id, kind: "text" },
					transient: true,
				});
			}

			// Persist new version
			const contentToPersist = content || currentContent;
			const row = await persistNextVersion({ id, base: latestRow, content: contentToPersist });

			return {
				id,
				title: row?.title ?? latestRow?.title ?? "",
				content: row?.content ?? contentToPersist,
				kind: (row?.kind as "text" | undefined) ?? (latestRow?.kind as "text") ?? "text",
				description,
			};
		},
	});
