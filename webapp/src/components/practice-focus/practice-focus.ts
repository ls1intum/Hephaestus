import type { HistoryState } from "@tanstack/react-router";

import {
	type DetailStackEntry,
	detailStackKey,
} from "@/components/layout/detail-drawer/detail-stack";
import { hasText } from "@/lib/text";

/**
 * The practice group, and the practice in it, that the reader is looking at, on either of the
 * two pages that show practices: their own Practice profile and Practices across the workspace.
 * Without a group, the focus is every group: the profile's list of all practice groups and the
 * workspace page itself.
 *
 * Neither page stores it. Each reads it off its own address (the profile from its detail stack, the
 * workspace page from its group level and the practice it marks), so the address stays the one
 * source of truth, and a switch to the other page writes the same focus into that page's address.
 */
export interface PracticeFocus {
	/** Absent for every group. */
	groupSlug?: string;
	/** Only inside a group. */
	practiceSlug?: string;
}

/** Every practice group: the profile's list of them, and the workspace page with no level open. */
export const ALL_GROUPS_FOCUS: PracticeFocus = {};

const ALL_GROUPS_LEVEL: DetailStackEntry = { kind: "practice-groups", id: "all" };

/**
 * The focus of the profile's front level: its practice level and the group under it, or its group
 * level alone. A practice opened without its group (from a review, say) finds its group through
 * `groupOf`; a practice in no group has no counterpart, since the workspace page lists practices
 * only inside their group. The list of every group is the focus on every group; the reviews and one
 * review have none.
 */
export function focusOfProfileStack(
	stack: readonly DetailStackEntry[],
	groupOf: (practiceSlug: string) => string | undefined,
): PracticeFocus | undefined {
	const front = stack.at(-1);
	if (front?.kind === ALL_GROUPS_LEVEL.kind) {
		return ALL_GROUPS_FOCUS;
	}
	if (front?.kind === "practice-group") {
		return { groupSlug: front.id };
	}
	if (front?.kind !== "practice") {
		return undefined;
	}
	const under = stack.at(-2);
	const groupSlug = under?.kind === "practice-group" ? under.id : groupOf(front.id);
	return hasText(groupSlug) ? { groupSlug, practiceSlug: front.id } : undefined;
}

/**
 * The profile's levels for a focus: the group, and the practice over it, or the list of every group.
 * Never deeper than two.
 */
export function profileStackOf(focus: PracticeFocus): DetailStackEntry[] {
	if (!hasText(focus.groupSlug)) {
		return [ALL_GROUPS_LEVEL];
	}
	return [
		{ kind: "practice-group", id: focus.groupSlug },
		...(hasText(focus.practiceSlug) ? [{ kind: "practice", id: focus.practiceSlug }] : []),
	];
}

/**
 * The workspace page's search for a focus: its group level, and the practice row to mark, or no
 * level at all for every group.
 */
export function workspaceSearchOf(focus: PracticeFocus): {
	detail: string[] | undefined;
	practice: string | undefined;
} {
	if (!hasText(focus.groupSlug)) {
		return { detail: undefined, practice: undefined };
	}
	return {
		detail: [detailStackKey({ kind: "practice-group", id: focus.groupSlug })],
		practice: focus.practiceSlug,
	};
}

/**
 * How a switch to the other page is written into history. The first switch from a page the reader
 * reached on their own is a step of its own, so Back returns there. Every switch after it replaces
 * that step, so going between the two pages any number of times adds one entry, never one per
 * switch, and Back leaves both pages in one step.
 */
export function focusSwitchHistory(current: HistoryState | undefined): {
	replace: boolean;
	state: HistoryState;
} {
	return {
		replace: isFocusSwitchStep(current),
		// Not a level this visit pushed: closing it must not go back to the other page.
		state: { focusSwitch: true, detailPush: false },
	};
}

/**
 * Whether a history entry is the step a switch wrote. Only that entry carries the mark: a level the
 * reader opens over it is a new entry without it, so the next switch adds a step and Back still
 * returns to that level. Closing a level the switch opened rewrites the step (see
 * `DetailStackOptions.replaceOnClose`), so Back afterwards does not open it again.
 */
export function isFocusSwitchStep(state: HistoryState | undefined): boolean {
	return state?.focusSwitch === true;
}
