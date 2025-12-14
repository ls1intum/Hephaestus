import { z } from "zod";

export const VoteMessageParamsSchema = z
	.object({ messageId: z.string().uuid() })
	.openapi("VoteMessageParams");

export type VoteMessageParams = z.infer<typeof VoteMessageParamsSchema>;

export const VoteMessageRequestSchema = z
	.object({ isUpvoted: z.boolean() })
	.openapi("VoteMessageRequest");

export type VoteMessageRequest = z.infer<typeof VoteMessageRequestSchema>;

export const ChatMessageVoteSchema = z
	.object({
		messageId: z.string().uuid(),
		isUpvoted: z.boolean(),
		createdAt: z.string().datetime(),
		updatedAt: z.string().datetime(),
	})
	.openapi("ChatMessageVote");

export type ChatMessageVote = z.infer<typeof ChatMessageVoteSchema>;
