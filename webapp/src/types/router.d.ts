// Custom state carried on a history entry, readable back off `location.state`.
//
// Augmented through `@tanstack/react-router`, which re-exports the interface, rather than through
// `@tanstack/history`, where it is declared: the latter reaches this workspace only as a transitive
// dependency of the former, so naming it here is `TS2664: module cannot be found`.
import "@tanstack/react-router";

declare module "@tanstack/react-router" {
	interface HistoryState {
		autoGreeting?: boolean;
		/** Stamped by {@link import("@/components/layout/detail-drawer/DetailStackLink").DetailStackLink} on the entry it pushes, so a dismiss knows it can go back rather than forward to a shorter URL. */
		detailPush?: boolean;
		/** With `detailPush`: how many levels at the top of the stack this visit pushed in a row, one entry each, see {@link import("@/components/layout/detail-drawer/detail-stack").pushedLevels}. */
		detailPushes?: number;
		/** Draft: stamped on the entry a switch between the Practice profile and Practices across the workspace wrote, see {@link import("@/components/practice-focus/practice-focus").focusSwitchHistory}. */
		focusSwitch?: boolean;
	}
}
