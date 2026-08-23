/**
 * What a region shows while its data is in flight, failed, or in.
 *
 * Not TanStack Query's own `pending | error | success`: a panel's ready state carries data the route
 * composed from several queries, and it treats `data === undefined` as still loading.
 */
export type PanelState<TReady> =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| ({ status: "ready" } & TReady);
