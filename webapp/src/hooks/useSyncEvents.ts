import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import {
	getConnectionSyncStatusQueryKey,
	getIntegrationCatalogQueryKey,
	getWorkspaceQueryKey,
	listConnectionSyncJobsQueryKey,
	listConnectionSyncResourcesQueryKey,
	listOutlineCollectionsQueryKey,
	listQueryKey,
	listSlackChannelsQueryKey,
} from "@/api/@tanstack/react-query.gen";
import environment from "@/environment";

type SyncEventScope = "job" | "resources" | "connection" | "activity";

interface SyncEventHint {
	scope: SyncEventScope;
	connectionId: number;
}

/** Invalidates integration queries from workspace-scoped SSE hints. REST remains authoritative. */
export function useSyncEvents(workspaceSlug: string | undefined): boolean {
	const queryClient = useQueryClient();
	const [livePushUnavailable, setLivePushUnavailable] = useState(false);

	useEffect(() => {
		if (!workspaceSlug) return;

		setLivePushUnavailable(false);

		const source = new EventSource(
			`${environment.serverUrl}/workspaces/${workspaceSlug}/sync/events`,
			{
				withCredentials: true,
			},
		);

		const invalidateWorkspaceQueries = () => {
			queryClient.invalidateQueries({
				queryKey: getIntegrationCatalogQueryKey({ path: { workspaceSlug } }),
			});
			queryClient.invalidateQueries({
				queryKey: getWorkspaceQueryKey({ path: { workspaceSlug } }),
			});
			queryClient.invalidateQueries({ queryKey: listQueryKey({ path: { workspaceSlug } }) });
			queryClient.invalidateQueries({
				queryKey: listOutlineCollectionsQueryKey({ path: { workspaceSlug } }),
			});
			queryClient.invalidateQueries({
				queryKey: listSlackChannelsQueryKey({ path: { workspaceSlug } }),
			});
		};

		source.onopen = () => {
			queryClient.invalidateQueries({
				predicate: ({ queryKey }) => {
					const [key] = queryKey;
					if (!key || typeof key !== "object" || !("path" in key)) return false;
					const path = key.path;
					return (
						typeof path === "object" &&
						path !== null &&
						"workspaceSlug" in path &&
						path.workspaceSlug === workspaceSlug
					);
				},
			});
		};

		const handleHint = (event: MessageEvent<string>) => {
			let hint: SyncEventHint;
			try {
				hint = JSON.parse(event.data);
			} catch {
				return;
			}

			const { scope, connectionId } = hint;

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
					queryClient.invalidateQueries({
						queryKey: listOutlineCollectionsQueryKey({ path: { workspaceSlug } }),
					});
					queryClient.invalidateQueries({
						queryKey: listSlackChannelsQueryKey({ path: { workspaceSlug } }),
					});
					break;
				case "connection":
					invalidateWorkspaceQueries();
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
		source.onerror = () => {
			if (source.readyState === EventSource.CLOSED) {
				setLivePushUnavailable(true);
			}
		};

		return () => {
			source.removeEventListener("sync", handleHint);
			source.onopen = null;
			source.onerror = null;
			source.close();
		};
	}, [workspaceSlug, queryClient]);

	return livePushUnavailable;
}
