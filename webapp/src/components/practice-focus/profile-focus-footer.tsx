import {
	type DetailStackEntry,
	detailStackKey,
} from "@/components/layout/detail-drawer/detail-stack";

import { focusOfProfileStack, type PracticeFocus } from "./practice-focus";
import { FOCUS_FOOTER_COPY, focusSwitchName, PracticeFocusFooter } from "./PracticeFocusFooter";

export interface ProfileFocusFooterOptions {
	/** The profile's open levels, outermost first. */
	stack: readonly DetailStackEntry[];
	/** The group a practice is filed in, for a practice opened without its group under it. */
	groupOf: (practiceSlug: string) => string | undefined;
	/** The group's or the practice's name, for the button's accessible name. */
	nameOf: (focus: PracticeFocus) => string | undefined;
	onSwitch: (focus: PracticeFocus) => void;
}

/**
 * The footer of the list of every group, a group or a practice level of the Practice profile,
 * read off the levels up to that one, so a group under an open practice still goes to its group.
 * Other levels get none.
 */
export function ProfileFocusFooter({
	entry,
	stack,
	groupOf,
	nameOf,
	onSwitch,
}: ProfileFocusFooterOptions & { entry: DetailStackEntry }) {
	const index = stack.findIndex((each) => detailStackKey(each) === detailStackKey(entry));
	const focus = index === -1 ? undefined : focusOfProfileStack(stack.slice(0, index + 1), groupOf);
	if (focus === undefined) {
		return null;
	}
	let copy: { sentence: string; label: string } = FOCUS_FOOTER_COPY.profileAllGroups;
	if (focus.groupSlug !== undefined) {
		copy =
			focus.practiceSlug === undefined
				? FOCUS_FOOTER_COPY.profileGroup
				: FOCUS_FOOTER_COPY.profilePractice;
	}
	return (
		<PracticeFocusFooter
			{...copy}
			accessibleLabel={focusSwitchName(copy.label, nameOf(focus))}
			onSwitch={() => onSwitch(focus)}
		/>
	);
}
