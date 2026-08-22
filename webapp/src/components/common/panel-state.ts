import type { UseQueryResult } from "@tanstack/react-query";

/**
 * What a region shows while its data is in flight, failed, or in.
 *
 * One union instead of the four that had been written out by hand, because writing it out is also
 * where the four unchecked `as` casts came from: each surface adapted a query itself and asserted
 * the payload type, so a mis-ordered stack was a runtime `undefined.name` rather than a type error.
 * `panelStateFrom` is the only adapter, and it is generic, so the cast has nowhere to live.
 */
export type PanelState<TReady> =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| ({ status: "ready" } & TReady);

/**
 * A query becomes a panel state. `ready` runs only once data exists, so it receives the payload
 * already narrowed — that is what removes the caller's cast.
 */
export function panelStateFrom<TData, TReady>(
	query: Pick<UseQueryResult<TData>, "isPending" | "isError" | "error" | "data" | "refetch">,
	ready: (data: TData) => TReady,
): PanelState<TReady> {
	if (query.isError) {
		return { status: "error", error: query.error, onRetry: () => void query.refetch() };
	}
	if (query.isPending || query.data === undefined) {
		return { status: "loading" };
	}
	return { status: "ready", ...ready(query.data) };
}
