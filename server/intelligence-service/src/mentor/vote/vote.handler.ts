import { ERROR_MESSAGES, HTTP_STATUS } from "@/shared/constants";
import type { AppRouteHandler } from "@/shared/http/types";
import { extractErrorMessage, getLogger } from "@/shared/utils";
import { messageExists, upsertVote } from "./data";
import type { HandleVoteMessageRoute } from "./vote.routes";

export const voteMessageHandler: AppRouteHandler<HandleVoteMessageRoute> = async (c) => {
	const logger = getLogger(c);
	const { messageId } = c.req.valid("param");
	const { isUpvoted } = c.req.valid("json");

	// Check message exists (guard clause)
	const exists = await messageExists(messageId);
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
