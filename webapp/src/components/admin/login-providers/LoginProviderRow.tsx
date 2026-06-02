import { useQuery } from "@tanstack/react-query";
import { CheckIcon, CopyIcon, HistoryIcon, PauseIcon, PlayIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";
import { auditOptions } from "@/api/@tanstack/react-query.gen";
import type { ConnectionSummary } from "@/api/types.gen";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Sheet,
	SheetContent,
	SheetDescription,
	SheetHeader,
	SheetTitle,
	SheetTrigger,
} from "@/components/ui/sheet";
import { Spinner } from "@/components/ui/spinner";
import {
	type LoginProviderKind,
	PROVIDER_LABELS,
	stateBadgeVariant,
	stateLabel,
} from "./loginProviders";

export interface LoginProviderRowProps {
	workspaceSlug: string;
	provider: ConnectionSummary & { kind: LoginProviderKind };
	/** Pre-built OAuth callback URL the admin must register in their IdP app. */
	callbackUrl?: string;
	/** Emphasize the callback URL right after creation (admin needs to copy it). */
	highlightCallback?: boolean;
	isSuspending: boolean;
	isReactivating: boolean;
	isDisconnecting: boolean;
	onSuspend: (reason?: string) => void;
	onReactivate: () => void;
	onDisconnect: () => void;
}

export function LoginProviderRow({
	workspaceSlug,
	provider,
	callbackUrl,
	highlightCallback = false,
	isSuspending,
	isReactivating,
	isDisconnecting,
	onSuspend,
	onReactivate,
	onDisconnect,
}: LoginProviderRowProps) {
	const [copied, setCopied] = useState(false);
	const [auditOpen, setAuditOpen] = useState(false);
	const isTerminal = provider.state === "UNINSTALLED";

	const copyCallback = () => {
		if (!callbackUrl) return;
		navigator.clipboard
			.writeText(callbackUrl)
			.then(() => {
				setCopied(true);
				setTimeout(() => setCopied(false), 2000);
			})
			.catch(() => setCopied(false));
	};

	return (
		<div className="rounded-lg border p-4">
			<div className="flex items-start justify-between gap-4">
				<div className="min-w-0 space-y-1">
					<div className="flex items-center gap-2">
						<span className="truncate font-medium">{provider.displayName ?? "Login provider"}</span>
						<Badge variant={stateBadgeVariant(provider.state)}>{stateLabel(provider.state)}</Badge>
					</div>
					<p className="text-sm text-muted-foreground">
						{PROVIDER_LABELS[provider.kind]}
						{provider.instanceKey ? ` · ${provider.instanceKey}` : ""}
					</p>
					{provider.state === "SUSPENDED" && provider.stateReason && (
						<p className="text-xs text-muted-foreground">Reason: {provider.stateReason}</p>
					)}
				</div>

				<div className="flex shrink-0 items-center gap-1">
					<Sheet open={auditOpen} onOpenChange={setAuditOpen}>
						<SheetTrigger
							render={
								<Button variant="ghost" size="icon" aria-label="View audit history">
									<HistoryIcon className="size-4" />
								</Button>
							}
						/>
						<AuditSheet workspaceSlug={workspaceSlug} connectionId={provider.id} open={auditOpen} />
					</Sheet>

					{provider.state === "ACTIVE" && (
						<Button
							variant="outline"
							size="sm"
							onClick={() => onSuspend()}
							disabled={isSuspending}
							aria-label={`Suspend ${provider.displayName ?? "provider"}`}
						>
							{isSuspending ? (
								<Spinner className="mr-2" />
							) : (
								<PauseIcon className="mr-2 size-3.5" />
							)}
							Suspend
						</Button>
					)}

					{provider.state === "SUSPENDED" && (
						<Button
							variant="outline"
							size="sm"
							onClick={onReactivate}
							disabled={isReactivating}
							aria-label={`Reactivate ${provider.displayName ?? "provider"}`}
						>
							{isReactivating ? (
								<Spinner className="mr-2" />
							) : (
								<PlayIcon className="mr-2 size-3.5" />
							)}
							Reactivate
						</Button>
					)}

					{!isTerminal && (
						<AlertDialog>
							<AlertDialogTrigger
								render={
									<Button
										variant="ghost"
										size="icon"
										aria-label={`Disconnect ${provider.displayName ?? "provider"}`}
										disabled={isDisconnecting}
									>
										<Trash2Icon className="size-4 text-destructive" />
									</Button>
								}
							/>
							<AlertDialogContent>
								<AlertDialogHeader>
									<AlertDialogTitle>
										Disconnect {provider.displayName ?? "this provider"}?
									</AlertDialogTitle>
									<AlertDialogDescription>
										Members will no longer be able to sign in with this provider. This is permanent
										— reconnecting requires registering a fresh OAuth app. Existing sessions are
										unaffected until they expire.
									</AlertDialogDescription>
								</AlertDialogHeader>
								<AlertDialogFooter>
									<AlertDialogCancel>Cancel</AlertDialogCancel>
									<AlertDialogAction
										variant="destructive"
										onClick={onDisconnect}
										disabled={isDisconnecting}
									>
										Disconnect
									</AlertDialogAction>
								</AlertDialogFooter>
							</AlertDialogContent>
						</AlertDialog>
					)}
				</div>
			</div>

			{callbackUrl && !isTerminal && (
				<div
					className={
						highlightCallback
							? "mt-3 rounded-md border border-primary/40 bg-primary/5 p-3"
							: "mt-3 rounded-md border bg-muted/40 p-3"
					}
				>
					<p className="mb-1 text-xs font-medium">Callback URL</p>
					<p className="mb-2 text-xs text-muted-foreground">
						Register this URL as the authorization callback / redirect URI in your provider's OAuth
						app. Most providers let you edit the redirect URI after creating the app.
					</p>
					<div className="flex items-center gap-2">
						<code className="min-w-0 flex-1 truncate rounded bg-background px-2 py-1 text-xs">
							{callbackUrl}
						</code>
						<Button
							type="button"
							variant="outline"
							size="sm"
							onClick={copyCallback}
							aria-label="Copy callback URL"
						>
							{copied ? (
								<CheckIcon className="mr-1.5 size-3.5 text-provider-success-foreground" />
							) : (
								<CopyIcon className="mr-1.5 size-3.5" />
							)}
							{copied ? "Copied" : "Copy"}
						</Button>
					</div>
					<span className="sr-only" role="status" aria-live="polite">
						{copied ? "Callback URL copied to clipboard" : ""}
					</span>
				</div>
			)}
		</div>
	);
}

