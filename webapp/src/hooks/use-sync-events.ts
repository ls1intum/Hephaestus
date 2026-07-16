import { type QueryClient, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import {
	getConnectionSyncStatusQueryKey,
	getIntegrationCatalogQueryKey,
	getOutlineTokenStatusQueryKey,
	getRepositoriesToMonitorQueryKey,
	getWorkspaceQueryKey,
	listConnectionSyncJobsQueryKey,
	listConnectionSyncResourcesQueryKey,
	listOutlineCollectionsQueryKey,
	listQueryKey,
	listSlackChannelCandidatesQueryKey,
	listSlackChannelsQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { ConnectionSummary, IntegrationCatalogEntry } from "@/api/types.gen";
import environment from "@/environment";

type SyncEventScope = "job" | "resources" | "connection" | "activity";

interface SyncEventHint {
	scope: SyncEventScope;
	connectionId: number;
}

/** Backoff ladder for manual reconnects: 1s, 2s, 4s … capped, each with ±50% jitter. */
const RECONNECT_BASE_MS = 1_000;
const RECONNECT_CAP_MS = 30_000;

/**
 * Consecutive failed connects before the surface admits live updates are gone. One is a blip — a
 * proxy restart reconnects inside a second — and announcing that would be noise.
 */
const FAILURES_BEFORE_DEGRADED = 2;

/** Floor between catch-up resyncs, so a flapping stream cannot storm the cache. */
const RESYNC_THROTTLE_MS = 30_000;

/** Trailing window used to coalesce hint bursts from a chatty job. */
const HINT_DEBOUNCE_MS = 300;

/**
 * The query families this section owns, identified by the generated `_id` rather than a hand-typed
 * string so a renamed operation breaks the build instead of silently un-scoping the resync.
 */
function integrationQueryFamilyIds(workspaceSlug: string): ReadonlySet<string> {
	const path = { workspaceSlug };
	const connectionPath = { workspaceSlug, connectionId: 0 };
	return new Set(
		[
			getIntegrationCatalogQueryKey({ path }),
			getWorkspaceQueryKey({ path }),
			listQueryKey({ path }),
			listOutlineCollectionsQueryKey({ path }),
			getOutlineTokenStatusQueryKey({ path }),
			listSlackChannelsQueryKey({ path }),
			listSlackChannelCandidatesQueryKey({ path }),
			getRepositoriesToMonitorQueryKey({ path }),
			getConnectionSyncStatusQueryKey({ path: connectionPath }),
			listConnectionSyncJobsQueryKey({ path: connectionPath }),
			listConnectionSyncResourcesQueryKey({ path: connectionPath }),
		].map(([key]) => key._id),
	);
}

/** Resolve a hint's connection to its integration kind from whatever catalog the page has cached. */
function connectionKindOf(
	queryClient: QueryClient,
	workspaceSlug: string,
	connectionId: number,
): IntegrationCatalogEntry["kind"] | undefined {
	const catalog = queryClient.getQueryData<IntegrationCatalogEntry[]>(
		getIntegrationCatalogQueryKey({ path: { workspaceSlug } }),
	);
	const entry = catalog?.find((candidate) => candidate.connectionId === connectionId);
	if (entry) return entry.kind;

	const connections = queryClient.getQueryData<ConnectionSummary[]>(
		listQueryKey({ path: { workspaceSlug } }),
	);
	return connections?.find((candidate) => candidate.id === connectionId)?.kind;
}

/** Invalidates integration queries from workspace-scoped SSE hints. REST remains authoritative. */
export function useSyncEvents(workspaceSlug: string | undefined): boolean {
	const queryClient = useQueryClient();
	const [livePushUnavailable, setLivePushUnavailable] = useState(false);

	useEffect(() => {
		if (!workspaceSlug) return;

		setLivePushUnavailable(false);

		let source: EventSource | null = null;
		let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
		let consecutiveFailures = 0;
		let hasEverOpened = false;
		let lastResyncAt = 0;
		let disposed = false;
		const hintTimers = new Map<string, ReturnType<typeof setTimeout>>();

		const invalidate = (queryKey: readonly unknown[]) =>
			queryClient.invalidateQueries({ queryKey });

		/**
		 * Catch-up after a gap in the stream: hints carry no ids, so the browser cannot replay them
		 * with `Last-Event-ID` and everything missed while disconnected is simply lost. Marking this
		 * section's queries stale is the substitute — scoped to the families we own, never the whole
		 * workspace, because `invalidateQueries` cancels in-flight fetches by default.
		 */
		const resyncIntegrationQueries = () => {
			const familyIds = integrationQueryFamilyIds(workspaceSlug);
			queryClient.invalidateQueries({
				predicate: ({ queryKey }) => {
					const [key] = queryKey;
					if (!key || typeof key !== "object" || !("_id" in key)) return false;
					const { _id: id } = key as { _id?: unknown };
					if (typeof id !== "string" || !familyIds.has(id)) return false;
					const path = "path" in key ? (key as { path?: unknown }).path : undefined;
					return (
						typeof path === "object" &&
						path !== null &&
						"workspaceSlug" in path &&
						(path as { workspaceSlug?: unknown }).workspaceSlug === workspaceSlug
					);
				},
			});
		};

		const applyHint = ({ scope, connectionId }: SyncEventHint) => {
			switch (scope) {
				case "job":
					invalidate(getConnectionSyncStatusQueryKey({ path: { workspaceSlug, connectionId } }));
					invalidate(listConnectionSyncJobsQueryKey({ path: { workspaceSlug, connectionId } }));
					break;
				case "resources": {
					invalidate(
						listConnectionSyncResourcesQueryKey({ path: { workspaceSlug, connectionId } }),
					);
					invalidate(getConnectionSyncStatusQueryKey({ path: { workspaceSlug, connectionId } }));
					// Only the catalog belonging to *this* connection's integration changed. A GitHub
					// repo-sync hint has nothing to say about Slack channels or Outline collections.
					const kind = connectionKindOf(queryClient, workspaceSlug, connectionId);
					if (kind === "OUTLINE" || kind === undefined) {
						invalidate(listOutlineCollectionsQueryKey({ path: { workspaceSlug } }));
					}
					if (kind === "SLACK" || kind === undefined) {
						invalidate(listSlackChannelsQueryKey({ path: { workspaceSlug } }));
					}
					break;
				}
				case "connection":
					// A connect/disconnect moves the catalog, the workspace record and the connection list
					// together, so this is the one hint that legitimately touches the whole section.
					resyncIntegrationQueries();
					break;
				case "activity":
					invalidate(getConnectionSyncStatusQueryKey({ path: { workspaceSlug, connectionId } }));
					break;
				default:
					break;
			}
		};

		const handleHint = (event: MessageEvent<string>) => {
			let hint: SyncEventHint;
			try {
				hint = JSON.parse(event.data);
			} catch {
				return;
			}

			// A running job emits progress hints far faster than a human can read them; collapse each
			// burst to one refetch per scope per connection.
			const timerKey = `${hint.scope}:${hint.connectionId}`;
			const pending = hintTimers.get(timerKey);
			if (pending) clearTimeout(pending);
			hintTimers.set(
				timerKey,
				setTimeout(() => {
					hintTimers.delete(timerKey);
					applyHint(hint);
				}, HINT_DEBOUNCE_MS),
			);
		};

		const detach = (target: EventSource) => {
			target.removeEventListener("sync", handleHint);
			target.onopen = null;
			target.onerror = null;
			target.close();
		};

		const connect = () => {
			const current = new EventSource(
				`${environment.serverUrl}/workspaces/${workspaceSlug}/sync/events`,
				{ withCredentials: true },
			);
			source = current;

			current.onopen = () => {
				consecutiveFailures = 0;
				setLivePushUnavailable(false);

				const now = Date.now();
				// The first open races the page's own mount fetches, which are already loading exactly
				// this data — resyncing here would cancel and restart every one of them. Record the
				// timestamp anyway so an immediate re-open is throttled against the mount, not against
				// nothing.
				const isFirstOpen = !hasEverOpened;
				hasEverOpened = true;
				if (isFirstOpen || now - lastResyncAt < RESYNC_THROTTLE_MS) {
					if (isFirstOpen) lastResyncAt = now;
					return;
				}
				lastResyncAt = now;
				resyncIntegrationQueries();
			};

			current.addEventListener("sync", handleHint);

			current.onerror = () => {
				// CONNECTING means the browser is already retrying a network error on its own — per the
				// HTML spec that path is automatic, so stay out of it. CLOSED means the connection was
				// *failed*, which the spec reaches for any non-200 or a wrong Content-Type, and from
				// which the browser never retries: one 502 from the proxy during a deploy, or one 401 on
				// an expired session, ends live updates for the session unless we reconnect ourselves.
				if (current.readyState !== EventSource.CLOSED) return;

				consecutiveFailures += 1;
				if (consecutiveFailures >= FAILURES_BEFORE_DEGRADED) setLivePushUnavailable(true);

				detach(current);
				if (disposed) return;

				const backoff = Math.min(
					RECONNECT_CAP_MS,
					RECONNECT_BASE_MS * 2 ** (consecutiveFailures - 1),
				);
				// Jitter keeps every admin tab from re-storming the server on the same tick after a
				// shared outage.
				reconnectTimer = setTimeout(connect, backoff * (0.5 + Math.random() * 0.5));
			};
		};

		connect();

		return () => {
			disposed = true;
			if (reconnectTimer) clearTimeout(reconnectTimer);
			for (const timer of hintTimers.values()) clearTimeout(timer);
			hintTimers.clear();
			if (source) detach(source);
		};
	}, [workspaceSlug, queryClient]);

	return livePushUnavailable;
}
