import { differenceInCalendarDays, format, formatDistanceToNow } from "date-fns";
import {
	CheckIcon,
	FileTextIcon,
	KeyRoundIcon,
	RefreshCwIcon,
	TriangleAlertIcon,
	WebhookIcon,
	ZapOffIcon,
} from "lucide-react";
import { useState } from "react";
import type { OutlineConnectionStatus, OutlineTokenStatus } from "@/api/types.gen";
import { OutlineIcon } from "@/components/icons/brand";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
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
import { Card, CardContent, CardDescription, CardFooter, CardHeader } from "@/components/ui/card";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";

export interface OutlineConnectInput {
	serverUrl: string;
	token: string;
}

export interface OutlineConnectCardProps {
	connected: boolean;
	connectionLabel?: string;
	status?: OutlineConnectionStatus;
	isStatusLoading?: boolean;
	tokenStatus?: OutlineTokenStatus;
	isTokenStatusLoading?: boolean;
	isConnecting?: boolean;
	isDisconnecting?: boolean;
	isSyncing?: boolean;
	errorMessage?: string;
	/** This instance has no Outline integration, so the connect form is a dead end and the card says so. */
	connectUnavailable?: boolean;
	onConnect: (input: OutlineConnectInput) => void;
	onDisconnect: () => void;
	onSyncNow: () => void;
}

// Client-side format hint only — the server re-validates the URL through the SSRF guard on connect.
const HTTPS_URL = /^https:\/\/.+/i;
const CLOUD_SERVER_URL = "https://app.getoutline.com";

/** Inside this window the admin has to act: Outline keys cannot be rotated through the API. */
const EXPIRY_WARNING_DAYS = 14;

/**
 * The API fields carry `Date` types, but this repo does not wire hey-api's date transformers —
 * at runtime they arrive as ISO strings. Normalize both shapes and drop anything unparseable.
 */
function asDate(value: Date | string | undefined): Date | undefined {
	if (value == null) return undefined;
	const date = value instanceof Date ? value : new Date(value);
	return Number.isNaN(date.getTime()) ? undefined : date;
}

/**
 * Workspace-admin card for the Outline integration: captures the server URL and API token when disconnected,
 * and shows the linked instance, health, token state, sync and a guarded disconnect when connected. Which
 * collections are mirrored is managed in {@link OutlineCollectionsSection}. Pure presentation.
 */