function AuditSheet({
	workspaceSlug,
	connectionId,
	open,
}: {
	workspaceSlug: string;
	connectionId?: number;
	/** Only fetch audit history once the sheet is open — collapsed rows must not fan out N requests. */
	open: boolean;
}) {
	const { data, isLoading, error } = useQuery({
		...auditOptions({ path: { workspaceSlug, id: connectionId ?? 0 } }),
		enabled: open && connectionId != null,
	});

	return (
		<SheetContent className="w-full sm:max-w-md">
			<SheetHeader>
				<SheetTitle>Audit history</SheetTitle>
				<SheetDescription>Lifecycle events recorded for this login provider.</SheetDescription>
			</SheetHeader>
			<div className="space-y-3 overflow-y-auto px-4 pb-4" aria-live="polite">
				{isLoading && <Spinner className="size-5" />}
				{error && !isLoading && (
					<p className="text-sm text-destructive" role="alert">
						Failed to load audit history.
					</p>
				)}
				{!isLoading && !error && (data?.length ?? 0) === 0 && (
					<p className="text-sm text-muted-foreground">No audit events yet.</p>
				)}
				{data?.map((entry, index) => (
					<div
						key={`${entry.occurredAt?.toISOString() ?? index}-${entry.eventType ?? "event"}`}
						className="rounded-md border p-3 text-sm"
					>
						<div className="flex items-center justify-between gap-2">
							<span className="font-medium">{entry.eventType ?? "Event"}</span>
							<span className="text-xs text-muted-foreground">
								{entry.occurredAt ? entry.occurredAt.toLocaleString() : ""}
							</span>
						</div>
						<p className="mt-1 text-xs text-muted-foreground">
							{entry.fromState ?? "—"} → {entry.toState ?? "—"}
							{entry.actorRef ? ` · by ${entry.actorRef}` : ""}
						</p>
					</div>
				))}
			</div>
		</SheetContent>
	);
}
