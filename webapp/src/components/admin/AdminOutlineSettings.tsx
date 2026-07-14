import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
	deleteOutlineCollectionMutation,
	getOutlineConnectionStatusOptions,
	getOutlineTokenStatusOptions,
	initiateMutation,
	listOptions,
	listOutlineCollectionsOptions,
	registerOutlineCollectionMutation,
	syncOutlineConnectionMutation,
	updateOutlineCollectionStateMutation,
	updateStatus1Mutation,
} from "@/api/@tanstack/react-query.gen";
import { problemDetailOf } from "@/lib/problem-detail";
import {
	OutlineCollectionsSection,
	type OutlineMirrorState,
} from "./outline/OutlineCollectionsSection";
import { OutlineConnectCard, type OutlineConnectInput } from "./outline/OutlineConnectCard";

export interface AdminOutlineSettingsProps {
	workspaceSlug: string;
}

/** How often the status/collection planes re-read the server while a reconcile is in flight. */
const SYNC_POLL_MS = 3_000;

/** The token state changes on the scale of months — one read per admin visit is plenty. */
const TOKEN_STATUS_STALE_MS = 5 * 60_000;

/**
 * Container for the Outline admin surface: reads the workspace's connections to derive the
 * connected state, and — while connected — the connection health and the mirrored-collections
 * plane. Drives connect (generic inline initiate: server URL + token only), disconnect
 * (status → UNINSTALLED), the fire-and-forget full reconcile, and the collection lifecycle
 * (register / pause / resume / remove + erase) through the generated hooks. Every mutation
 * invalidates both the collection list and the status line so the UI reflects server truth.
 * The components below are pure presentation.
 */
export function AdminOutlineSettings({ workspaceSlug }: AdminOutlineSettingsProps) {
	const queryClient = useQueryClient();

	const connectionsQueryOptions = listOptions({ path: { workspaceSlug } });
	const { data: connections } = useQuery({
		...connectionsQueryOptions,
		enabled: Boolean(workspaceSlug),
	});

	const outlineConnection = (connections ?? []).find(
		(connection) => connection.kind === "OUTLINE" && connection.state === "ACTIVE",
	);
	const connected = outlineConnection != null;

	// Collections + status only exist server-side while a connection is ACTIVE — gate the
	// queries on the derived connected state instead of firing guaranteed-404 requests.
	// A sync is a 202 fire-and-forget: nothing pushes its progress, so both planes poll while
	// (and only while) work is in flight, then fall back to a single fetch per invalidation.
	const statusQueryOptions = getOutlineConnectionStatusOptions({ path: { workspaceSlug } });
	const { data: connectionStatus, isLoading: isStatusLoading } = useQuery({
		...statusQueryOptions,
		enabled: connected,
		refetchInterval: (query) => (query.state.data?.syncRunning ? SYNC_POLL_MS : false),
	});

	const collectionsQueryOptions = listOutlineCollectionsOptions({ path: { workspaceSlug } });
	const {
		data: collections,
		isLoading: isLoadingCollections,
		error: collectionsError,
		refetch: refetchCollections,
	} = useQuery({
		...collectionsQueryOptions,
		enabled: connected,
		refetchInterval: (query) =>
			query.state.data?.some((collection) => collection.syncStatus === "PENDING")
				? SYNC_POLL_MS
				: false,
	});

	// The token state is a live call into Outline (and one an admin reads, not watches): cache it
	// hard and never poll. Connect/disconnect invalidate it explicitly.
	const tokenStatusQueryOptions = getOutlineTokenStatusOptions({ path: { workspaceSlug } });
	const { data: tokenStatus, isLoading: isTokenStatusLoading } = useQuery({
		...tokenStatusQueryOptions,
		enabled: connected,
		staleTime: TOKEN_STATUS_STALE_MS,
		retry: false,
	});

	const invalidateConnections = () =>
		queryClient.invalidateQueries({ queryKey: connectionsQueryOptions.queryKey });

	// Every collection/sync mutation refreshes all three planes: the list (rows, counts,
	// watermarks), the status line (aggregate document count, last sync), and the token state
	// (a connect stores a different key).
	const invalidateOutline = () => {
		queryClient.invalidateQueries({ queryKey: collectionsQueryOptions.queryKey });
		queryClient.invalidateQueries({ queryKey: statusQueryOptions.queryKey });
		queryClient.invalidateQueries({ queryKey: tokenStatusQueryOptions.queryKey });
	};

	const connect = useMutation({
		...initiateMutation(),
		onSuccess: () => {
			// Outline uses inline-credential connect (LINKED); the server persists an ACTIVE
			// connection, so a refetch of the connections list flips the card to its connected state.
			toast.success("Outline connected");
			invalidateConnections();
			invalidateOutline();
		},
		onError: (e) => {
			toast.error("Could not connect Outline", { description: problemDetailOf(e) });
		},
	});

	const disconnect = useMutation({
		...updateStatus1Mutation(),
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
		...syncOutlineConnectionMutation(),
		onSuccess: () => {
			// 202 fire-and-forget: the reconcile runs async; the invalidated queries pick up
			// progress (sync_status flips, watermarks advance) as it lands.
			toast.success("Sync started");
			invalidateOutline();
		},
		onError: (e) => {
			toast.error("Failed to start sync", { description: problemDetailOf(e) });
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

	// Dialog-driven mutations use mutateAsync so the dialogs await the result and only close
	// on success (pessimistic); onError above still surfaces the toast.
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

	return (
		<div className="space-y-10">
			<OutlineConnectCard
				connected={connected}
				connectionLabel={outlineConnection?.displayName ?? outlineConnection?.instanceKey}
				status={connectionStatus}
				isStatusLoading={connected && isStatusLoading}
				tokenStatus={tokenStatus}
				isTokenStatusLoading={connected && isTokenStatusLoading}
				isConnecting={connect.isPending}
				isDisconnecting={disconnect.isPending}
				isSyncing={syncNow.isPending}
				errorMessage={connect.error != null ? problemDetailOf(connect.error) : undefined}
				onConnect={handleConnect}
				onDisconnect={handleDisconnect}
				onSyncNow={() => syncNow.mutate({ path: { workspaceSlug } })}
			/>

			{connected && (
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
