import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useSpinDelay } from "spin-delay";

import { getOwnPracticePageFeedbackOptions } from "@/api/@tanstack/react-query.gen";
import type { FeedbackResponse, FeedbackResponseRequest, GroupRef } from "@/api/types.gen";
import { type LoadState, queryLoadState } from "@/components/common/panel-state";
import { toFeedbackCard } from "@/components/practice-profile/practice-feedback-cards";
import type { FeedbackUsefulness } from "@/components/practice-vocabulary/feedback-usefulness-defs";
import type {
	FeedbackBand,
	FeedbackRatingProps,
	PracticeFeedbackCardEntry,
	ResolvingAnswer,
	ResponseControl,
} from "@/components/practice-vocabulary/PracticeFeedbackCard";
import { useFeedbackResponseWrite } from "@/hooks/use-feedback-response-write";

export interface PracticePageFeedbackRequest {
	workspaceSlug: string;
	/** The workspace's groups; a card's colour and icon are its group's. */
	groups: GroupRef[];
	/**
	 * Whether to read the cards at all. Reading them delivers them, so a page that may yet send the
	 * reader away holds the request until it knows it will not.
	 */
	enabled?: boolean;
}

export interface PracticePageFeedbackCards {
	/** Every readable card, newest first; empty until loaded. */
	cards: PracticeFeedbackCardEntry[];
	/** The card props that wire one piece of feedback's rating and answer to the server. */
	ratingProps: (feedbackId: string) => FeedbackRatingProps;
	state: LoadState;
}

/**
 * The response a press on a rating writes: the pressed rating, keeping the resolution and the
 * comment; pressed on the rating already chosen, the rating withdrawn with its note. A disagreement
 * is not the rating's to take back, so it keeps its reason, which the server requires.
 */
export function nextRating(
	current: FeedbackResponseRequest | undefined,
	pressed: FeedbackUsefulness,
): FeedbackResponseRequest {
	const withdrawing = current?.usefulness === pressed;
	const disagreed = current?.resolution === "DISAGREE";
	return {
		usefulness: withdrawing ? undefined : pressed,
		resolution: current?.resolution,
		comment: withdrawing && !disagreed ? undefined : current?.comment,
	};
}

/**
 * Whether a rating opens its note band. A response has one comment and a disagreement holds it, so
 * a note would replace the reason that admins read.
 */
export function opensNoteBand(response: FeedbackResponseRequest): boolean {
	return response.usefulness !== undefined && response.resolution !== "DISAGREE";
}

/**
 * The response a press on Addressed or Not applicable writes: the pressed answer in place of the one
 * before, a disagreement included, keeping the rating and the comment; pressed on the answer already
 * chosen, the answer taken back and the rest kept. The server closes the card on the first and
 * reopens it on the second.
 */
export function nextResolution(
	current: FeedbackResponseRequest | undefined,
	pressed: ResolvingAnswer,
): FeedbackResponseRequest {
	return {
		usefulness: current?.usefulness,
		resolution: current?.resolution === pressed ? undefined : pressed,
		comment: current?.comment,
	};
}

/** The response Send writes: the comment added under the rating it was written for. */
export function withComment(
	current: FeedbackResponseRequest | undefined,
	comment: string,
): FeedbackResponseRequest {
	return {
		usefulness: current?.usefulness,
		resolution: current?.resolution,
		comment: comment.trim() || undefined,
	};
}

/** Send in the disagreement band: it replaces any earlier answer and keeps the rating. */
export function withDisagreement(
	current: FeedbackResponseRequest | undefined,
	comment: string,
): FeedbackResponseRequest {
	return { usefulness: current?.usefulness, resolution: "DISAGREE", comment: comment.trim() };
}

/** Disagree on a standing disagreement: takes it back with its reason and keeps the rating. */
export function withoutDisagreement(
	current: FeedbackResponseRequest | undefined,
): FeedbackResponseRequest {
	return { usefulness: current?.usefulness, resolution: undefined, comment: undefined };
}

interface OpenBand {
	feedbackId: string;
	band: FeedbackBand;
}

/** The control a write came from. The mutation cannot say: its variables are the whole response. */
interface WriteFrom {
	feedbackId: string;
	control: ResponseControl;
}

/**
 * Nothing in flight and nothing to wait for. A query held back by `enabled` reports `isPending`
 * for as long as it is held, which a page reads as a skeleton that never resolves; with the
 * reading disabled the feedback is settled and empty instead.
 */
const SETTLED_EMPTY: LoadState = { status: "ready" };

