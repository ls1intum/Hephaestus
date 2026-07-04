import { PlusIcon, RadioIcon } from "lucide-react";
import { useState } from "react";
import type { SlackMonitoredChannel } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ActivateChannelDialog } from "./slack-channels/ActivateChannelDialog";
import { AddChannelDialog } from "./slack-channels/AddChannelDialog";
import { ChannelHistorySheet } from "./slack-channels/ChannelHistorySheet";
import { RemoveChannelAlertDialog } from "./slack-channels/RemoveChannelAlertDialog";
import { SlackChannelRow, type SlackConsentState } from "./slack-channels/SlackChannelRow";

export type { SlackConsentState };

export interface AdminSlackChannelsSettingsProps {
	workspaceSlug: string;
	/** Whether the workspace has an installed Slack app; gates the Add-channel affordance. */
	hasSlackConnection: boolean;
	channels: SlackMonitoredChannel[];
	isLoading: boolean;
	/** Allow-list a new channel (lands PENDING). Resolves on success, rejects to keep the dialog open. */
	onRegisterChannel: (input: {
		slackChannelId: string;
		channelName?: string;
	}) => Promise<void> | void;
	/** Drive activate / pause / resume via the target consent state. */
	onUpdateConsent: (input: {
		slackChannelId: string;
		consentState: SlackConsentState;
		reason?: string;
	}) => Promise<void> | void;
	/** Terminal revoke + erase. Resolves on success, rejects to keep the dialog open. */
	onRemoveChannel: (input: { slackChannelId: string; reason?: string }) => Promise<void> | void;
}

/**
 * Admin surface to allow-list Slack channels and drive their per-channel consent lifecycle
 * (PENDING → ACTIVE ⇄ PAUSED → REVOKED + erase). Self-contained section so promoting it to a
 * dedicated /admin/slack route later is a move, not a rewrite. Pure: all data + mutations
 * live in the route container.
 */
export function AdminSlackChannelsSettings({
	workspaceSlug,
	hasSlackConnection,
	channels,
	isLoading,
	onRegisterChannel,
	onUpdateConsent,
	onRemoveChannel,
}: AdminSlackChannelsSettingsProps) {
	const [addOpen, setAddOpen] = useState(false);
	const [activateChannel, setActivateChannel] = useState<SlackMonitoredChannel | null>(null);
	const [removeChannel, setRemoveChannel] = useState<SlackMonitoredChannel | null>(null);
	const [historyChannel, setHistoryChannel] = useState<SlackMonitoredChannel | null>(null);

	const hasChannels = channels.length > 0;

	return (
		<div className="space-y-6">
			<div>
				<div className="mb-4 flex items-center justify-between gap-4">
					<h2 className="text-lg font-semibold">Slack channel monitoring</h2>
					<Button size="sm" disabled={!hasSlackConnection} onClick={() => setAddOpen(true)}>
						<PlusIcon className="size-4" />
						Add channel
					</Button>
				</div>

				<Card>
					<CardContent className="space-y-4">
						<p className="text-muted-foreground text-sm">
							Monitored channels have their <strong>new</strong> messages read to generate AI
							practice feedback. Reading is <strong>forward-only</strong> (never past history), each
							monitored channel gets a <strong>visible in-channel announcement</strong>, and any
							member can <strong>opt out</strong> from the app's Home tab. Removing a channel{" "}
							<strong>permanently erases</strong> everything collected from it.
						</p>

						{!hasSlackConnection && (
							<p className="text-muted-foreground text-sm">
								Connect a Slack workspace above to start monitoring channels.
							</p>
						)}

						{isLoading ? (
							<div className="space-y-2">
								<Skeleton className="h-10 w-full" />
								<Skeleton className="h-10 w-full" />
								<Skeleton className="h-10 w-full" />
							</div>
						) : hasChannels ? (
							<Table>
								<TableHeader>
									<TableRow>
										<TableHead>Channel</TableHead>
										<TableHead>Status</TableHead>
										<TableHead>Opted out</TableHead>
										<TableHead>Announced</TableHead>
										<TableHead className="w-0 text-right">
											<span className="sr-only">Actions</span>
										</TableHead>
									</TableRow>
								</TableHeader>
								<TableBody>
									{channels.map((channel) => (
										<SlackChannelRow
											key={channel.slackChannelId}
											channel={channel}
											onActivate={setActivateChannel}
											onPause={(c) =>
												onUpdateConsent({
													slackChannelId: c.slackChannelId,
													consentState: "PAUSED",
												})
											}
											onResume={(c) =>
												onUpdateConsent({
													slackChannelId: c.slackChannelId,
													consentState: "ACTIVE",
												})
											}
											onRemove={setRemoveChannel}
											onViewHistory={setHistoryChannel}
										/>
									))}
								</TableBody>
							</Table>
						) : (
							<Empty>
								<EmptyHeader>
									<EmptyMedia variant="icon">
										<RadioIcon />
									</EmptyMedia>
									<EmptyTitle>No channels monitored yet</EmptyTitle>
									<EmptyDescription>
										Allow-list a Slack channel to start generating AI practice feedback from its
										conversations. You choose exactly when monitoring begins.
									</EmptyDescription>
								</EmptyHeader>
								<EmptyContent>
									<Button size="sm" disabled={!hasSlackConnection} onClick={() => setAddOpen(true)}>
										<PlusIcon className="size-4" />
										Add channel
									</Button>
								</EmptyContent>
							</Empty>
						)}
					</CardContent>
				</Card>
			</div>

			<AddChannelDialog
				key={addOpen ? "add-open" : "add-closed"}
				open={addOpen}
				onOpenChange={setAddOpen}
				onSubmit={onRegisterChannel}
			/>

			<ActivateChannelDialog
				channel={activateChannel}
				onOpenChange={(open) => {
					if (!open) setActivateChannel(null);
				}}
				onConfirm={(c) =>
					onUpdateConsent({ slackChannelId: c.slackChannelId, consentState: "ACTIVE" })
				}
			/>

			<RemoveChannelAlertDialog
				key={removeChannel?.slackChannelId ?? "remove-closed"}
				channel={removeChannel}
				onOpenChange={(open) => {
					if (!open) setRemoveChannel(null);
				}}
				onConfirm={onRemoveChannel}
			/>

			<ChannelHistorySheet
				workspaceSlug={workspaceSlug}
				channel={historyChannel}
				onOpenChange={(open) => {
					if (!open) setHistoryChannel(null);
				}}
			/>
		</div>
	);
}
