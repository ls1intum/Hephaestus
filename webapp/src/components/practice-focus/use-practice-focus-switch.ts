import { useNavigate, useRouter } from "@tanstack/react-router";

import { detailStackKey } from "@/components/layout/detail-drawer/detail-stack";

import {
	focusSwitchHistory,
	type PracticeFocus,
	profileStackOf,
	workspaceSearchOf,
} from "./practice-focus";

export interface PracticeFocusSwitch {
	/** The same group or practice in the reader's own Practice profile. */
	toProfile: (focus: PracticeFocus) => void;
	/** The same group, with the practice marked, on Practices across the workspace. */
	toWorkspace: (focus: PracticeFocus) => void;
}

/**
 * The switch between the reader's own levels and the workspace's at the same group or
 * practice. It replaces the other page's levels with the focus instead of stacking them on what was
 * open, and writes history as {@link focusSwitchHistory} says, so going back and forth never grows
 * the stack or the history.
 */
export function usePracticeFocusSwitch(workspaceSlug: string): PracticeFocusSwitch {
	const navigate = useNavigate();
	const router = useRouter();
	const history = () => focusSwitchHistory(router.state.location.state);
	return {
		toProfile: (focus) => {
			void navigate({
				to: "/w/$workspaceSlug/practice-profile",
				params: { workspaceSlug },
				search: { detail: profileStackOf(focus).map(detailStackKey) },
				resetScroll: false,
				...history(),
			});
		},
		toWorkspace: (focus) => {
			void navigate({
				to: "/w/$workspaceSlug/practices-across-the-workspace",
				params: { workspaceSlug },
				// Nothing of the profile's search goes along; the workspace page opens at its default window.
				search: workspaceSearchOf(focus),
				resetScroll: false,
				...history(),
			});
		},
	};
}
