import { smoothStream, streamText, tool, type UIMessage, type UIMessageStreamWriter } from "ai";
import { desc, eq } from "drizzle-orm";
import { z } from "zod";
import env from "@/env";
import db from "@/shared/db";
import { document as docTable } from "@/shared/db/schema";

const inputSchema = z.object({
	id: z.string().uuid(),
	description: z.string(),
});

type Input = z.infer<typeof inputSchema>;

type DocumentRow = typeof docTable.$inferSelect;

async function getLatestDocumentRow(id: string): Promise<DocumentRow | undefined> {
	return (
		await db
			.select()
			.from(docTable)
			.where(eq(docTable.id, id))
			.orderBy(desc(docTable.versionNumber))
			.limit(1)
	)[0];
}

async function streamUpdatedDraft(params: {
	id: string;
	currentContent: string;
	description: string;
	dataStream: UIMessageStreamWriter<UIMessage>;
}): Promise<string> {
	const { id, currentContent, description, dataStream } = params;
	let draftContent = "";

	const { fullStream } = streamText({
		model: env.defaultModel,
		system: `You are updating the following document. Keep structure, improve clarity, apply the described changes.\n\nCurrent content:\n\n${currentContent}`,
		experimental_transform: smoothStream({ chunking: "word" }),
		prompt: description,
		providerOptions: {
			openai: {
				prediction: { type: "content", content: currentContent },
			},
		},
	});

	for await (const delta of fullStream) {
		if (delta.type !== "text-delta") {
			continue;
		}
		const text = (delta as { type: string; text?: string }).text ?? "";
		if (!text) {
			continue;
		}
		draftContent += text;
		dataStream.write({
			type: "data-document-delta",
			data: { id, kind: "text", delta: text },
			transient: true,
		});
	}

	return draftContent;
}

async function persistNextVersion(params: {
	id: string;
	base: DocumentRow | undefined;
	content: string;
}): Promise<DocumentRow | undefined> {
	const { id, base, content } = params;
	const now = new Date().toISOString();
	const nextVersion = (base?.versionNumber ?? 0) + 1;

	const rows = await db
		.insert(docTable)
		.values({
			id,
			versionNumber: nextVersion,
			createdAt: now,
			title: base?.title ?? "",
			content,
			kind: (base?.kind as "text" | undefined) ?? "text",
			userId: base?.userId ?? 0,
		})
		.returning();

	return rows[0];
}

export const updateDocument = ({ dataStream }: { dataStream: UIMessageStreamWriter<UIMessage> }) =>
	tool({
		description: "Update an existing document. Emits streaming UI data for the client.",
		inputSchema,
		execute: async ({ id, description }: Input) => {
			dataStream.write({
				type: "data-document-update",
				data: { id, kind: "text" },
				transient: true,
			});

			const latestRow = await getLatestDocumentRow(id);
			const currentContent = latestRow?.content ?? "";

			let draftContent = "";
			try {
				draftContent = await streamUpdatedDraft({
					id,
					currentContent,
					description,
					dataStream,
				});
			} finally {
				dataStream.write({
					type: "data-document-finish",
					data: { id, kind: "text" },
					transient: true,
				});
			}

			const contentToPersist = draftContent || currentContent;
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
