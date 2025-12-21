import { createRoute } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContentRequired } from "stoker/openapi/helpers";
import { EXPORTED_TAG } from "@/shared/http/exported-tag";
import { chatRequestBodySchema, streamPartSchema } from "./chat.schema";

export const mentorChatRoute = createRoute({
	path: "/chat",
	method: "post",
	tags: ["mentor", ...EXPORTED_TAG],
	summary: "Handle mentor chat (set greeting=true for initial greeting without user message)",
	operationId: "mentorChat",
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
