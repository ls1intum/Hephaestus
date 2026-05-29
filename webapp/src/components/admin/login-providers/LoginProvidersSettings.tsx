import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { KeyRoundIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	disconnectMutation,
	initiateMutation,
	listOptions,
	listQueryKey,
	reactivateMutation,
	suspendMutation,
} from "@/api/@tanstack/react-query.gen";
import type { ConnectionSummary } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogTrigger } from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { AddLoginProviderDialog } from "./AddLoginProviderDialog";
import { LoginProviderRow } from "./LoginProviderRow";
import {
	callbackUrlFor,
	IDENTITY_LOGIN_KINDS,
	type LoginProviderKind,
	problemDetailOf,
} from "./loginProviders";

export interface LoginProvidersSettingsProps {
	/** Numeric workspace id — the connections API is keyed by id, not slug. */
	workspaceId: number;
	/** API origin (e.g. environment.serverUrl) used to build the OAuth callback URL. */
	apiOrigin: string;
}

/**
 * Workspace-admin surface for self-hosted OIDC *login* providers (ADR 0017): a workspace
 * bringing its own GitHub Enterprise / self-hosted GitLab as a sign-in provider.
 *
 * <p>Wraps the generic `/api/v1/workspaces/{workspaceId}/connections` registry, filtering
 * client-side to the IDENTITY family (kinds {@code OIDC_LOGIN_GITHUB} / {@code OIDC_LOGIN_GITLAB})
 * so the Slack/SCM connections that share the endpoint don't leak onto this screen.
 */