export function OutlineConnectCard({
	connected,
	connectionLabel,
	status,
	isStatusLoading = false,
	tokenStatus,
	isTokenStatusLoading = false,
	isConnecting = false,
	isDisconnecting = false,
	isSyncing = false,
	errorMessage,
	connectUnavailable = false,
	onConnect,
	onDisconnect,
	onSyncNow,
}: OutlineConnectCardProps) {
	const [serverUrl, setServerUrl] = useState("");
	const [token, setToken] = useState("");
	const [disconnectOpen, setDisconnectOpen] = useState(false);

	const serverUrlInvalid = serverUrl.length > 0 && !HTTPS_URL.test(serverUrl.trim());
	const canConnect = HTTPS_URL.test(serverUrl.trim()) && token.trim().length > 0 && !isConnecting;

	return (
		<div className="space-y-6">
			<Card>
				<CardHeader>
					{/* CardTitle renders a div; the settings page is navigated by heading, so the
					    section title has to be a real h2 — matching the Slack integration card. */}
					<h2
						data-slot="card-title"
						className="flex items-center gap-2 text-base leading-snug font-medium"
					>
						<OutlineIcon className="size-4" aria-hidden />
						Outline documentation
					</h2>
					<CardDescription>
						Mirror Outline collections so their design docs and decision records reach practice
						detection as context. Use a dedicated bot-user API token; after connecting you choose
						exactly which collections are mirrored.
					</CardDescription>
				</CardHeader>

				<CardContent className="space-y-4">
					{!connected ? (
						<FieldGroup>
							<Field data-invalid={serverUrlInvalid}>
								<FieldLabel htmlFor="outline-server-url">Server URL</FieldLabel>
								<Input
									id="outline-server-url"
									value={serverUrl}
									disabled={isConnecting}
									onChange={(e) => setServerUrl(e.target.value)}
									// Placeholder, never a default value: a prefilled cloud URL would ship a
									// self-hoster's token to Outline Cloud if they only pasted the token.
									placeholder={CLOUD_SERVER_URL}
									autoComplete="off"
									aria-invalid={serverUrlInvalid}
								/>
								<FieldDescription>
									Your Outline host — <code>{CLOUD_SERVER_URL}</code> for Outline Cloud, otherwise
									your self-hosted URL.
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
									Create it in Outline under <em>Settings → API Keys</em>. A dedicated bot-user
									token is recommended.
								</FieldDescription>
							</Field>

							{errorMessage && <FieldError>{errorMessage}</FieldError>}

							{connectUnavailable && (
								<Alert variant="warning">
									<TriangleAlertIcon />
									<AlertTitle>Outline may not be enabled on this instance</AlertTitle>
									<AlertDescription>
										The server has no Outline integration configured, so connecting cannot succeed
										here. If your URL and token are correct, ask your server administrator to enable
										the Outline integration for this deployment.
									</AlertDescription>
								</Alert>
							)}

							<Button
								onClick={() => onConnect({ serverUrl: serverUrl.trim(), token: token.trim() })}
								disabled={!canConnect}
								className="w-full"
							>
								{isConnecting && <Spinner />}
								{isConnecting ? "Connecting…" : "Connect Outline"}
							</Button>
						</FieldGroup>
					) : (
						<>
							<div className="flex items-center gap-2 text-sm">
								<CheckIcon className="size-4 text-success" aria-hidden />
								<span>
									Outline connected
									{connectionLabel ? ` — ${connectionLabel}` : ""}
								</span>
							</div>

							{isStatusLoading ? (
								<Skeleton className="h-5 w-full max-w-md" />
							) : (
								status && (
									<>
										<div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
											{status.webhookRegistered ? (
												<span className="flex items-center gap-1.5">
													<WebhookIcon className="size-4 text-success" aria-hidden />
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
											{status.syncRunning && (
												<span className="flex items-center gap-1.5">
													<RefreshCwIcon className="size-4 animate-spin" aria-hidden />
													Sync in progress…
												</span>
											)}
										</div>
										{status.erroredCollections > 0 && (
											<p className="flex items-center gap-1.5 text-sm text-destructive">
												<TriangleAlertIcon className="size-4" aria-hidden />
												{status.erroredCollections} collection
												{status.erroredCollections === 1 ? "" : "s"} hit a sync error — check the
												collections list below.
											</p>
										)}
									</>
								)
							)}

							<OutlineTokenPanel tokenStatus={tokenStatus} isLoading={isTokenStatusLoading} />
						</>
					)}
				</CardContent>

				{connected && (
					<CardFooter className="justify-between gap-2">
						<Button variant="outline" size="sm" onClick={onSyncNow} disabled={isSyncing}>
							{isSyncing ? <Spinner /> : <RefreshCwIcon className="size-4" />}
							{isSyncing ? "Starting sync…" : "Sync now"}
						</Button>
						<Button
							variant="destructive-outline"
							size="sm"
							onClick={() => setDisconnectOpen(true)}
							disabled={isDisconnecting}
						>
							{isDisconnecting ? "Disconnecting…" : "Disconnect Outline…"}
						</Button>
					</CardFooter>
				)}
			</Card>

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

interface OutlineTokenPanelProps {
	tokenStatus?: OutlineTokenStatus;
	isLoading: boolean;
}

/**
 * The state of the stored API key. Outline keys cannot rotate themselves through the API, so the most this can do
 * is warn early. Rejection and imminent expiry both silently kill the mirror, so both are alerts, not muted text.
 */
function OutlineTokenPanel({ tokenStatus, isLoading }: OutlineTokenPanelProps) {
	if (isLoading) {
		return <Skeleton className="h-5 w-64" />;
	}
	if (!tokenStatus) {
		return null;
	}

	if (!tokenStatus.accepted) {
		return (
			<Alert variant="destructive">
				<TriangleAlertIcon />
				<AlertTitle>Outline no longer accepts this token — reconnect with a new one</AlertTitle>
				<AlertDescription>
					Syncing is stopped until a working API key is stored. Create a key in Outline under{" "}
					<strong>Settings → API Keys</strong>, then disconnect and reconnect here with it.
				</AlertDescription>
			</Alert>
		);
	}

	const expiresAt = asDate(tokenStatus.expiresAt);
	const lastActiveAt = asDate(tokenStatus.lastActiveAt);
	// A scoped key (or one owned by a user who cannot see it) cannot list itself, so Outline accepts
	// the token while telling us nothing about it. Say only what we know.
	const hasMetadata =
		tokenStatus.name != null ||
		tokenStatus.last4 != null ||
		expiresAt != null ||
		lastActiveAt != null;

	const daysLeft = expiresAt ? differenceInCalendarDays(expiresAt, new Date()) : undefined;
	const expiringSoon = daysLeft != null && daysLeft <= EXPIRY_WARNING_DAYS;

	return (
		<div className="space-y-2">
			<div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
				<span className="flex items-center gap-1.5">
					<KeyRoundIcon className="size-4 text-success" aria-hidden />
					Outline accepts this token
				</span>
				{hasMetadata && (tokenStatus.name || tokenStatus.last4) && (
					<span>
						{tokenStatus.name ?? "API key"}
						{tokenStatus.last4 ? ` (…${tokenStatus.last4})` : ""}
					</span>
				)}
				{lastActiveAt && (
					<span>Last used {formatDistanceToNow(lastActiveAt, { addSuffix: true })}</span>
				)}
				{hasMetadata && !expiresAt && <span>Never expires</span>}
				{expiresAt && !expiringSoon && (
					<span>
						Expires in {daysLeft} day{daysLeft === 1 ? "" : "s"} (on{" "}
						{format(expiresAt, "d MMM yyyy")})
					</span>
				)}
			</div>

			{expiresAt && expiringSoon && (
				<Alert variant="warning">
					<TriangleAlertIcon />
					<AlertTitle>
						{daysLeft != null && daysLeft <= 0
							? `This API key expired on ${format(expiresAt, "d MMM yyyy")}`
							: `This API key expires in ${daysLeft} day${daysLeft === 1 ? "" : "s"} (on ${format(expiresAt, "d MMM yyyy")})`}
					</AlertTitle>
					<AlertDescription>
						Outline API keys cannot be rotated through the API, so create a fresh key in Outline
						under <strong>Settings → API Keys</strong> and re-enter it here before this one lapses —
						the mirror stops the moment it does.
					</AlertDescription>
				</Alert>
			)}
		</div>
	);
}
