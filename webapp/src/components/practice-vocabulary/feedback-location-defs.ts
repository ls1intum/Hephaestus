import type { ReviewFeedback, ReviewedWorkRef } from "@/api/types.gen";
import { HephMarkIcon } from "@/components/brand/HephIcon";
import type { StatusDefs } from "@/components/common/status-def";
import { GitHubIcon, GitLabIcon, SlackIcon } from "@/components/icons/brand";
import { reviewedWorkName } from "@/lib/artifact-kinds";

import { DELIVERY_PLACE_DEFS } from "./delivery-place-defs";

export type FeedbackLocation = ReviewFeedback["location"];

/**
 * **Where** a piece of feedback appears, by the provider that shows it: its channel split where the
 * record names the provider (`docs/contributor/practice-feedback-language.md`, location). It answers
 * the same question as `DELIVERY_PLACE_DEFS`, more precisely, so a row shows one of the two, never
 * both. The three channel-wide entries stand where no provider is known.
 *
 * <p>The labels are the facet's words and the badge's. The sentence a row and the drawer show is
 * {@link feedbackLocationSentence}.
 */
export const FEEDBACK_LOCATION_DEFS: StatusDefs<FeedbackLocation> = {
	GITHUB: {
		label: "On GitHub",
		icon: GitHubIcon,
		badgeVariant: "outline",
		description: "Posted as a comment on the pull request or issue at GitHub.",
	},
	GITLAB: {
		label: "On GitLab",
		icon: GitLabIcon,
		badgeVariant: "outline",
		description: "Posted as a comment on the merge request or issue at GitLab.",
	},
	OTHER_WORK: {
		// The channel's own word and mark, so the Channel filter and the channel tag agree.
		label: DELIVERY_PLACE_DEFS.IN_CONTEXT.label,
		icon: DELIVERY_PLACE_DEFS.IN_CONTEXT.icon,
		badgeVariant: "outline",
		description:
			"Posted on the work the feedback is about, at a provider the record does not name as GitHub or GitLab.",
	},
	HEPH: {
		label: "With Heph in the app",
		icon: HephMarkIcon,
		badgeVariant: "outline",
		description: "Raised in a turn of the developer’s conversation with Heph in Hephaestus.",
	},
	SLACK: {
		label: "With Heph in Slack",
		icon: SlackIcon,
		badgeVariant: "outline",
		description:
			"Raised in a turn of the developer’s conversation with Heph in a Slack direct message.",
	},
	NEXT_CONVERSATION: {
		// The channel's own word and mark, so the Channel filter and the channel tag agree.
		label: DELIVERY_PLACE_DEFS.IN_CHAT.label,
		icon: DELIVERY_PLACE_DEFS.IN_CHAT.icon,
		badgeVariant: "outline",
		description:
			"For the developer’s conversation with Heph. No turn raised it yet, or the turn that raised it is gone.",
	},
	PRACTICE_PAGES: {
		// The channel's own word and mark, so the Channel filter and the channel tag agree.
		label: DELIVERY_PLACE_DEFS.IN_APP.label,
		icon: DELIVERY_PLACE_DEFS.IN_APP.icon,
		badgeVariant: "outline",
		description: "A card on the developer’s own practice pages, which only they can read.",
	},
};

/**
 * The location in words, where a reader asks where one piece of feedback went: "Comment on GitHub
 * pull request #12", "Conversation with Heph in Slack", "Card on their practice pages". The work's
 * own noun comes from the provider, so a GitLab one reads "merge request !425".
 */
export function feedbackLocationSentence(
	location: FeedbackLocation,
	reviewedWork: Pick<ReviewedWorkRef, "kind" | "provider" | "label"> | undefined,
): string {
	const work = reviewedWork ? reviewedWorkName(reviewedWork) : undefined;
	switch (location) {
		case "GITHUB":
		case "GITLAB": {
			const provider = location === "GITHUB" ? "GitHub" : "GitLab";
			return work === undefined
				? `Comment on ${provider}`
				: `Comment on ${provider} ${lowerFirst(work)}`;
		}
		case "OTHER_WORK": {
			return work === undefined ? "Comment on the work" : `Comment on ${lowerFirst(work)}`;
		}
		case "HEPH": {
			return "Conversation with Heph in the app";
		}
		case "SLACK": {
			return "Conversation with Heph in Slack";
		}
		case "NEXT_CONVERSATION": {
			return "For a conversation with Heph";
		}
		case "PRACTICE_PAGES": {
			return "Card on their practice pages";
		}
	}
}

/** "Pull request #12" reads "pull request #12" mid-sentence; a label after the noun keeps its case. */
function lowerFirst(value: string): string {
	return value.charAt(0).toLowerCase() + value.slice(1);
}
