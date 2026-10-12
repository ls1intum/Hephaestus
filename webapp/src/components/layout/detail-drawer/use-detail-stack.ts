import { type HistoryState, useRouter } from "@tanstack/react-router";
import { useEffect, useRef } from "react";

import { useSearchState } from "@/lib/search-params";

import {
	type DetailStackEntry,
	detailEntryState,
	detailStackKey,
	openInStack,
	pushedLevels,
	stackInSearch,
} from "./detail-stack";

/** Values for the level params the hook was given, and no others: a misspelt key does not compile. */
type LevelSearch<TParam extends string> = Partial<Record<TParam, unknown>>;

export interface DetailStackControls<TParam extends string = never> {
	/** Prefer {@link import("./DetailStackLink").DetailStackLink}; this is for openers that cannot be links. */
	open: (entry: DetailStackEntry) => void;
	/**
	 * Opens several levels, one history entry each, so Back closes them one at a time; `levelSearch`
	 * is written with the last of them.
	 */
	push: (entries: DetailStackEntry[], levelSearch?: LevelSearch<TParam>) => Promise<void>;
	close: (depth: number) => void;
	/**
	 * Replaces search state that is a view, not a place (a tab, a sort, a range). It keeps the
	 * history entry's state, which `close` reads to dismiss a level by going back.
	 */
	setView: (view: Record<string, unknown>) => void;
}

export interface DetailStackOptions<TParam extends string = never> {
	/**
	 * Search params that mean something only inside a level — an open card, a tab — cleared in the
	 * same write that changes the stack. Going back restores the entry before the level was pushed,
	 * which never held them; a forward write would otherwise leave them dangling under a level
	 * that no longer shows them, or hand them to the next level opened.
	 */
	levelParams?: readonly TParam[];
	/**
	 * Whether closing a level that was not pushed on this entry rewrites the entry instead of adding
	 * one. A page that another page navigates into as a step of its own passes this, so closing a
	 * level that step opened belongs to the step and Back afterwards leaves for where it came from
	 * instead of opening the level again. Without it, such a close is a new entry.
	 */
	replaceOnClose?: (state: HistoryState) => boolean;
}

/**
 * Dismissing levels goes *back*, not forward to a shorter URL, so Escape and the browser's Back
 * button agree — otherwise Escape then Back re-opens what was just dismissed. Only for levels this
 * visit pushed, which is why the history entry is stamped with how many of its top levels were
 * pushed one entry each ({@link pushedLevels}): a deep-linked stack has nothing behind it, and going
 * back would leave the app. Closing several levels at once — a crumb, a press outside, the close of
 * a level under another — goes back that many entries, so Back afterwards does not open them again.
 * Where fewer of them were pushed, it goes back over those and closes the rest on the entry it lands
 * on, so no close is ever written ahead of a level Back would open again.
 */
export function useDetailStack<TParam extends string = never>({
	levelParams = [],
	replaceOnClose,
}: DetailStackOptions<TParam> = {}): DetailStackControls<TParam> {
	const setSearch = useSearchState();
	const router = useRouter();
	// The listener of a close still waiting for its back step, dropped if the page goes first.
	const pendingClose = useRef<(() => void) | undefined>(undefined);
	useEffect(
		() => () => {
			pendingClose.current?.();
			pendingClose.current = undefined;
		},
		[],
	);
	// The stack in the address now, in wire form: a control can run long after its render, such as a
	// close after a request, and the reader may have moved the stack in the meantime.
	const liveStack = () =>
		stackInSearch((router.state.location.search as { detail?: unknown }).detail);

	const goToStack = async (
		next: string[],
		detailPush: boolean,
		levelSearch: LevelSearch<TParam> = {},
		replace = false,
	) =>
		setSearch(
			(previous) => ({
				...previous,
				...Object.fromEntries(levelParams.map((param) => [param, undefined])),
				...levelSearch,
				detail: next.length > 0 ? next : undefined,
			}),
			{ state: (previous) => detailEntryState(previous, detailPush, replace), replace },
		);

	const push = async (entries: DetailStackEntry[], levelSearch?: LevelSearch<TParam>) => {
		// In turn, one history entry each. The rules are DetailStackLink's: a level already in front
		// keeps its entry, and one already open lower down or past the full depth is not a push.
		let current = liveStack();
		for (const [index, entry] of entries.entries()) {
			const key = detailStackKey(entry);
			const last = index === entries.length - 1;
			if (current.at(-1) === key) {
				if (last && levelSearch !== undefined) {
					await setSearch((previous) => ({ ...previous, ...levelSearch }), {
						state: true,
						replace: true,
					});
				}
				continue;
			}
			const { detail, pushed } = openInStack(current, key);
			current = detail;
			await goToStack(detail, pushed, last ? levelSearch : undefined);
		}
	};

	return {
		open: (entry) => {
			void push([entry]);
		},
		push,
		close: (depth) => {
			const current = liveStack();
			const closing = current.length - depth;
			if (closing <= 0) {
				return;
			}
			// Only levels this visit pushed: the entries below them are not known to be ours.
			const pushed = pushedLevels(router.state.location.state);
			if (pushed >= closing) {
				router.history.go(-closing);
				return;
			}
			// The rest is closed on the entry the reader is on, which `replaceOnClose` judges.
			const closeRest = () => {
				void goToStack(
					current.slice(0, depth),
					false,
					undefined,
					replaceOnClose?.(router.state.location.state) === true,
				);
			};
			if (pushed === 0) {
				closeRest();
				return;
			}
			// Back over the pushed levels first: a close written over them would leave them ahead of
			// it, and Back would open them again. The rest is written only where that step lands: a
			// blocker that keeps the reader on the level (unsaved changes) also resolves a navigation,
			// on the entry the close started from.
			const target = router.state.location.state.__TSR_index - pushed;
			pendingClose.current?.();
			const unsubscribe = router.subscribe("onResolved", () => {
				unsubscribe();
				pendingClose.current = undefined;
				if (router.state.location.state.__TSR_index === target) {
					closeRest();
				}
			});
			pendingClose.current = unsubscribe;
			router.history.go(-pushed);
		},
		setView: (view) => {
			void setSearch((previous) => ({ ...previous, ...view }), { state: true, replace: true });
		},
	};
}
