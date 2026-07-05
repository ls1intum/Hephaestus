import { BookTextIcon, CheckIcon, ExternalLinkIcon } from "lucide-react";
import { useState } from "react";
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
import { Textarea } from "@/components/ui/textarea";

export interface OutlineConnectInput {
	serverUrl: string;
	token: string;
	/** Raw allow-list text (comma- or newline-separated) exactly as typed; the container normalizes it. */
	collectionAllowList: string;
}

export interface OutlineIntegrationCardProps {
	connected: boolean;
	/** Label for the connected instance (team name or id), shown next to the connected state. */
	connectionLabel?: string;
	isConnecting?: boolean;
	isDisconnecting?: boolean;
	/** Connect error surfaced inline under the form. */
	errorMessage?: string;
	onConnect: (input: OutlineConnectInput) => void;
	onDisconnect: () => void;
}

// Client-side format hint only — the server re-validates the URL through the SSRF guard on connect.
const HTTPS_URL = /^https:\/\/.+/i;
const DEFAULT_SERVER_URL = "https://app.getoutline.com";

/**
 * Workspace-admin card for the Outline documentation integration. When disconnected it captures the
 * Outline server URL, an API token, and a collection allow-list, then hands them to the generic connection
 * initiate flow. When connected it shows the linked instance and a guarded disconnect action.
 *
 * <p>Pure presentation: all data fetching and mutations live in the container that renders this card.
 */
export function OutlineIntegrationCard({
	connected,
	connectionLabel,
	isConnecting = false,
	isDisconnecting = false,
	errorMessage,
	onConnect,
	onDisconnect,
}: OutlineIntegrationCardProps) {
	const [serverUrl, setServerUrl] = useState(DEFAULT_SERVER_URL);
	const [token, setToken] = useState("");
	const [collections, setCollections] = useState("");
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
							Mirror allow-listed Outline collections so their design docs and decision records
							reach practice detection as context. Use a dedicated bot-user API token; only the
							collections you list are read.
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

								<Field>
									<FieldLabel htmlFor="outline-collections">Collection allow-list</FieldLabel>
									<Textarea
										id="outline-collections"
										value={collections}
										disabled={isConnecting}
										onChange={(e) => setCollections(e.target.value)}
										placeholder={"Engineering\nArchitecture Decisions"}
										rows={3}
									/>
									<FieldDescription>
										One collection name, URL id, or id per line (or comma-separated). Only these
										collections are mirrored. Leave blank to mirror none until you scope it.
									</FieldDescription>
								</Field>

								{errorMessage && <FieldError>{errorMessage}</FieldError>}

								<Button
									onClick={() =>
										onConnect({
											serverUrl: serverUrl.trim(),
											token: token.trim(),
											collectionAllowList: collections,
										})
									}
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

								<div className="flex justify-end border-t pt-4">
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
							erased. You can reconnect later with a token.
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
