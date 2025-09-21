import { createRoute } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";

import { chatRequestBodySchema, streamPartSchema } from "./mentor.schemas";

export const mentorChatRoute = createRoute({
	path: "/mentor/chat",
	method: "post",
	tags: ["mentor"],
	summary: "Handle mentor chat",
	request: {
		body: {
			content: {
				"application/json": {
					schema: chatRequestBodySchema,
				},
			},
			required: true,
		},
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
