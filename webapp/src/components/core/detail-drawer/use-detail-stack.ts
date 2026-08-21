import { useNavigate, useRouter } from "@tanstack/react-router";
import { type DetailStackEntry, encodeDetailStack } from "./detail-stack";

/**
 * Whether a history entry was created by opening a detail level. Present only on entries this visit
 * pushed, which is what lets a dismiss know there is something behind it.
 *
 * Read and written through a cast rather than a `HistoryState` module augmentation: the interface
 * lives in `@tanstack/history`, a transitive package this workspace cannot name in a `declare
 * module`. The shape is asserted in one place, here.
 */
interface DetailHistoryState {
	detailPush?: boolean;
}

export interface DetailStackControls {
	/**
	 * Pushes a level programmatically. Prefer {@link import("./DetailStackLink").DetailStackLink} —
	 * this is for the openers that genuinely cannot be links, such as a row inside an open drawer.
	 */
	open: (entry: DetailStackEntry) => void;
	/** Closes the drawer at `depth`, leaving `depth` levels open. `close(0)` closes all of them. */
	close: (depth: number) => void;
}

/**
 * Drives a detail-drawer stack from the current route's search params. The route owns the `detail`
 * param — it reads the stack from its own typed search and passes it in — so this hook stays a
 * navigation concern only.
 *
 * Dismissing the top level goes *back* rather than forward to a shorter URL, so Escape and the
 * browser's Back button agree instead of fighting: without it, Escape then Back re-opens the drawer
 * the reader just dismissed. That only holds for a level this visit actually pushed, which is why
 * the depth is stamped on the history entry rather than counted here — a stack that arrived by deep
 * link has nothing behind it, and going back would leave the app.
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
