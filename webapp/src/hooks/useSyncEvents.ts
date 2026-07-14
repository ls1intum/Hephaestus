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
 * locally, or an older deployment) — `EventSource` retries on its own, so failures must stay
 * silent; the overview/detail queries' own `refetchInterval` polling keeps the UI correct
 * without this hook regardless. Mount once, in the Integrations layout route.
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
		// EventSource retries the connection on its own; swallowing here just prevents a missing or
		// unavailable endpoint from spamming the console on every reconnect attempt.
		source.onerror = () => {};

		return () => {
			source.removeEventListener("sync", handleHint);
			source.close();
		};
	}, [workspaceSlug, queryClient]);
}
