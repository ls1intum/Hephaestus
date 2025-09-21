import { createRoute, z } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent, jsonContentRequired } from "stoker/openapi/helpers";
import {
	ChatMessageVoteSchema,
	VoteMessageParamsSchema,
	VoteMessageRequestSchema,
} from "./vote.schemas";

export const voteMessageRoute = createRoute({
	path: "/chat/messages/{messageId}/vote",
	method: "post",
	tags: ["vote"],
	summary: "Vote on a chat message (upvote/downvote)",
	request: {
		params: VoteMessageParamsSchema,
		body: jsonContentRequired(VoteMessageRequestSchema, "Vote request body"),
	},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(ChatMessageVoteSchema, "Vote recorded"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(
			z.object({ error: z.string() }),
			"Message not found",
		),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			z.object({ error: z.string() }),
			"Internal error",
		),
	},
});

export type HandleVoteMessageRoute = typeof voteMessageRoute;
