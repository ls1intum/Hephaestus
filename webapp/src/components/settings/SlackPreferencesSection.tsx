import type { SlackWorkspacePreferences } from "@/api/types.gen";
import { SlackIcon } from "@/components/icons/brand";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";

export interface SlackPreferencesSectionProps {
	workspaces: SlackWorkspacePreferences[];
	isSlackLinked: boolean;
	canConnectSlack: boolean;
	onConnectSlack: () => void;
	onToggleChannelMessages: (workspaceSlug: string, channelMessagesAllowed: boolean) => void;
	updatingWorkspaceSlug?: string | null;
	isLoading?: boolean;
	isError?: boolean;
	onRetry?: () => void;
}

export function SlackPreferencesSection({
	workspaces,
	isSlackLinked,
	canConnectSlack,
	onConnectSlack,
	onToggleChannelMessages,
	updatingWorkspaceSlug = null,
	isLoading = false,
	isError = false,
	onRetry,
}: SlackPreferencesSectionProps) {
	return (
		<section className="space-y-4" aria-labelledby="slack-preferences-heading">
			<div className="space-y-1">
				<div className="flex items-center gap-2">
					<SlackIcon className="size-5" aria-hidden="true" />
					<h2 id="slack-preferences-heading" className="text-xl font-semibold">
						Slack
					</h2>
				</div>
				<p className="text-sm text-muted-foreground">
					Manage Slack account linking and whether your new messages in monitored channels can
					support mentoring context.
				</p>
			</div>

			{isLoading ? (
				<div className="flex justify-center py-6">
					<Spinner aria-label="Loading Slack preferences" />
				</div>
			) : isError ? (
				<div className="space-y-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4">
					<p className="text-sm text-destructive" role="alert">
						We couldn't load your Slack preferences.
					</p>
					{onRetry && (
						<Button variant="outline" size="sm" onClick={onRetry}>
							Retry
						</Button>
					)}
				</div>
			) : !isSlackLinked ? (
				<div className="flex flex-col gap-3 rounded-lg border p-4 sm:flex-row sm:items-center sm:justify-between">
					<div className="space-y-1">
						<p className="text-sm font-medium">Slack is not connected</p>
						<p className="text-sm text-muted-foreground">
							Connect Slack to manage your channel-message preference from Hephaestus.
						</p>
					</div>
					{canConnectSlack ? (
						<Button variant="outline" size="sm" onClick={onConnectSlack}>
							<SlackIcon className="mr-1.5 size-3.5" aria-hidden="true" />
							Connect Slack
						</Button>
					) : (
						<Badge variant="secondary">Not available</Badge>
					)}
				</div>
			) : workspaces.length === 0 ? (
				<div className="space-y-2 rounded-lg border p-4">
					<div className="flex items-center gap-2">
						<p className="text-sm font-medium">Slack is connected</p>
						<Badge variant="secondary">Connected</Badge>
					</div>
					<p className="text-sm text-muted-foreground">
						No linked Hephaestus workspace currently has this Slack workspace installed.
					</p>
				</div>
			) : (
				<div className="space-y-3">
					{workspaces.map((workspace) => (
						<WorkspacePreferenceRow
							key={workspace.workspaceSlug}
							workspace={workspace}
							isUpdating={updatingWorkspaceSlug === workspace.workspaceSlug}
							onToggleChannelMessages={onToggleChannelMessages}
						/>
					))}
				</div>
			)}
		</section>
	);
}

interface WorkspacePreferenceRowProps {
	workspace: SlackWorkspacePreferences;
	isUpdating: boolean;
	onToggleChannelMessages: (workspaceSlug: string, channelMessagesAllowed: boolean) => void;
}

function WorkspacePreferenceRow({
	workspace,
	isUpdating,
	onToggleChannelMessages,
}: WorkspacePreferenceRowProps) {
	const switchId = `slack-channel-messages-${workspace.workspaceSlug}`;
	const channelMessagesAllowed = workspace.channelMessagesAllowed ?? true;

	return (
		<div
			role="group"
			aria-label={`${workspace.workspaceName} Slack preferences`}
			className="space-y-4 rounded-lg border p-4"
		>
			<div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
				<div className="space-y-1">
					<div className="flex flex-wrap items-center gap-2">
						<h3 className="text-sm font-medium">{workspace.workspaceName}</h3>
						<Badge variant={channelMessagesAllowed ? "secondary" : "outline"}>
							{channelMessagesAllowed ? "Message use on" : "Message use off"}
						</Badge>
					</div>
					<p className="text-xs text-muted-foreground">
						Slack workspace {workspace.slackTeamName ?? workspace.slackTeamId}
					</p>
					<p className="text-xs text-muted-foreground">
						{channelCountText(workspace.activeMonitoredChannelCount)}
					</p>
				</div>
			</div>

			<div className="flex items-start justify-between gap-6">
				<div className="space-y-1">
					<Label htmlFor={switchId} className="text-sm font-medium">
						Use my new channel messages
					</Label>
					<p className="text-sm text-muted-foreground leading-relaxed">
						When this is on, new messages you send in monitored Slack channels can be used as
						context for your private mentor conversations. Turning it off deletes already collected
						channel-message data for you in this workspace.
					</p>
				</div>
				<Switch
					id={switchId}
					className="mt-1"
					checked={channelMessagesAllowed}
					onCheckedChange={(checked) => onToggleChannelMessages(workspace.workspaceSlug, checked)}
					disabled={isUpdating}
					aria-busy={isUpdating}
				/>
			</div>
		</div>
	);
}

function channelCountText(count?: number): string {
	const value = count ?? 0;
	return value === 1 ? "1 active monitored channel" : `${value} active monitored channels`;
}
