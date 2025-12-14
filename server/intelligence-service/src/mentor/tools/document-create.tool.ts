import { smoothStream, streamText, tool, type UIMessage, type UIMessageStreamWriter } from "ai";
import { z } from "zod";
import env from "@/env";
import { createDocument as createDocumentInDb } from "@/mentor/documents/data";
import { type DocumentKind, DocumentKindEnum } from "@/shared/document";
import { toolCallIdToUuid } from "@/shared/tool-call-id";

const inputSchema = z.object({
	title: z.string().min(1).max(255),
	kind: DocumentKindEnum,
});

type Input = z.infer<typeof inputSchema>;

export const createDocument = ({ dataStream }: { dataStream: UIMessageStreamWriter<UIMessage> }) =>
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

			const doc = await createDocumentInDb({
				id,
				title,
				content: draftContent,
				kind: kind as DocumentKind,
			});

			return {
				id: doc?.id ?? id,
				title: doc?.title ?? title,
				kind: doc?.kind ?? kind,
				content: doc?.content ?? draftContent,
			};
		},
	});
