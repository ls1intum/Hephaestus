import {
	BadgeCheckIcon,
	CircleSlashIcon,
	MessageSquareIcon,
	MessageSquareWarningIcon,
} from "lucide-react";

import type { ResultMark } from "@/api/types.gen";

import type { StatusDef, StatusDefs } from "@/components/common/status-def";

export type ResultMarkKind = ResultMark["kind"];

/**
 * A workspace admin's assessment of one result. A mark is not a developer response: the
 * developer's disagreement lives on the feedback. Accepted never overrides an active Incorrect
 * mark. `note` is what a count of marked results does and does not say.
 */
export const RESULT_MARK_DEFS: Record<ResultMarkKind, StatusDef & { note: string }> = {
	INCORRECT: {
		label: "Marked incorrect",
		icon: CircleSlashIcon,
		badgeVariant: "destructive",
		description:
			"A workspace admin marked this result incorrect. Its pending feedback stops and posted comments on the work are retracted.",
		note: "Only results an admin found wrong are marked. An unmarked one is not confirmed correct.",
	},
	ACCEPTED: {
		label: "Marked accepted",
		icon: BadgeCheckIcon,
		badgeVariant: "success",
		description:
			"A workspace admin accepted this result. It records the review’s quality and changes no feedback.",
		note: "Accepted results leave the open lists. Acceptance does not change any outcome.",
	},
};

/** The action that creates each mark, as a button names it. */
export const RESULT_MARK_ACTIONS: Record<ResultMarkKind, string> = {
	INCORRECT: "Mark incorrect",
	ACCEPTED: "Mark accepted",
};

/** The action that reverses a mark. */
export const REMOVE_MARK_ACTION = "Remove mark";

/** Whether the developer disagrees with feedback written from a result, as admins filter it. */
export type Disagreement = "DISAGREED" | "NOT_DISAGREED";

export const DISAGREEMENT_DEFS: StatusDefs<Disagreement> = {
	DISAGREED: {
		label: "Disagreed",
		icon: MessageSquareWarningIcon,
		badgeVariant: "warning",
		description:
			"The developer disagrees with feedback written from this result and explained why. Mark the result incorrect or withdraw the feedback if they are right.",
	},
	NOT_DISAGREED: {
		label: "No disagreement",
		icon: MessageSquareIcon,
		badgeVariant: "outline",
		description: "The developer disagrees with no feedback written from this result.",
	},
};
