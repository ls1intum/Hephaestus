import {
	smoothStream,
	streamText,
	tool,
	type UIMessage,
	type UIMessageStreamWriter,
} from "ai";
import { desc, eq } from "drizzle-orm";
import { z } from "zod";
import db from "../../../db";
import { document as docTable } from "../../../db/schema";
import env from "../../../env";

const inputSchema = z.object({
	id: z.string().uuid(),
	description: z.string(),
});

type Input = z.infer<typeof inputSchema>;

export const updateDocument = ({
	dataStream,
}: {
	dataStream: UIMessageStreamWriter<UIMessage>;
}) =>
	tool({
		description:
			"Update an existing document. Emits streaming UI data for the client.",
		inputSchema,
		execute: async ({ id, description }: Input) => {
			dataStream.write({
				type: "data-document-update",
				data: { id, kind: "text" },
				transient: true,
			});

			// Get the latest stored document content
			const latestRow = (
				await db
					.select()
					.from(docTable)
					.where(eq(docTable.id, id))
					.orderBy(desc(docTable.versionNumber))
					.limit(1)
			)[0];
			const currentContent = latestRow?.content ?? "";

			// Stream an updated draft using the model, similar to ai-chatbot textDocumentHandler
			let draftContent = "";
			try {
				const { fullStream } = streamText({
					model: env.defaultModel,
					system: `You are updating the following document. Keep structure, improve clarity, apply the described changes.\n\nCurrent content:\n\n${currentContent}`,
					experimental_transform: smoothStream({ chunking: "word" }),
					prompt: description ?? "",
					providerOptions: {
						openai: {
							prediction: { type: "content", content: currentContent },
						},
					},
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

			// Persist new version
			const latest = (
				await db
					.select()
					.from(docTable)
					.where(eq(docTable.id, id))
					.orderBy(desc(docTable.versionNumber))
					.limit(1)
			)[0];

			const now = new Date().toISOString();
			const nextVersion = (latest?.versionNumber ?? 0) + 1;
			const rows = await db
				.insert(docTable)
				.values({
					id,
					versionNumber: nextVersion,
					createdAt: now,
					title: latest?.title ?? "",
					content: draftContent || currentContent,
					kind: (latest?.kind as "text" | undefined) ?? "text",
					userId: latest?.userId ?? 0,
				})
				.returning();
			const row = rows[0];

			return {
				id,
				title: row?.title ?? latest?.title ?? "",
				content: (row?.content ?? draftContent) || currentContent,
				kind:
					(row?.kind as "text" | undefined) ??
					(latest?.kind as "text") ??
					"text",
				description,
			};
		},
	});
