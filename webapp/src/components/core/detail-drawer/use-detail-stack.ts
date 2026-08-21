import { useNavigate, useRouter } from "@tanstack/react-router";
import { useRef } from "react";
import { type DetailStackEntry, encodeDetailStack } from "./detail-stack";

export interface DetailStackControls {
	/**
	 * Pushes a level. Prefer {@link import("./DetailStackLink").DetailStackLink} — this is for the
	 * openers that genuinely cannot be links, such as a row inside an already-open drawer.
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
 * Dismissing a drawer goes *back* rather than forward to a shorter URL, so Escape and the browser's
 * Back button agree with each other instead of fighting. That only holds for levels opened during
 * this visit: a stack that arrived by deep link has nothing behind it in history, and going back
 * would leave the app.
 */
export function useDetailStack(stack: DetailStackEntry[]): DetailStackControls {
	const navigate = useNavigate();
	const router = useRouter();
	const deepLinkedDepth = useRef(stack.length);

	const goToStack = (next: DetailStackEntry[]) => {
		navigate({
			to: ".",
			search: (previous: Record<string, unknown>) => ({
				...previous,
				detail: encodeDetailStack(next),
			}),
		});
	};

	return {
		open: (entry) => goToStack([...stack, entry]),
		close: (depth) => {
			if (depth >= deepLinkedDepth.current && depth === stack.length - 1) {
				router.history.back();
				return;
			}
			goToStack(stack.slice(0, depth));
		},
	};
}
