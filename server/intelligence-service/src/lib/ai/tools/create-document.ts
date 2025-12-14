import {
	smoothStream,
	streamText,
	tool,
	type UIMessage,
	type UIMessageStreamWriter,
} from "ai";
import { z } from "zod";
import db from "../../../db";
import { document as docTable } from "../../../db/schema";
import env from "../../../env";
import { toolCallIdToUuid } from "../../../routes/mentor/chat/tool-call-id";
import { DocumentKindEnum } from "../../../routes/mentor/documents/documents.schemas";

const inputSchema = z.object({
	title: z.string().min(1).max(255),
	kind: DocumentKindEnum,
});

type Input = z.infer<typeof inputSchema>;

export const createDocument = ({
	dataStream,
}: {
	dataStream: UIMessageStreamWriter<UIMessage>;
}) =>
	tool({
		description:
			"Create a document for writing or content creation. Emits streaming UI data for the client.",
		inputSchema,
		execute: async ({ title, kind }: Input, context) => {
			const id = toolCallIdToUuid(context?.toolCallId);

			dataStream.write({
				type: "data-document-create",
				data: { id, title, kind: "text" },
				transient: true,
			});

			let draftContent = "";
			try {
				const { fullStream } = streamText({
					model: env.defaultModel,
					system:
						"Write about the given topic. Markdown is supported. Use headings wherever appropriate.",
					experimental_transform: smoothStream({ chunking: "word" }),
					prompt: title,
				});

				for await (const delta of fullStream) {
					if (delta.type === "text-delta") {
						const text = (delta as { type: string; text?: string }).text ?? "";
						if (text) {
							draftContent += text;
							dataStream.write({
								type: "data-document-delta",
								data: { id, kind: "text", delta: text },
								transient: true,
							});
						}
					}
				}
			} finally {
				dataStream.write({
					type: "data-document-finish",
					data: { id, kind: "text" },
					transient: true,
				});
			}

			const now = new Date().toISOString();
			const rows = await db
				.insert(docTable)
				.values({
					id,
					versionNumber: 1,
					createdAt: now,
					title,
					content: draftContent,
					kind,
					userId: 0,
				})
				.returning();
			const row = rows[0];

			return {
				id: row?.id ?? id,
				title: row?.title ?? title,
				kind: (row?.kind as "text" | undefined) ?? kind,
				content: row?.content ?? draftContent,
			};
		},
	});
