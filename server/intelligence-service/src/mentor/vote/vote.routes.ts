import { createRoute, z } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent, jsonContentRequired } from "stoker/openapi/helpers";
import { EXPORTED_TAG } from "@/shared/http/exported-tag";
import {
	ChatMessageVoteSchema,
	VoteMessageParamsSchema,
	VoteMessageRequestSchema,
} from "./vote.schema";

export const voteMessageRoute = createRoute({
	path: "/chat/messages/{messageId}/vote",
	method: "post",
	tags: ["vote", ...EXPORTED_TAG],
	summary: "Vote on a chat message (upvote/downvote)",
	operationId: "voteMessage",
	request: {
		params: VoteMessageParamsSchema,
		body: jsonContentRequired(VoteMessageRequestSchema, "Vote request body"),
	},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(ChatMessageVoteSchema, "Vote recorded"),
		[HttpStatusCodes.BAD_REQUEST]: jsonContent(z.object({ error: z.string() }), "Missing context"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(z.object({ error: z.string() }), "Message not found"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			z.object({ error: z.string() }),
			"Internal error",
		),
	},
});

export type HandleVoteMessageRoute = typeof voteMessageRoute;
