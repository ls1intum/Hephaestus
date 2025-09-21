import { createRoute, z } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent, jsonContentRequired } from "stoker/openapi/helpers";
import {
	chatRequestBodySchema,
	streamPartSchema,
	ThreadIdParamsSchema,
	threadDetailSchema,
} from "./chat.schemas";

export const mentorChatRoute = createRoute({
	path: "/chat",
	method: "post",
	tags: ["mentor"],
	summary: "Handle mentor chat",
	request: {
		body: jsonContentRequired(chatRequestBodySchema, "Chat request body"),
	},
	responses: {
		[HttpStatusCodes.OK]: {
			description: "Event stream of chat updates.",
			content: {
				"text/event-stream": {
					schema: streamPartSchema,
				},
			},
		},
	},
});

export type HandleMentorChatRoute = typeof mentorChatRoute;

export const getThreadRoute = createRoute({
	path: "/threads/{threadId}",
	method: "get",
	tags: ["mentor"],
	summary: "Get mentor chat thread detail",
	request: {
		params: ThreadIdParamsSchema,
	},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(
			threadDetailSchema,
			"Thread detail with messages",
		),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(
			z.object({ error: z.string() }),
			"Thread not found",
		),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			z.object({ error: z.string() }),
			"Internal error",
		),
	},
});

export type HandleGetThreadRoute = typeof getThreadRoute;
