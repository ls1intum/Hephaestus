import { useNavigate, useRouter } from "@tanstack/react-router";
import { type DetailStackEntry, encodeDetailStack } from "./detail-stack";

/**
 * Cast rather than a `HistoryState` module augmentation: the interface lives in `@tanstack/history`,
 * a transitive package this workspace cannot name in a `declare module`. Asserted here only.
 */
interface DetailHistoryState {
	detailPush?: boolean;
}

export interface DetailStackControls {
	/** Prefer {@link import("./DetailStackLink").DetailStackLink}; this is for openers that cannot be links. */
	open: (entry: DetailStackEntry) => void;
	/** Closes the drawer at `depth`, leaving `depth` levels open. `close(0)` closes all of them. */
	close: (depth: number) => void;
}

/**
 * Dismissing the top level goes *back*, not forward to a shorter URL, so Escape and the browser's
 * Back button agree — otherwise Escape then Back re-opens what was just dismissed. Only for a level
 * this visit pushed, which is why the history entry is stamped rather than counted: a deep-linked
 * stack has nothing behind it, and going back would leave the app.
 */
export function useDetailStack(stack: DetailStackEntry[]): DetailStackControls {
	const navigate = useNavigate();
	const router = useRouter();

	const goToStack = (next: DetailStackEntry[], detailPush: boolean) => {
		navigate({
			to: ".",
			search: (previous: Record<string, unknown>) => ({
				...previous,
				detail: encodeDetailStack(next),
			}),
			state: (previous) => ({ ...previous, detailPush }) as never,
		});
	};

	return {
		open: (entry) => goToStack([...stack, entry], true),
		close: (depth) => {
			// Only the top level can go back: the entries below it are not known to be ours, so
			// closing several at once has to move forward to be safe.
			const current = router.state.location.state as DetailHistoryState;
			if (current.detailPush === true && depth === stack.length - 1) {
				router.history.back();
				return;
			}
			goToStack(stack.slice(0, depth), false);
		},
	};
}
