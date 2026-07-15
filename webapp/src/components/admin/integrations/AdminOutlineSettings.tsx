import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
	cancelConnectionSyncJobMutation,
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
	updateOutlineCollectionStateMutation,
} from "@/api/@tanstack/react-query.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Skeleton } from "@/components/ui/skeleton";
import { problemDetailOf } from "@/lib/problem-detail";
import {
	OutlineCollectionsSection,
	type OutlineMirrorState,
} from "./outline/OutlineCollectionsSection";
import {
	OutlineConnectCard,
	type OutlineConnectInput,
	type OutlineSyncSummary,
} from "./outline/OutlineConnectCard";

export interface AdminOutlineSettingsProps {
	workspaceSlug: string;
}

/** The token state changes on the scale of months — one read per admin visit is plenty. */
const TOKEN_STATUS_STALE_MS = 5 * 60_000;

/**
 * Container for the Outline admin surface: connection state, health, and the mirrored-collections plane.
 * Every mutation invalidates the collection list, the unified sync status and the token state, so the
 * UI reflects server truth. The components below are pure presentation.
 *
 * This fetches here rather than at the route (the Slack sibling's pattern) because the Outline reads must stay
 * lazy: the status/token queries only exist while a connection is ACTIVE, and the candidates probe is a live
 * proxy into Outline that would otherwise hit it on every settings visit whether or not the picker is opened.
 *
 * Outline's old bespoke `GET /connections/outline/status` + `POST /connections/outline/sync` were
 * absorbed into the unified sync API (`ConnectionSyncStatus` + `triggerSyncJob`) — this container
 * composes the card's `OutlineSyncSummary` from that status plus a document-count rollup over the
 * mirrored collections, which already carry their own per-collection sync fields.
 */
export function AdminOutlineSettings({ workspaceSlug }: AdminOutlineSettingsProps) {
	const queryClient = useQueryClient();

	const connectionsQueryOptions = listOptions({ path: { workspaceSlug } });
	const connectionsQuery = useQuery({
		...connectionsQueryOptions,
		enabled: Boolean(workspaceSlug),
	});
	const connections = connectionsQuery.data;

	const outlineConnection = (connections ?? []).find(
		(connection) => connection.kind === "OUTLINE" && connection.state !== "UNINSTALLED",
	);
	const connected = outlineConnection != null;
	const active = outlineConnection?.state === "ACTIVE";
	const connectionId = outlineConnection?.id;

	// Gated on `connected` — the sync status endpoint is state-aware but there is nothing to show
	// without a connection at all. Tight polling only while a job is actually running.
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
		enabled: connected && connectionId != null,
		refetchInterval: (query) => (query.state.data?.activeJob ? 3_000 : 60_000),
	});

	const collectionsQueryOptions = listOutlineCollectionsOptions({ path: { workspaceSlug } });
	const {
		data: collections,
		isLoading: isLoadingCollections,
		error: collectionsError,
		refetch: refetchCollections,
	} = useQuery({
		...collectionsQueryOptions,
		enabled: active,
		refetchInterval: (query) =>
			query.state.data?.some((collection) => collection.syncStatus === "PENDING") ? 3_000 : 60_000,
	});

	// A live call into Outline that an admin reads rather than watches: cache hard, never poll.
	const tokenStatusQueryOptions = getOutlineTokenStatusOptions({ path: { workspaceSlug } });
	const {
		data: tokenStatus,
		isLoading: isTokenStatusLoading,
		error: tokenStatusError,
		refetch: refetchTokenStatus,
	} = useQuery({
		...tokenStatusQueryOptions,
		enabled: active,
		staleTime: TOKEN_STATUS_STALE_MS,
		retry: false,
	});

	const invalidateConnections = () =>
		queryClient.invalidateQueries({ queryKey: connectionsQueryOptions.queryKey });

	// Every mutation refreshes all planes: the collection list, the unified sync status, the token
	// state, and (for the sibling job-history table) the job list.
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
			// Inline-credential connect: the server persists an ACTIVE connection, so refetching the list flips the card.
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
		...cancelConnectionSyncJobMutation(),
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
			// The 409 ProblemDetail.detail for an illegal transition surfaces here.
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

	// Nothing tells the SPA up front whether this deployment has Outline enabled. When it is disabled the
	// server has no ConnectionStrategy for the kind and rejects the initiate with this exact 400 detail;
	// matching it turns a cryptic error into a clear "not available here". A capability field would be cleaner.
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

	// mutateAsync so the dialogs await the result and only close on success; onError still toasts.
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

	if (connectionsQuery.isLoading) {
		return <Skeleton className="h-48 w-full" />;
	}

	if (connectionsQuery.isError) {
		return (
			<QueryErrorAlert
				error={connectionsQuery.error}
				title="We couldn't load the Outline connection"
				onRetry={() => connectionsQuery.refetch()}
			/>
		);
	}

	return (
		<div className="space-y-10">
			{statusError && (
				<QueryErrorAlert
					error={statusError}
					title="We couldn't load Outline sync status"
					onRetry={() => refetchStatus()}
				/>
			)}
			{tokenStatusError && (
				<QueryErrorAlert
					error={tokenStatusError}
					title="We couldn't verify the Outline token"
					onRetry={() => refetchTokenStatus()}
				/>
			)}
			<OutlineConnectCard
				connected={connected}
				connectionState={outlineConnection?.state}
				connectionLabel={outlineConnection?.displayName}
				status={syncSummary}
				isStatusLoading={connected && isStatusLoading}
				tokenStatus={tokenStatus}
				isTokenStatusLoading={connected && isTokenStatusLoading}
				isConnecting={connect.isPending}
				isDisconnecting={disconnect.isPending}
				isSyncing={syncNow.isPending}
				syncDisabled={!active || statusError != null || connectionStatus == null}
				isCancelling={cancelJob.isPending}
				cancelRequested={connectionStatus?.activeJob?.cancelRequested}
				errorMessage={connectErrorMessage}
				connectUnavailable={connectUnavailable}
				onConnect={handleConnect}
				onDisconnect={handleDisconnect}
				onSyncNow={() => {
					if (connectionId == null) return;
					syncNow.mutate({
						path: { workspaceSlug, connectionId },
						body: { type: "RECONCILIATION" },
					});
				}}
				onCancel={() => {
					const jobId = connectionStatus?.activeJob?.id;
					if (connectionId == null || jobId == null) return;
					cancelJob.mutate({ path: { workspaceSlug, connectionId, jobId } });
				}}
			/>

			{active && (
				<OutlineCollectionsSection
					workspaceSlug={workspaceSlug}
					collections={collections ?? []}
					isLoading={isLoadingCollections}
					error={collectionsError}
					onRetry={() => {
						refetchCollections();
					}}
					onRegisterCollection={handleRegisterCollection}
					onUpdateCollectionState={handleUpdateCollectionState}
					onRemoveCollection={handleRemoveCollection}
				/>
			)}
		</div>
	);
}
