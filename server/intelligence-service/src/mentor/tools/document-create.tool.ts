/**
 * Document creation tool for the AI mentor.
 *
 * Creates structured documents (weekly status updates, reflection summaries)
 * based on the conversation context. Uses the full conversation history
 * including tool results to generate accurate, data-backed documents.
 */

import {
	type ModelMessage,
	smoothStream,
	streamText,
	tool,
	type UIMessage,
	type UIMessageStreamWriter,
} from "ai";
import { z } from "zod";
import env from "@/env";
import { createDocument as createDocumentInDb } from "@/mentor/documents/data";
import { formatConversationForDocument } from "@/shared/ai/messages";
import { getTelemetryOptions } from "@/shared/ai/telemetry";
import { type DocumentKind, DocumentKindEnum } from "@/shared/document";
import { toolCallIdToUuid } from "@/shared/tool-call-id";
import { defineToolMeta } from "./define-tool";

// ═══════════════════════════════════════════════════════════════════════════
// TOOL DEFINITION (Single Source of Truth)
// ═══════════════════════════════════════════════════════════════════════════

const inputSchema = z.object({
	title: z
		.string()
		.min(1)
		.max(255)
		.describe('Document title, e.g. "Weekly Status - Dec 16, 2024" or "Sprint 5 Reflection"'),
	kind: DocumentKindEnum.describe('Document type. Use "text" (only supported type).'),
});

const { definition: createDocumentDefinition, TOOL_DESCRIPTION } = defineToolMeta({
	name: "createDocument",
	description: `Create a new document (reflection note, session summary) for the user.

**When to use:**
- When the user wants to save a reflection
- When creating a session summary at the end of a conversation
- When documenting learnings or action items

**When NOT to use:**
- For temporary notes (just include in chat response)
- When updating an existing document (use updateDocument)

**Document kinds:**
- "reflection": Personal reflection on work or learning
- "summary": Session summary with accomplishments, challenges, learnings`,
	inputSchema,
});

export { createDocumentDefinition };

// ═══════════════════════════════════════════════════════════════════════════
// INPUT TYPE
// ═══════════════════════════════════════════════════════════════════════════

type Input = z.infer<typeof inputSchema>;

// ═══════════════════════════════════════════════════════════════════════════
// SYSTEM PROMPT
// ═══════════════════════════════════════════════════════════════════════════

/**
 * System prompt for the document generator.
 *
 * Key design: We separate the format instructions from the conversation data.
 * The conversation is passed via the user message, not crammed into the system prompt.
 */
const DOCUMENT_SYSTEM_PROMPT = `You are generating a structured document for a software developer based on their conversation with an AI mentor.

Your task: Synthesize the conversation into a clean, well-organized document.

**Document Structure:**

## 🎯 Accomplishments
- Specific completed work with PR/issue references (e.g., "Merged #123 - Add user authentication")
- Focus on outcomes and impact

## ⚠️ Challenges & Impediments
- Blockers encountered and their current status
- What was tried, what worked/didn't

## 💡 Learnings & Insights
- Technical or process learnings
- What would be done differently

## 📋 Next Steps
- Specific, achievable goals
- Dependencies or support needed

## ❓ Discussion Topics
- Questions for supervisor
- Items needing feedback

**Rules:**
- Use actual PR numbers and titles from the conversation
- Be concise but specific
- Skip sections if no relevant content (don't invent)
- Use markdown formatting`;

// ═══════════════════════════════════════════════════════════════════════════
// DOCUMENT GENERATION
// ═══════════════════════════════════════════════════════════════════════════

interface StreamParams {
	id: string;
	title: string;
	dataStream: UIMessageStreamWriter<UIMessage>;
	conversationContext: string;
}

/**
 * Streams document content to the client.
 *
 * Design: The conversation context is passed as the user message, not stuffed
 * into the system prompt. This gives the model a cleaner separation between
 * instructions (system) and data (user message).
 */
async function streamDocumentContent(params: StreamParams): Promise<string> {
	const { id, title, dataStream, conversationContext } = params;
	let content = "";

	// Build a proper user prompt that includes both the request and context
	const userPrompt = conversationContext
		? `Create a document titled "${title}" based on this conversation:\n\n${conversationContext}`
		: `Create a document titled "${title}". No conversation context available - create a template.`;

	// Enable telemetry for document generation
	const telemetryOptions = getTelemetryOptions({
		operation: "document:create",
	});

	const { fullStream } = streamText({
		model: env.defaultModel,
		system: DOCUMENT_SYSTEM_PROMPT,
		prompt: userPrompt,
		experimental_transform: smoothStream({ chunking: "word" }),
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

// ═══════════════════════════════════════════════════════════════════════════
// TOOL FACTORY
// ═══════════════════════════════════════════════════════════════════════════

interface ToolFactoryParams {
	dataStream: UIMessageStreamWriter<UIMessage>;
	workspaceId: number | null;
	userId: number | null;
}

/**
 * Factory to create the document creation tool.
 * Requires dataStream for streaming document content to the client.
 */
export const createDocumentTool = ({ dataStream, workspaceId, userId }: ToolFactoryParams) =>
	tool({
		description: TOOL_DESCRIPTION,

		inputSchema,
		strict: true, // Ensure model follows schema strictly

		execute: async ({ title, kind }: Input, context) => {
			const id = toolCallIdToUuid(context?.toolCallId);
			const messages: ModelMessage[] = context?.messages ?? [];

			// Signal document creation start
			dataStream.write({
				type: "data-document-create",
				data: { id, title, kind: "text" },
				transient: true,
			});

			// Extract conversation context (includes tool results!)
			const conversationContext = formatConversationForDocument(messages);

			let content = "";
			try {
				content = await streamDocumentContent({
					id,
					title,
					dataStream,
					conversationContext,
				});
			} finally {
				dataStream.write({
					type: "data-document-finish",
					data: { id, kind: "text" },
					transient: true,
				});
			}

			// Persist if we have workspace context
			const doc =
				workspaceId && userId
					? await createDocumentInDb({
							id,
							title,
							content,
							kind: kind as DocumentKind,
							workspaceId,
							userId,
						})
					: null;

			return {
				id: doc?.id ?? id,
				title: doc?.title ?? title,
				kind: doc?.kind ?? kind,
				content: doc?.content ?? content,
			};
		},
	});
