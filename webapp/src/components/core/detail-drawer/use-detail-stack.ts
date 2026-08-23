import { useRouter } from "@tanstack/react-router";
import { useSearchState } from "@/lib/search-params";
import { type DetailStackEntry, encodeDetailStack } from "./detail-stack";

/**
 * Whether this visit is what pushed the top level. Read off the history entry rather than declared
 * on it: `HistoryState` lives in `@tanstack/history`, which this workspace cannot name in a
 * `declare module`, so the stamp is checked where it is read.
 */
function wasPushedHere(state: unknown): boolean {
	return (
		typeof state === "object" &&
		state !== null &&
		"detailPush" in state &&
		state.detailPush === true
	);
}

export interface DetailStackControls {
	/** Prefer {@link import("./DetailStackLink").DetailStackLink}; this is for openers that cannot be links. */
	open: (entry: DetailStackEntry) => void;
	close: (depth: number) => void;
}

/**
 * Dismissing the top level goes *back*, not forward to a shorter URL, so Escape and the browser's
 * Back button agree — otherwise Escape then Back re-opens what was just dismissed. Only for a level
 * this visit pushed, which is why the history entry is stamped rather than counted: a deep-linked
 * stack has nothing behind it, and going back would leave the app.
 */
export function useDetailStack(stack: DetailStackEntry[]): DetailStackControls {
	const setSearch = useSearchState();
	const router = useRouter();

	const goToStack = (next: DetailStackEntry[], detailPush: boolean) => {
		void setSearch(
			(previous) => ({ ...previous, detail: encodeDetailStack(next) }),
			(previous) => ({ ...previous, detailPush }),
		);
	};

	return {
		open: (entry) => goToStack([...stack, entry], true),
		close: (depth) => {
			// Only the top level: the entries below it are not known to be ours.
			if (wasPushedHere(router.state.location.state) && depth === stack.length - 1) {
				router.history.back();
				return;
			}
			goToStack(stack.slice(0, depth), false);
		},
	};
}
