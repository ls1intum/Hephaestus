import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import {
	getConnectionSyncStatusQueryKey,
	getIntegrationCatalogQueryKey,
	listConnectionSyncJobsQueryKey,
	listConnectionSyncResourcesQueryKey,
} from "@/api/@tanstack/react-query.gen";
import environment from "@/environment";

type SyncEventScope = "job" | "resources" | "connection" | "activity";

interface SyncEventHint {
	scope: SyncEventScope;
	connectionId?: number;
	kind?: "GITHUB" | "GITLAB" | "SLACK" | "OUTLINE";
}

/**
 * Live invalidation channel for the Integrations admin section. Opens an SSE stream at
 * `GET /workspaces/{slug}/sync/events` and, on each hint, invalidates the matching generated
 * query keys instead of consuming a pushed payload — REST stays the single source of truth
 * (see `.ai/notes/integration-sync-observability-design.md` §3.5).
 *
 * The endpoint may not exist yet in every environment (server not running the sync feature
 * locally, or an older deployment). Note the EventSource contract (WHATWG SSE spec): a non-200
 * response — 404 (missing endpoint), 401 (expired auth), 500 — *fails the connection permanently*;
 * the UA fires `onerror` once with `readyState === CLOSED` and does NOT auto-reconnect. Only a
 * transient drop after a successful connect is retried by the UA. Either way the overview/detail
 * queries' own `refetchInterval` polling keeps the UI correct without this hook, so we degrade to
 * polling rather than hammering a reconnect loop. Mount once, in the Integrations layout route.
 */
export function useSyncEvents(workspaceSlug: string | undefined): void {
	const queryClient = useQueryClient();

	useEffect(() => {
		if (!workspaceSlug) return;

		const source = new EventSource(
			`${environment.serverUrl}/workspaces/${workspaceSlug}/sync/events`,
			{
				withCredentials: true,
			},
		);

		const handleHint = (event: MessageEvent<string>) => {
			let hint: SyncEventHint;
			try {
				hint = JSON.parse(event.data);
			} catch {
				return;
			}

			const { scope, connectionId } = hint;

			if (connectionId == null) {
				queryClient.invalidateQueries({
					queryKey: getIntegrationCatalogQueryKey({ path: { workspaceSlug } }),
				});
				return;
			}

			switch (scope) {
				case "job":
					queryClient.invalidateQueries({
						queryKey: getConnectionSyncStatusQueryKey({ path: { workspaceSlug, connectionId } }),
					});
					queryClient.invalidateQueries({
						queryKey: listConnectionSyncJobsQueryKey({ path: { workspaceSlug, connectionId } }),
					});
					break;
				case "resources":
					queryClient.invalidateQueries({
						queryKey: listConnectionSyncResourcesQueryKey({
							path: { workspaceSlug, connectionId },
						}),
					});
					queryClient.invalidateQueries({
						queryKey: getConnectionSyncStatusQueryKey({ path: { workspaceSlug, connectionId } }),
					});
					break;
				case "connection":
					queryClient.invalidateQueries({
						queryKey: getIntegrationCatalogQueryKey({ path: { workspaceSlug } }),
					});
					queryClient.invalidateQueries({
						queryKey: getConnectionSyncStatusQueryKey({ path: { workspaceSlug, connectionId } }),
					});
					break;
				case "activity":
					queryClient.invalidateQueries({
						queryKey: getConnectionSyncStatusQueryKey({ path: { workspaceSlug, connectionId } }),
					});
					break;
				default:
					break;
			}
		};

		source.addEventListener("sync", handleHint);
		let closedLogged = false;
		source.onerror = () => {
			// A permanent failure (non-200: missing endpoint, expired auth, server error) leaves the
			// stream CLOSED and the UA will not reconnect. Log it once and rely on the queries'
			// refetchInterval polling — do NOT re-create the source here (that would hammer a dead
			// endpoint). A transient drop after a live connect keeps readyState === CONNECTING and is
			// retried by the UA on its own, so we stay silent for that case.
			if (source.readyState === EventSource.CLOSED && !closedLogged) {
				closedLogged = true;
				console.info("Sync live-push unavailable; falling back to polling for this session.");
			}
		};

		return () => {
			source.removeEventListener("sync", handleHint);
			source.close();
		};
	}, [workspaceSlug, queryClient]);
}
