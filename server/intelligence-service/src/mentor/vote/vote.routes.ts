import { createRoute } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent, jsonContentRequired } from "stoker/openapi/helpers";
import { EXPORTED_TAG } from "@/shared/http/exported-tag";
import { ErrorResponseSchema } from "@/shared/http/schemas";
import {
	ChatMessageVoteSchema,
	VoteMessageParamsSchema,
	VoteMessageRequestSchema,
} from "./vote.schema";

export const voteMessageRoute = createRoute({
	path: "/{messageId}/vote",
	method: "put",
	tags: ["vote", ...EXPORTED_TAG],
	summary: "Vote on a chat message (upvote/downvote) - idempotent upsert",
	operationId: "voteMessage",
	request: {
		params: VoteMessageParamsSchema,
		body: jsonContentRequired(VoteMessageRequestSchema, "Vote request body"),
	},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(ChatMessageVoteSchema, "Vote recorded"),
		[HttpStatusCodes.BAD_REQUEST]: jsonContent(ErrorResponseSchema, "Missing context"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(ErrorResponseSchema, "Message not found"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(ErrorResponseSchema, "Internal error"),
	},
});

export type HandleVoteMessageRoute = typeof voteMessageRoute;
