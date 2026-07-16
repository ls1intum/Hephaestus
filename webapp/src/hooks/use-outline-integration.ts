import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
	deleteOutlineCollectionMutation,
	getConnectionSyncStatusOptions,
	getOutlineTokenStatusOptions,
	initiateMutation,
	listConnectionSyncJobsQueryKey,
	listOptions,
	listOutlineCollectionsOptions,
	registerOutlineCollectionMutation,
	triggerSyncJobMutation,
	updateConnectionStatusMutation,
	updateConnectionSyncJobMutation,
	updateOutlineCollectionStateMutation,
} from "@/api/@tanstack/react-query.gen";
import type { OutlineMirrorState } from "@/components/admin/integrations/outline/OutlineCollectionsSection";
import type {
	OutlineConnectInput,
	OutlineSyncSummary,
} from "@/components/admin/integrations/outline/OutlineConnectCard";
import { syncPollInterval } from "@/components/admin/integrations/sync-format";
import { useLivePushUnavailable } from "@/hooks/use-sync-liveness";
import { problemDetailOf } from "@/lib/problem-detail";

const TOKEN_STATUS_STALE_MS = 5 * 60_000;

export function useOutlineIntegration(workspaceSlug: string) {
	const queryClient = useQueryClient();
	const livePushUnavailable = useLivePushUnavailable();

	const connectionsQueryOptions = listOptions({ path: { workspaceSlug } });
	const connectionsQuery = useQuery({
		...connectionsQueryOptions,
		enabled: Boolean(workspaceSlug),
	});
	const connections = connectionsQuery.data;

	const outlineConnection = (connections ?? []).find(
		(connection) => connection.kind === "OUTLINE" && connection.state !== "UNINSTALLED",
	);
	const hasConnection = outlineConnection != null;
	const isConnectionActive = outlineConnection?.state === "ACTIVE";
	const connectionId = outlineConnection?.id;

	const statusQueryOptions = getConnectionSyncStatusOptions({
		path: { workspaceSlug, connectionId: connectionId ?? -1 },
	});
	const {
		data: connectionStatus,
		isLoading: isStatusLoading,
		error: statusError,
		refetch: refetchStatus,
	} = useQuery({
		...statusQueryOptions,
		enabled: hasConnection && connectionId != null,
		refetchInterval: (query) =>
			syncPollInterval(query.state.data?.activeJob != null, livePushUnavailable),
	});

	const collectionsQueryOptions = listOutlineCollectionsOptions({ path: { workspaceSlug } });
	const {
		data: collections,
		isLoading: isLoadingCollections,
		error: collectionsError,
		refetch: refetchCollections,
	} = useQuery({
		...collectionsQueryOptions,
		enabled: isConnectionActive,
		// A collection still PENDING is work in flight, so it drives the same adaptive cadence as
		// an active job rather than a third hand-tuned interval.
		refetchInterval: (query) =>
			syncPollInterval(
				query.state.data?.some((collection) => collection.syncStatus === "PENDING") ?? false,
				livePushUnavailable,
			),
	});

	const tokenStatusQueryOptions = getOutlineTokenStatusOptions({ path: { workspaceSlug } });
	const {
		data: tokenStatus,
		isLoading: isTokenStatusLoading,
		error: tokenStatusError,
		refetch: refetchTokenStatus,
	} = useQuery({
		...tokenStatusQueryOptions,
		enabled: isConnectionActive,
		staleTime: TOKEN_STATUS_STALE_MS,
		retry: false,
	});

	const invalidateConnections = () =>
		queryClient.invalidateQueries({ queryKey: connectionsQueryOptions.queryKey });

	const invalidateOutline = () => {
		queryClient.invalidateQueries({ queryKey: collectionsQueryOptions.queryKey });
		queryClient.invalidateQueries({ queryKey: statusQueryOptions.queryKey });
		queryClient.invalidateQueries({ queryKey: tokenStatusQueryOptions.queryKey });
		if (connectionId != null) {
			queryClient.invalidateQueries({
				queryKey: listConnectionSyncJobsQueryKey({ path: { workspaceSlug, connectionId } }),
			});
		}
	};

	const connect = useMutation({
		...initiateMutation(),
		onSuccess: () => {
			toast.success("Outline connected");
			invalidateConnections();
			invalidateOutline();
		},
		onError: (e) => {
			toast.error("Could not connect Outline", { description: problemDetailOf(e) });
		},
	});

	const disconnect = useMutation({
		...updateConnectionStatusMutation(),
		onSuccess: () => {
			toast.success("Outline disconnected");
			invalidateConnections();
			invalidateOutline();
		},
		onError: (e) => {
			toast.error("Failed to disconnect Outline", { description: problemDetailOf(e) });
		},
	});

	const syncNow = useMutation({
		...triggerSyncJobMutation(),
		onSuccess: () => {
			toast.success("Sync started");
			invalidateOutline();
		},
		onError: (e) => {
			toast.error("Failed to start sync", { description: problemDetailOf(e) });
		},
	});

	const cancelJob = useMutation({
		...updateConnectionSyncJobMutation(),
		onSuccess: () => {
			toast.success("Cancelling — stopping after current collection…");
			invalidateOutline();
		},
		onError: (e) => {
			toast.error("Failed to cancel sync", { description: problemDetailOf(e) });
		},
	});

	const registerCollection = useMutation({
		...registerOutlineCollectionMutation(),
		onSuccess: (collection) => {
			toast.success(`Collection “${collection.name ?? collection.collectionId}” added`);
			invalidateOutline();
		},
		onError: (e) => {
			toast.error("Failed to add collection", { description: problemDetailOf(e) });
		},
	});

	const updateCollectionState = useMutation({
		...updateOutlineCollectionStateMutation(),
		onSuccess: (collection) => {
			toast.success(collection.state === "PAUSED" ? "Collection paused" : "Collection resumed");
			invalidateOutline();
		},
		onError: (e) => {
			toast.error("Failed to update collection", { description: problemDetailOf(e) });
		},
	});

	const removeCollection = useMutation({
		...deleteOutlineCollectionMutation(),
		onSuccess: () => {
			toast.success("Collection removed and its mirrored documents erased");
			invalidateOutline();
		},
		onError: (e) => {
			toast.error("Failed to remove collection", { description: problemDetailOf(e) });
		},
	});

	// The catalog does not expose deployment availability, so translate the server's missing-strategy error.
	const connectErrorMessage = connect.error != null ? problemDetailOf(connect.error) : undefined;
	const connectUnavailable =
		connectErrorMessage != null && /no connectionstrategy registered/i.test(connectErrorMessage);

	const handleConnect = (input: OutlineConnectInput) => {
		connect.mutate({
			path: { workspaceSlug },
			body: {
				kind: "OUTLINE",
				userInput: {
					server_url: input.serverUrl,
					token: input.token,
				},
			},
		});
	};

	const handleDisconnect = () => {
		if (outlineConnection?.id == null) {
			return;
		}
		disconnect.mutate({
			path: { workspaceSlug, id: outlineConnection.id },
			body: { state: "UNINSTALLED" },
		});
	};

	const handleRegisterCollection = async ({ collectionId }: { collectionId: string }) => {
		await registerCollection.mutateAsync({
			path: { workspaceSlug },
			body: { collectionId },
		});
	};

	const handleUpdateCollectionState = async ({
		collectionId,
		state,
	}: {
		collectionId: string;
		state: OutlineMirrorState;
	}) => {
		await updateCollectionState.mutateAsync({
			path: { workspaceSlug, collectionId },
			body: { state },
		});
	};

	const handleRemoveCollection = async ({ collectionId }: { collectionId: string }) => {
		await removeCollection.mutateAsync({ path: { workspaceSlug, collectionId } });
	};

	const documentCount = (collections ?? []).reduce(
		(sum, collection) => sum + collection.documentCount,
		0,
	);
	const syncSummary: OutlineSyncSummary | undefined = connectionStatus && {
		webhookRegistered: connectionStatus.webhookRegistered,
		documentCount,
		lastSyncedAt: connectionStatus.lastSuccessfulSyncAt,
		syncRunning: connectionStatus.activeJob != null,
		erroredCollections: connectionStatus.resourceCounts.errored,
	};

	return {
		// Not gated on ACTIVE: a SUSPENDED connection is exactly when an admin needs the job history to
		// see what the last run did before it stopped. Sync CONTROLS stay gated (see isConnectionActive);
		// reading history is safe and the sibling integrations already show theirs in this state.
		connectionId,
		health: connectionStatus?.health,
		// Lets the route poll its job-history query on the same adaptive cadence as the rest.
		hasActiveJob: connectionStatus?.activeJob != null,
		isLoading: connectionsQuery.isLoading,
		connectionsError: connectionsQuery.error,
		retryConnections: () => connectionsQuery.refetch(),
		statusError,
		retryStatus: () => refetchStatus(),
		tokenStatusError,
		retryTokenStatus: () => refetchTokenStatus(),
		connectCardProps: {
			connected: hasConnection,
			connectionState: outlineConnection?.state,
			connectionLabel: outlineConnection?.displayName,
			status: syncSummary,
			isStatusLoading: hasConnection && isStatusLoading,
			tokenStatus,
			isTokenStatusLoading: hasConnection && isTokenStatusLoading,
			isConnecting: connect.isPending,
			isDisconnecting: disconnect.isPending,
			isSyncing: syncNow.isPending,
			syncDisabled: !isConnectionActive || statusError != null || connectionStatus == null,
			isCancelling: cancelJob.isPending,
			cancelRequested: connectionStatus?.activeJob?.cancelRequested,
			errorMessage: connectErrorMessage,
			connectUnavailable,
			onConnect: handleConnect,
			onDisconnect: handleDisconnect,
			onSyncNow: () => {
				if (connectionId == null) return;
				syncNow.mutate({
					path: { workspaceSlug, connectionId },
					body: { type: "RECONCILIATION" },
				});
			},
			onCancel: () => {
				const jobId = connectionStatus?.activeJob?.id;
				if (connectionId == null || jobId == null) return;
				cancelJob.mutate({
					path: { workspaceSlug, connectionId, jobId },
					body: { cancelRequested: true },
				});
			},
		},
		collectionsProps: isConnectionActive
			? {
					workspaceSlug,
					collections: collections ?? [],
					isLoading: isLoadingCollections,
					error: collectionsError,
					onRetry: () => refetchCollections(),
					onRegisterCollection: handleRegisterCollection,
					onUpdateCollectionState: handleUpdateCollectionState,
					onRemoveCollection: handleRemoveCollection,
				}
			: undefined,
	};
}
