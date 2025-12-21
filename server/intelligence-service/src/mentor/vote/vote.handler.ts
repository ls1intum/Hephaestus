import { ERROR_MESSAGES, HTTP_STATUS } from "@/shared/constants";
import type { AppRouteHandler } from "@/shared/http/types";
import { getValidatedContext } from "@/shared/http/workspace-context";
import { extractErrorMessage, getLogger } from "@/shared/utils";
import { messageExistsForUser, upsertVote } from "./data";
import type { HandleVoteMessageRoute } from "./vote.routes";

export const voteMessageHandler: AppRouteHandler<HandleVoteMessageRoute> = async (c) => {
	const logger = getLogger(c);
	const { messageId } = c.req.valid("param");
	const { isUpvoted } = c.req.valid("json");

	const ctx = getValidatedContext(c);
	if (!ctx) {
		return c.json({ error: ERROR_MESSAGES.MISSING_CONTEXT }, { status: HTTP_STATUS.BAD_REQUEST });
	}

	// Check message exists AND belongs to this user's thread
	const exists = await messageExistsForUser(messageId, ctx.userId, ctx.workspaceId);
	if (!exists) {
		return c.json({ error: ERROR_MESSAGES.MESSAGE_NOT_FOUND }, { status: HTTP_STATUS.NOT_FOUND });
	}

	try {
		const vote = await upsertVote(messageId, isUpvoted);

		if (!vote) {
			return c.json(
				{ error: ERROR_MESSAGES.VOTE_RETRIEVAL_FAILED },
				{ status: HTTP_STATUS.INTERNAL_SERVER_ERROR },
			);
		}

		return c.json(vote, { status: HTTP_STATUS.OK });
	} catch (err) {
		logger.error({ err: extractErrorMessage(err) }, "Vote message failed");
		return c.json(
			{ error: ERROR_MESSAGES.INTERNAL_ERROR },
			{ status: HTTP_STATUS.INTERNAL_SERVER_ERROR },
		);
	}
};
