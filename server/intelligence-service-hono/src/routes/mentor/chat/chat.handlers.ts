import type { UIMessage } from "ai";
import {
	convertToCoreMessages,
	createUIMessageStream,
	createUIMessageStreamResponse,
	streamText,
} from "ai";
import env from "@/env";
import { mentorSystemPrompt } from "@/lib/ai/prompts";
import { getWeather } from "@/lib/ai/tools/get-weather";
import {
	createThread,
	getMessagesByThreadId,
	getThreadById,
	saveMessage,
	updateSelectedLeafMessageId,
	updateThreadTitle,
} from "@/lib/chat-repo";
import type { AppRouteHandler } from "@/lib/types";
import type {
	HandleGetThreadRoute,
	HandleMentorChatRoute,
} from "./chat.routes";
import type { ChatRequestBody } from "./chat.schemas";
import { chatRequestBodySchema } from "./chat.schemas";

type IncomingMessage = ChatRequestBody["message"];
type IncomingPart = IncomingMessage["parts"][number];

function partsToPersist(parts: IncomingPart[]) {
	return parts.map((p) => {
		if (p.type === "text") {
			return { type: "text", originalType: "text", content: { text: p.text } };
		}
		return {
			type: "file",
			originalType: "file",
			content: { url: p.url, mediaType: p.mediaType, name: p.name },
		};
	});
}

function inferTitleFromMessage(msg: IncomingMessage): string {
	const firstText = msg.parts.find((p) => p.type === "text") as
		| { type: "text"; text: string }
		| undefined;
	const raw = firstText?.text?.trim() ?? "New chat";
	return raw.length > 60 ? `${raw.slice(0, 57)}...` : raw;
}

export const mentorChatHandler: AppRouteHandler<HandleMentorChatRoute> = async (
	context,
) => {
	const logger = (() => {
		try {
			const l = context.get("logger") as unknown as {
				error: (obj?: unknown, msg?: string) => void;
				warn: (obj?: unknown, msg?: string) => void;
			};
			if (l && typeof l.warn === "function" && typeof l.error === "function")
				return l;
		} catch {}
		return { error: () => {}, warn: () => {} };
	})();
	const reqJson = context.req.valid("json");

	let requestBody: ChatRequestBody;
	try {
		requestBody = chatRequestBodySchema.parse(reqJson);
	} catch (_) {
		return context.json({ error: "Invalid request body" }, { status: 400 });
	}

	const { id: threadId, message, previousMessageId } = requestBody;

	// Load or create thread (best-effort: don't fail the request if DB isn't ready)
	let thread = null as Awaited<ReturnType<typeof getThreadById>>;
	try {
		thread = await getThreadById(threadId);
		if (!thread) {
			thread = await createThread({
				id: threadId,
				title: inferTitleFromMessage(message),
			});
		}
	} catch (err) {
		logger.warn({ err }, "Proceeding without thread persistence");
		thread = null;
	}

	// Persist user message (best-effort)
	try {
		await saveMessage({
			id: message.id,
			role: "user",
			threadId,
			parts: partsToPersist(message.parts),
			parentMessageId: previousMessageId ?? null,
			createdAt: new Date(),
		});
	} catch (err) {
		logger.warn({ err }, "Proceeding without user message persistence");
	}

	// Build UI messages from history + current
	const history = await getMessagesByThreadId(threadId);
	type UTextPart = { type: "text"; text: string };
	type UFilePart = {
		type: "file";
		url: string;
		mediaType: "image/jpeg" | "image/png";
		name?: string;
	};
	type UMessage = {
		id: string;
		role: "system" | "user" | "assistant";
		parts: Array<UTextPart | UFilePart>;
	};

	const uiMessages: UMessage[] = [
		...(history.map((m) => ({
			id: m.id,
			role: m.role,
			parts: m.parts
				.map((p) => {
					if (p.type === "text") {
						const c = p.content as { text?: string };
						if (typeof c?.text === "string") {
							return { type: "text", text: c.text } as UTextPart;
						}
					}
					if (p.type === "file") {
						const c = p.content as {
							url?: string;
							mediaType?: string;
							name?: string;
						};
						if (
							c?.url &&
							(c.mediaType === "image/jpeg" || c.mediaType === "image/png")
						) {
							return {
								type: "file",
								url: c.url,
								mediaType: c.mediaType,
								name: c.name,
							} as UFilePart;
						}
					}
					return null;
				})
				.filter(Boolean),
		})) as UMessage[]),
		message as unknown as UMessage,
	];

	type MinimalUIMessage = UIMessage<
		Record<string, never>,
		Record<string, never>,
		Record<string, never>
	>;
	const coreMessagesForModel = convertToCoreMessages(
		uiMessages as unknown as MinimalUIMessage[],
	);

	const stream = createUIMessageStream({
		async execute({ writer }) {
			const result = streamText({
				model: env.defaultModel,
				system: mentorSystemPrompt,
				messages: coreMessagesForModel,
				tools: { getWeather },
				toolChoice: "auto",
			});

			writer.merge(
				result.toUIMessageStream({
					onFinish: async ({ responseMessage }) => {
						try {
							// Persist assistant message
							await saveMessage({
								id: responseMessage.id,
								role: "assistant",
								threadId,
								parts: partsToPersist(
									(responseMessage as unknown as IncomingMessage).parts,
								),
								parentMessageId: message.id,
								createdAt: new Date(),
							});
							await updateSelectedLeafMessageId(threadId, responseMessage.id);
							if (!thread?.title) {
								const title = inferTitleFromMessage(message);
								await updateThreadTitle(threadId, title);
							}
						} catch (err) {
							logger.warn(
								{ err },
								"Proceeding without assistant message persistence",
							);
						}
					},
					onError: (error) => {
						logger.error({ err: error }, "Mentor response streaming error");
						return error instanceof Error ? error.message : "An error occurred";
					},
				}),
			);

			await result.consumeStream();
		},
		onError: () => "Oops, an error occurred!",
	});

	return createUIMessageStreamResponse({ stream });
};

