import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { KeyRoundIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	initiateMutation,
	listOptions,
	listQueryKey,
	updateStatus1Mutation,
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
	/** Workspace slug — the connections API is keyed by slug. */
	workspaceSlug: string;
	/** API origin (e.g. environment.serverUrl) used to build the OAuth callback URL. */
	apiOrigin: string;
}

/**
 * Workspace-admin surface for self-hosted OIDC *login* providers (ADR 0017): a workspace
 * bringing its own GitHub Enterprise / self-hosted GitLab as a sign-in provider.
 *
 * Wraps the generic `/api/v1/workspaces/{workspaceId}/connections` registry, filtering
 * client-side to the IDENTITY family (kinds `OIDC_LOGIN_GITHUB` / `OIDC_LOGIN_GITLAB`)
 * so the Slack/SCM connections that share the endpoint don't leak onto this screen.
 */
export function LoginProvidersSettings({ workspaceSlug, apiOrigin }: LoginProvidersSettingsProps) {
	const queryClient = useQueryClient();
	const [dialogOpen, setDialogOpen] = useState(false);
	// Connection id we just created — its callback URL is surfaced prominently so the admin
	// can paste it into their IdP OAuth app (chicken-and-egg: the id only exists post-create).
	const [justCreated, setJustCreated] = useState<{ id: number; kind: LoginProviderKind } | null>(
		null,
	);

	const queryOptions = listOptions({ path: { workspaceSlug } });
	const { data, isLoading, error } = useQuery(queryOptions);

	const providers = (data ?? []).filter(
		(c): c is ConnectionSummary & { kind: LoginProviderKind } =>
			c.kind != null && IDENTITY_LOGIN_KINDS.includes(c.kind as LoginProviderKind),
	);

	const invalidateList = () =>
		queryClient.invalidateQueries({ queryKey: listQueryKey({ path: { workspaceSlug } }) });

	const initiate = useMutation({
		...initiateMutation(),
		onSuccess: (response) => {
			// OIDC login is always an inline-credential ("LINKED") flow — the synchronous
			// issuer-discovery probe ran server-side and a Connection row now exists, so the
			// flat response carries `connectionId` rather than a `vendorUrl` redirect.
			invalidateList();
			setDialogOpen(false);
			if (response.type === "LINKED" && response.connectionId != null && pendingKind != null) {
				setJustCreated({ id: response.connectionId, kind: pendingKind });
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

	// All three lifecycle transitions funnel through the single status endpoint
	// (PATCH .../connections/{id}/status). We distinguish the action by the target
	// `state` in the in-flight variables so per-row pending state stays accurate.
	const updateStatus = useMutation({
		...updateStatus1Mutation(),
		onSuccess: (_data, variables) => {
			invalidateList();
			if (variables.body?.state === "UNINSTALLED") {
				toast.success("Login provider disconnected");
			}
		},
		onError: (err, variables) => {
			const message =
				variables.body?.state === "SUSPENDED"
					? "Could not suspend provider"
					: variables.body?.state === "UNINSTALLED"
						? "Could not disconnect provider"
						: "Could not reactivate provider";
			toast.error(message, { description: problemDetailOf(err) });
		},
	});

	const pendingState = updateStatus.isPending ? updateStatus.variables?.body?.state : undefined;
	const pendingId = updateStatus.isPending ? updateStatus.variables?.path.id : undefined;

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
									path: { workspaceSlug },
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
									workspaceSlug={workspaceSlug}
									provider={provider}
									callbackUrl={
										provider.id != null
											? callbackUrlFor(apiOrigin, provider.kind, provider.id)
											: undefined
									}
									highlightCallback={justCreated?.id === provider.id}
									isSuspending={pendingId === provider.id && pendingState === "SUSPENDED"}
									isReactivating={pendingId === provider.id && pendingState === "ACTIVE"}
									isDisconnecting={pendingId === provider.id && pendingState === "UNINSTALLED"}
									onSuspend={(reason) =>
										provider.id != null &&
										updateStatus.mutate({
											path: { workspaceSlug, id: provider.id },
											body: { state: "SUSPENDED", reason },
										})
									}
									onReactivate={() =>
										provider.id != null &&
										updateStatus.mutate({
											path: { workspaceSlug, id: provider.id },
											body: { state: "ACTIVE" },
										})
									}
									onDisconnect={() =>
										provider.id != null &&
										updateStatus.mutate({
											path: { workspaceSlug, id: provider.id },
											body: { state: "UNINSTALLED" },
										})
									}
								/>
							))}
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
