import type { UIMessage } from "ai";
import {
	convertToModelMessages,
	createUIMessageStream,
	createUIMessageStreamResponse,
	streamText,
} from "ai";
import { v4 as uuidv4 } from "uuid";
import env from "@/env";
import { mentorSystemPrompt } from "@/lib/ai/prompts";
import { createDocument as createDocumentFactory } from "@/lib/ai/tools/create-document";
import { getIssueDetails } from "@/lib/ai/tools/get-issue-details";
import { getIssues } from "@/lib/ai/tools/get-issues";
import { getPullRequestBadPractices } from "@/lib/ai/tools/get-pull-request-bad-practices";
import { getPullRequestDetails } from "@/lib/ai/tools/get-pull-request-details";
import { getPullRequests } from "@/lib/ai/tools/get-pull-requests";
import { getWeather } from "@/lib/ai/tools/get-weather";
import { updateDocument as updateDocumentFactory } from "@/lib/ai/tools/update-document";
import {
	createThread,
	getMessagesByThreadId,
	getThreadById,
	saveMessage,
	updateSelectedLeafMessageId,
	updateThreadTitle,
} from "@/lib/chat-repo";
import { ERROR_MESSAGES, HTTP_STATUS } from "@/lib/constants";
import type { AppRouteHandler } from "@/lib/types";
import type {
	HandleGetThreadRoute,
	HandleMentorChatRoute,
} from "./chat.routes";
import type { ChatRequestBody, ThreadDetail } from "./chat.schemas";

type IncomingMessage = ChatRequestBody["message"];
type IncomingPart = { type: string; [k: string]: unknown };

function partsToPersist(parts: IncomingPart[]) {
	return parts
		.filter((p) => {
			// Drop ephemeral control/data events from persistence
			if (typeof p.type === "string" && p.type.startsWith("data-"))
				return false;
			return true;
		})
		.map((p) => {
			// Persist known structured parts directly in the DB with their native shape
			if (p.type === "text" && typeof p.text === "string") {
				return {
					type: "text",
					originalType: "text",
					content: { type: "text", text: p.text },
				};
			}
			if (
				p.type === "file" &&
				typeof p.url === "string" &&
				typeof p.mediaType === "string"
			) {
				return {
					type: "file",
					originalType: "file",
					content: {
						type: "file",
						url: p.url,
						mediaType: p.mediaType,
						name: typeof p.name === "string" ? p.name : undefined,
						providerMetadata:
							"providerMetadata" in p
								? (p as Record<string, unknown>).providerMetadata
								: undefined,
					},
				};
			}
			// For reasoning and tool parts and others, store as-is
			return {
				type: p.type,
				originalType: p.type,
				content: p,
			};
		});
}

function inferTitleFromMessage(msg: IncomingMessage): string {
	const firstText = msg.parts.find(
		(p): p is { type: "text"; text: string } => p.type === "text",
	);
	const raw = (firstText?.text ?? "").trim() || "New chat";
	return raw.length > 60 ? `${raw.slice(0, 57)}...` : raw;
}