export function LoginProvidersSettings({ workspaceId, apiOrigin }: LoginProvidersSettingsProps) {
	const queryClient = useQueryClient();
	const [dialogOpen, setDialogOpen] = useState(false);
	// Connection id we just created — its callback URL is surfaced prominently so the admin
	// can paste it into their IdP OAuth app (chicken-and-egg: the id only exists post-create).
	const [justCreated, setJustCreated] = useState<{ id: number; kind: LoginProviderKind } | null>(
		null,
	);

	const queryOptions = listOptions({ path: { workspaceId } });
	const { data, isLoading, error } = useQuery(queryOptions);

	const providers = (data ?? []).filter(
		(c): c is ConnectionSummary & { kind: LoginProviderKind } =>
			c.kind != null && IDENTITY_LOGIN_KINDS.includes(c.kind as LoginProviderKind),
	);

	const invalidateList = () =>
		queryClient.invalidateQueries({ queryKey: listQueryKey({ path: { workspaceId } }) });

	const initiate = useMutation({
		...initiateMutation(),
		onSuccess: (response) => {
			// OIDC login is always an inline-credential ("linked") flow — the synchronous
			// issuer-discovery probe ran server-side and a Connection row now exists.
			const connectionId =
				response && typeof response === "object" && "connectionId" in response
					? (response.connectionId as number | undefined)
					: undefined;
			invalidateList();
			setDialogOpen(false);
			if (connectionId != null && pendingKind != null) {
				setJustCreated({ id: connectionId, kind: pendingKind });
			}
			toast.success("Login provider added");
		},
		onError: (err) => {
			// The probe rejects unreachable/private/invalid issuers with RFC 9457 problem+json.
			// Surface the server's `detail` rather than swallowing it — handled inline in the
			// dialog; the toast is a secondary cue.
			toast.error("Could not add login provider", { description: problemDetailOf(err) });
		},
	});

	// Track which kind the in-flight initiate is for, so we can label the callback URL.
	const [pendingKind, setPendingKind] = useState<LoginProviderKind | null>(null);

	const lifecycleMutationOptions = {
		onSuccess: () => {
			invalidateList();
		},
	} as const;

	const suspend = useMutation({
		...suspendMutation(),
		...lifecycleMutationOptions,
		onError: (err) =>
			toast.error("Could not suspend provider", { description: problemDetailOf(err) }),
	});
	const reactivate = useMutation({
		...reactivateMutation(),
		...lifecycleMutationOptions,
		onError: (err) =>
			toast.error("Could not reactivate provider", { description: problemDetailOf(err) }),
	});
	const disconnect = useMutation({
		...disconnectMutation(),
		onSuccess: () => {
			invalidateList();
			toast.success("Login provider disconnected");
		},
		onError: (err) =>
			toast.error("Could not disconnect provider", { description: problemDetailOf(err) }),
	});

	return (
		<div className="space-y-6">
			<div>
				<div className="mb-4 flex items-center justify-between gap-4">
					<div>
						<h2 className="text-lg font-semibold">Login providers</h2>
						<p className="text-sm text-muted-foreground">
							Let members sign in with your own GitHub Enterprise or self-hosted GitLab. Hephaestus
							validates the issuer before saving your OAuth app credentials.
						</p>
					</div>
					<Dialog
						open={dialogOpen}
						onOpenChange={(open) => {
							setDialogOpen(open);
							if (!open) {
								initiate.reset();
							}
						}}
					>
						<DialogTrigger
							render={
								<Button>
									<PlusIcon className="mr-2 size-4" />
									Add provider
								</Button>
							}
						/>
						<AddLoginProviderDialog
							isSubmitting={initiate.isPending}
							submitError={initiate.isError ? problemDetailOf(initiate.error) : undefined}
							onSubmit={(payload) => {
								setPendingKind(payload.kind);
								initiate.reset();
								initiate.mutate({
									path: { workspaceId },
									body: { kind: payload.kind, userInput: payload.userInput },
								});
							}}
						/>
					</Dialog>
				</div>

				<Card>
					<CardContent className="space-y-3">
						{isLoading && (
							<div className="space-y-2" aria-busy="true" aria-live="polite">
								<Skeleton className="h-16 w-full" />
								<Skeleton className="h-16 w-full" />
							</div>
						)}

						{error && !isLoading && (
							<p className="text-sm text-destructive" role="alert">
								Failed to load login providers. Please refresh and try again.
							</p>
						)}

						{!isLoading && !error && providers.length === 0 && (
							<div className="flex flex-col items-center gap-3 py-8 text-center">
								<div className="rounded-full bg-muted p-3">
									<KeyRoundIcon className="size-6 text-muted-foreground" aria-hidden="true" />
								</div>
								<div>
									<p className="font-medium">No login providers yet</p>
									<p className="text-sm text-muted-foreground">
										Add a GitHub Enterprise or self-hosted GitLab provider so your members can sign
										in with it.
									</p>
								</div>
								<Button onClick={() => setDialogOpen(true)}>
									<PlusIcon className="mr-2 size-4" />
									Add your first provider
								</Button>
							</div>
						)}

						{!isLoading &&
							providers.map((provider) => (
								<LoginProviderRow
									key={provider.id}
									workspaceId={workspaceId}
									provider={provider}
									callbackUrl={
										provider.id != null
											? callbackUrlFor(apiOrigin, provider.kind, provider.id)
											: undefined
									}
									highlightCallback={justCreated?.id === provider.id}
									isSuspending={suspend.isPending && suspend.variables?.path.id === provider.id}
									isReactivating={
										reactivate.isPending && reactivate.variables?.path.id === provider.id
									}
									isDisconnecting={
										disconnect.isPending && disconnect.variables?.path.id === provider.id
									}
									onSuspend={(reason) =>
										provider.id != null &&
										suspend.mutate({ path: { workspaceId, id: provider.id }, body: { reason } })
									}
									onReactivate={() =>
										provider.id != null &&
										reactivate.mutate({ path: { workspaceId, id: provider.id }, body: {} })
									}
									onDisconnect={() =>
										provider.id != null &&
										disconnect.mutate({ path: { workspaceId, id: provider.id } })
									}
								/>
							))}
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
