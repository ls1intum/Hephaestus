import { formatDistanceToNow } from "date-fns";
import {
	BookTextIcon,
	CheckIcon,
	ExternalLinkIcon,
	FileTextIcon,
	RefreshCwIcon,
	WebhookIcon,
	ZapOffIcon,
} from "lucide-react";
import { useState } from "react";
import type { OutlineConnectionStatus } from "@/api/types.gen";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";

export interface OutlineConnectInput {
	serverUrl: string;
	token: string;
}

export interface OutlineConnectCardProps {
	connected: boolean;
	/** Label for the connected instance (team name or id), shown next to the connected state. */
	connectionLabel?: string;
	/** Connection health (webhook, document count, last sync), shown while connected. */
	status?: OutlineConnectionStatus;
	/** The status query is still resolving — show a placeholder line instead of stale zeros. */
	isStatusLoading?: boolean;
	isConnecting?: boolean;
	isDisconnecting?: boolean;
	/** The fire-and-forget full reconcile has been requested and not yet acknowledged. */
	isSyncing?: boolean;
	/** Connect error surfaced inline under the form. */
	errorMessage?: string;
	onConnect: (input: OutlineConnectInput) => void;
	onDisconnect: () => void;
	/** Trigger the full reconcile (202 fire-and-forget). */
	onSyncNow: () => void;
}

// Client-side format hint only — the server re-validates the URL through the SSRF guard on connect.
const HTTPS_URL = /^https:\/\/.+/i;
const DEFAULT_SERVER_URL = "https://app.getoutline.com";

/**
 * Workspace-admin card for the Outline documentation integration. When disconnected it captures the
 * Outline server URL and an API token, then hands them to the generic connection initiate flow.
 * When connected it shows the linked instance, a health line (webhook vs polling, document count,
 * last sync), a Sync-now action, and a guarded disconnect. Which collections are mirrored is managed
 * post-connect in {@link OutlineCollectionsSection}.
 *
 * <p>Pure presentation: all data fetching and mutations live in the container that renders this card.
 */
export function OutlineConnectCard({
	connected,
	connectionLabel,
	status,
	isStatusLoading = false,
	isConnecting = false,
	isDisconnecting = false,
	isSyncing = false,
	errorMessage,
	onConnect,
	onDisconnect,
	onSyncNow,
}: OutlineConnectCardProps) {
	const [serverUrl, setServerUrl] = useState(DEFAULT_SERVER_URL);
	const [token, setToken] = useState("");
	const [disconnectOpen, setDisconnectOpen] = useState(false);

	const serverUrlInvalid = serverUrl.length > 0 && !HTTPS_URL.test(serverUrl.trim());
	const canConnect = HTTPS_URL.test(serverUrl.trim()) && token.trim().length > 0 && !isConnecting;

	return (
		<div className="space-y-6">
			<div>
				<h2 className="mb-4 flex items-center gap-2 text-lg font-semibold">
					<BookTextIcon className="size-5 text-muted-foreground" />
					Outline documentation
				</h2>
				<Card>
					<CardContent className="space-y-4">
						<p className="text-sm text-muted-foreground">
							Mirror Outline collections so their design docs and decision records reach practice
							detection as context. Use a dedicated bot-user API token; after connecting you choose
							exactly which collections are mirrored.
						</p>

						{!connected ? (
							<FieldGroup>
								<Field data-invalid={serverUrlInvalid}>
									<FieldLabel htmlFor="outline-server-url">Server URL</FieldLabel>
									<Input
										id="outline-server-url"
										value={serverUrl}
										disabled={isConnecting}
										onChange={(e) => setServerUrl(e.target.value)}
										placeholder={DEFAULT_SERVER_URL}
										autoComplete="off"
										aria-invalid={serverUrlInvalid}
									/>
									<FieldDescription>
										Outline Cloud (<code>{DEFAULT_SERVER_URL}</code>) or your self-hosted host.
									</FieldDescription>
									{serverUrlInvalid && <FieldError>Enter an https:// URL.</FieldError>}
								</Field>

								<Field>
									<FieldLabel htmlFor="outline-token">API token</FieldLabel>
									<Input
										id="outline-token"
										type="password"
										value={token}
										disabled={isConnecting}
										onChange={(e) => setToken(e.target.value)}
										placeholder="ol_api_…"
										autoComplete="off"
									/>
									<FieldDescription>
										Create it in Outline under <em>Settings → API tokens</em>. A dedicated bot-user
										token is recommended.
									</FieldDescription>
								</Field>

								{errorMessage && <FieldError>{errorMessage}</FieldError>}

								<Button
									onClick={() => onConnect({ serverUrl: serverUrl.trim(), token: token.trim() })}
									disabled={!canConnect}
									className="w-full"
								>
									{isConnecting ? "Connecting…" : "Connect Outline"}
									{!isConnecting && <ExternalLinkIcon className="ml-2 size-3.5" />}
								</Button>
							</FieldGroup>
						) : (
							<>
								<div className="flex items-center gap-2 text-sm">
									{/* no semantic success token in the kit */}
									<CheckIcon className="size-4 text-green-600 dark:text-green-400" />
									<span>
										Outline connected
										{connectionLabel ? ` — ${connectionLabel}` : ""}
									</span>
								</div>

								{isStatusLoading ? (
									<Skeleton className="h-5 w-full max-w-md" />
								) : (
									status && (
										<div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
											{status.webhookRegistered ? (
												<span className="flex items-center gap-1.5">
													<WebhookIcon
														className="size-4 text-green-600 dark:text-green-400"
														aria-hidden
													/>
													Live updates via webhook
												</span>
											) : (
												<span className="flex items-center gap-1.5">
													<ZapOffIcon className="size-4" aria-hidden />
													Polling only — webhook not registered
												</span>
											)}
											<span className="flex items-center gap-1.5">
												<FileTextIcon className="size-4" aria-hidden />
												{status.documentCount} document{status.documentCount === 1 ? "" : "s"}{" "}
												mirrored
											</span>
											<span>
												{status.lastSyncedAt
													? `Last synced ${formatDistanceToNow(new Date(status.lastSyncedAt), { addSuffix: true })}`
													: "Not synced yet"}
											</span>
										</div>
									)
								)}

								<div className="flex items-center justify-between gap-2 border-t pt-4">
									<Button variant="outline" size="sm" onClick={onSyncNow} disabled={isSyncing}>
										<RefreshCwIcon className="size-4" />
										{isSyncing ? "Starting sync…" : "Sync now"}
									</Button>
									<Button
										variant="outline"
										className="text-destructive"
										onClick={() => setDisconnectOpen(true)}
										disabled={isDisconnecting}
									>
										{isDisconnecting ? "Disconnecting…" : "Disconnect Outline…"}
									</Button>
								</div>
							</>
						)}
					</CardContent>
				</Card>
			</div>

			<AlertDialog open={disconnectOpen} onOpenChange={setDisconnectOpen}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Disconnect Outline?</AlertDialogTitle>
						<AlertDialogDescription>
							The document mirror stops syncing and every mirrored document for this workspace is
							erased. Documents in Outline itself are not affected. You can reconnect later with a
							token.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel disabled={isDisconnecting}>Cancel</AlertDialogCancel>
						<AlertDialogAction
							variant="destructive"
							disabled={isDisconnecting}
							onClick={() => {
								setDisconnectOpen(false);
								onDisconnect();
							}}
						>
							{isDisconnecting ? "Disconnecting…" : "Disconnect"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</div>
	);
}