export const getThreadHandler: AppRouteHandler<HandleGetThreadRoute> = async (
	context,
) => {
	const logger = (() => {
		try {
			const l = context.get("logger") as unknown as {
				warn: (obj?: unknown, msg?: string) => void;
			};
			if (l && typeof l.warn === "function") return l;
		} catch {}
		return { warn: () => {} } as const;
	})();
	const { threadId } = context.req.valid("param");

	try {
		const thread = await getThreadById(threadId);
		if (!thread) {
			return context.json({ error: "Thread not found" }, { status: 404 });
		}

		const history = await getMessagesByThreadId(threadId);
		const messages = history.map((m) => {
			const parts: Array<
				| { type: "text"; text: string }
				| {
						type: "file";
						url: string;
						mediaType: "image/jpeg" | "image/png";
						name?: string;
				  }
			> = [];
			for (const p of m.parts) {
				if (p.type === "text") {
					const c = p.content as { text?: string };
					if (typeof c?.text === "string") {
						parts.push({ type: "text", text: c.text });
					}
				} else if (p.type === "file") {
					const c = p.content as {
						url?: string;
						mediaType?: string;
						name?: string;
					};
					if (
						c?.url &&
						(c.mediaType === "image/jpeg" || c.mediaType === "image/png")
					) {
						parts.push({
							type: "file",
							url: c.url,
							mediaType: c.mediaType,
							name: c.name,
						});
					}
				}
			}
			return {
				id: m.id,
				role: m.role,
				parts,
				createdAt: m.createdAt.toISOString(),
				parentMessageId: m.parentMessageId ?? null,
			};
		});

		return context.json(
			{
				id: thread.id,
				title: thread.title ?? null,
				selectedLeafMessageId: thread.selectedLeafMessageId ?? null,
				messages,
			},
			{ status: 200 },
		);
	} catch (err) {
		logger.warn(
			{ err },
			"DB unavailable while fetching thread; returning empty",
		);
		return context.json(
			{
				id: threadId,
				title: null,
				selectedLeafMessageId: null,
				messages: [],
			},
			{ status: 200 },
		);
	}
};