/**
 * The developer's feedback on their practice pages and the ratings on it.
 *
 * Reading the cards is what delivers them: the server flips an unread card to delivered on this
 * very GET, so a card reads as new exactly once. Each card arrives with the developer's current
 * response, so the list is the one query; a card whose response is being written says so on its
 * own rating buttons, and a refetch that adds a card never re-skeletons the list.
 *
 * Every press writes the complete response, keeping what the reader said before: "Helpful" and
 * "Not helpful" replace the usefulness, Send adds the comment, "Addressed" and "Not applicable"
 * replace the resolution, and pressing the chosen one again withdraws it. "Disagree" opens the band
 * that asks why, and its Send writes the disagreement; pressed on a standing one, it takes it back.
 *
 * The read records the first read, so only the route that shows the cards calls this: never a
 * story, never the extension. A user view reads the page and writes nothing: its route passes no
 * `ratingProps` on.
 */
export function usePracticePageFeedback({
	workspaceSlug,
	groups,
	enabled = true,
}: PracticePageFeedbackRequest): PracticePageFeedbackCards {
	const [openBand, setOpenBand] = useState<OpenBand>();
	const [lastWrite, setLastWrite] = useState<WriteFrom>();
	const feedbackQuery = useQuery({
		...getOwnPracticePageFeedbackOptions({ path: { workspaceSlug } }),
		enabled,
	});
	const feedback = feedbackQuery.data ?? [];
	const responses = useFeedbackResponseWrite(workspaceSlug);
	const { pendingResponses } = responses;
	const recordedOf = (feedbackId: string): FeedbackResponse | null =>
		feedback.find((item) => item.id === feedbackId)?.response ?? null;
	// The response a write is carrying stands over the recorded one until the refetch brings it back.
	const responseOf = (feedbackId: string) =>
		responses.responseOf(feedbackId, recordedOf(feedbackId));

	const writeFrom = (
		feedbackId: string,
		control: ResponseControl,
		response: FeedbackResponseRequest,
	) => {
		setLastWrite({ feedbackId, control });
		responses.write(feedbackId, recordedOf(feedbackId), response);
	};
	// A quick write says nothing; a slow one says "Saving…" after a second, for at least 500ms.
	// `ssr: false`: its default shows the word at once when a page mounts with a write in flight.
	const showSaving = useSpinDelay(
		lastWrite !== undefined && pendingResponses.has(lastWrite.feedbackId),
		{ delay: 1000, minDuration: 500, ssr: false },
	);

	const rate = (feedbackId: string, usefulness: FeedbackUsefulness) => {
		const next = nextRating(responseOf(feedbackId), usefulness);
		writeFrom(feedbackId, "rating", next);
		setOpenBand(opensNoteBand(next) ? { feedbackId, band: "comment" } : undefined);
	};
	const resolve = (feedbackId: string, answer: ResolvingAnswer) => {
		writeFrom(feedbackId, "answer", nextResolution(responseOf(feedbackId), answer));
	};
	const send = (feedbackId: string, comment: string) => {
		writeFrom(feedbackId, "send", withComment(responseOf(feedbackId), comment));
		setOpenBand(undefined);
	};
	const disagree = (feedbackId: string) => {
		const current = responseOf(feedbackId);
		if (current.resolution === "DISAGREE") {
			writeFrom(feedbackId, "disagree", withoutDisagreement(current));
			setOpenBand(undefined);
			return;
		}
		const open = openBand?.feedbackId === feedbackId && openBand.band === "disagreement";
		setOpenBand(open ? undefined : { feedbackId, band: "disagreement" });
	};
	const sendDisagreement = (feedbackId: string, comment: string) => {
		writeFrom(feedbackId, "send", withDisagreement(responseOf(feedbackId), comment));
		setOpenBand(undefined);
	};

	return {
		cards: feedback.map((item) => toFeedbackCard(item, groups)),
		ratingProps: (feedbackId) => ({
			usefulness: responseOf(feedbackId).usefulness,
			resolution: responseOf(feedbackId).resolution,
			openBand: openBand?.feedbackId === feedbackId ? openBand.band : undefined,
			isPending: pendingResponses.has(feedbackId),
			saving: showSaving && lastWrite?.feedbackId === feedbackId ? lastWrite.control : undefined,
			onRate: (usefulness) => rate(feedbackId, usefulness),
			onSendComment: (comment) => send(feedbackId, comment),
			onSkipComment: () => setOpenBand(undefined),
			onDisagree: () => disagree(feedbackId),
			onSendDisagreement: (comment) => sendDisagreement(feedbackId, comment),
			onResolve: (answer) => resolve(feedbackId, answer),
		}),
		state: enabled ? queryLoadState(feedbackQuery) : SETTLED_EMPTY,
	};
}