export const mentorChatHandler: AppRouteHandler<HandleMentorChatRoute> = async (
	context,
) => {
	const logger = (() => {
		const candidate = context.get("logger") as unknown;
		if (
			candidate &&
			typeof candidate === "object" &&
			"warn" in (candidate as object) &&
			"error" in (candidate as object) &&
			typeof (candidate as { warn?: unknown }).warn === "function" &&
			typeof (candidate as { error?: unknown }).error === "function"
		) {
			return candidate as {
				error: (obj?: unknown, msg?: string) => void;
				warn: (obj?: unknown, msg?: string) => void;
			};
		}
		return { error: () => {}, warn: () => {} };
	})();

	// Hono's c.req.valid() handles validation via the route schema.
	// If validation fails, Hono returns a 400 automatically via defaultHook.
	const requestBody = context.req.valid("json") as ChatRequestBody;

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
			// If client didn't send a valid previousMessageId, we let DB derive via selectedLeaf later on fetch
			parentMessageId: previousMessageId || null,
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
	type UMessage = UIMessage<
		Record<string, never>,
		Record<string, never>,
		Record<string, never>
	>;

	const historyU: UMessage[] = history.map((m) => {
		const parts: Array<UTextPart | UFilePart> = [];
		for (const p of m.parts) {
			if (p.type === "text") {
				const c = p.content as { text?: string };
				if (typeof c?.text === "string")
					parts.push({ type: "text", text: c.text });
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
		return { id: m.id, role: m.role, parts } as UMessage;
	});

	const currentU: UMessage = {
		id: message.id,
		role: "user",
		parts: message.parts.map((p) =>
			p.type === "text"
				? ({ type: "text", text: p.text } as UTextPart)
				: ({
						type: "file",
						url: p.url,
						mediaType: p.mediaType,
						name: p.name,
					} as UFilePart),
		),
	};

	const uiMessages: UMessage[] = [...historyU, currentU];

	const modelMessages = convertToModelMessages(uiMessages);

	const stream = createUIMessageStream({
		async execute({ writer }) {
			// Diagnostics: log model presence in non-production
			if (process.env.NODE_ENV !== "production" && env.defaultModel) {
				console.log("[mentor] model configured");
			}
			try {
				const result = streamText({
					model: env.defaultModel,
					system: mentorSystemPrompt,
					messages: modelMessages,
					tools: {
						getWeather,
						createDocument: createDocumentFactory({ dataStream: writer }),
						updateDocument: updateDocumentFactory({ dataStream: writer }),
						getIssues,
						getPullRequests,
						getIssueDetails,
						getPullRequestDetails,
						getPullRequestBadPractices,
					},
					toolChoice: "auto",
				});

				writer.merge(
					result.toUIMessageStream({
						sendReasoning: true,
						onFinish: async ({ responseMessage }) => {
							try {
								// Ensure assistant message has a valid UUID (AI SDK may omit IDs)
								const assistantId =
									responseMessage?.id &&
									typeof responseMessage.id === "string" &&
									responseMessage.id.trim().length > 0
										? responseMessage.id
										: uuidv4();
								const hasParts = (
									val: unknown,
								): val is { parts: IncomingPart[] } =>
									!!val &&
									typeof val === "object" &&
									Array.isArray((val as Record<string, unknown>).parts);
								// Persist assistant message
								await saveMessage({
									id: assistantId,
									role: "assistant",
									threadId,
									parts: hasParts(responseMessage)
										? partsToPersist(responseMessage.parts)
										: [],
									parentMessageId: message.id,
									createdAt: new Date(),
								});
								await updateSelectedLeafMessageId(threadId, assistantId);
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
							// Always log errors in development/test to aid debugging
							try {
								console.error("[mentor] stream error:", error);
							} catch {}
							logger.error({ err: error }, "Mentor response streaming error");
							return error instanceof Error
								? error.message
								: "An error occurred";
						},
					}),
				);

				await result.consumeStream();
			} catch (err) {
				try {
					console.error("[mentor] execute() failed:", err);
				} catch {}
				throw err;
			}
		},
		onError: (e) => {
			// Surface the internal error message in non-production to speed up debugging
			const isProd = process.env.NODE_ENV === "production";
			const msg = e instanceof Error ? e.message : "Oops, an error occurred!";
			return isProd ? "Oops, an error occurred!" : msg;
		},
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
				error: (obj?: unknown, msg?: string) => void;
			};
			if (l && typeof l.warn === "function" && typeof l.error === "function")
				return l;
		} catch {}
		return { warn: () => {}, error: () => {} } as const;
	})();
	const { threadId } = context.req.valid("param");

	try {
		const thread = await getThreadById(threadId);
		if (!thread) {
			return context.json(
				{ error: ERROR_MESSAGES.THREAD_NOT_FOUND },
				{ status: HTTP_STATUS.NOT_FOUND },
			);
		}

		const history = await getMessagesByThreadId(threadId);
		const messages = history.map((m) => {
			// Return parts as stored (already structured UIMessage parts),
			// but coerce to schema-compatible shapes for strict types.
			const rawParts = m.parts.map((p) => p.content).filter((c) => c != null);
			const parts = rawParts.map((c) => {
				if (!c || typeof c !== "object") {
					return { type: "data-unknown", value: c };
				}
				const part = c as { type?: string; [k: string]: unknown };
				if (typeof part.type !== "string") {
					return { type: "data-unknown", value: part };
				}
				if (part.type === "file") {
					const mediaType = part.mediaType as string | undefined;
					const url = part.url as string | undefined;
					const name = part.name as string | undefined;
					if (
						url &&
						(mediaType === "image/jpeg" || mediaType === "image/png")
					) {
						return { type: "file", url, mediaType, name };
					}
					// Preserve unsupported file types under generic data envelope
					return { type: "data-file", file: part };
				}
				// Pass-through for text, reasoning, tool-*, source-* and others
				return part;
			}) as ThreadDetail["messages"][number]["parts"];

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
			{ status: HTTP_STATUS.OK },
		);
	} catch (err) {
		logger.error({ err }, "Database error while fetching thread");
		return context.json(
			{ error: ERROR_MESSAGES.SERVICE_UNAVAILABLE },
			{ status: HTTP_STATUS.SERVICE_UNAVAILABLE },
		);
	}
};
