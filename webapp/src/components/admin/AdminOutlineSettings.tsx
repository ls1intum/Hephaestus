import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
	initiateMutation,
	listOptions,
	updateStatus1Mutation,
} from "@/api/@tanstack/react-query.gen";
import { type OutlineConnectInput, OutlineIntegrationCard } from "./outline/OutlineIntegrationCard";

export interface AdminOutlineSettingsProps {
	workspaceSlug: string;
}

// Normalize the free-form allow-list textarea (newline- or comma-separated) into the CSV the
// server-side inline-connect config builder parses for `collection_allow_list`.
function toAllowListCsv(raw: string): string {
	return raw
		.split(/[\n,]/)
		.map((entry) => entry.trim())
		.filter((entry) => entry.length > 0)
		.join(",");
}

/**
 * Container for the Outline integration card: reads the workspace's connections to derive the connected
 * state and drives connect (generic inline initiate) and disconnect (status → UNINSTALLED) through the
 * generated hooks. The card itself is pure presentation.
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

	const invalidateConnections = () =>
		queryClient.invalidateQueries({ queryKey: connectionsQueryOptions.queryKey });

	const connect = useMutation({
		...initiateMutation(),
		onSuccess: () => {
			// Outline uses inline-credential connect (LINKED); the server persists an ACTIVE connection,
			// so a refetch of the connections list flips the card to its connected state.
			toast.success("Outline connected");
			invalidateConnections();
		},
		onError: (e) => {
			toast.error("Could not connect Outline", {
				description: e instanceof Error ? e.message : undefined,
			});
		},
	});

	const disconnect = useMutation({
		...updateStatus1Mutation(),
		onSuccess: () => {
			toast.success("Outline disconnected");
			invalidateConnections();
		},
		onError: (e) => {
			toast.error("Failed to disconnect Outline", {
				description: e instanceof Error ? e.message : undefined,
			});
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
					collection_allow_list: toAllowListCsv(input.collectionAllowList),
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

	return (
		<OutlineIntegrationCard
			connected={outlineConnection != null}
			connectionLabel={outlineConnection?.instanceKey ?? outlineConnection?.displayName}
			isConnecting={connect.isPending}
			isDisconnecting={disconnect.isPending}
			errorMessage={connect.error instanceof Error ? connect.error.message : undefined}
			onConnect={handleConnect}
			onDisconnect={handleDisconnect}
		/>
	);
}
