import { formatDistanceToNow } from "date-fns";
import {
	BanIcon,
	CheckIcon,
	ClockIcon,
	HistoryIcon,
	MoreHorizontalIcon,
	PauseIcon,
	PlayIcon,
	Trash2Icon,
} from "lucide-react";
import type { ReactNode } from "react";
import type { SlackMonitoredChannel } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { TableCell, TableRow } from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

/** The consent lifecycle states, sourced from the generated DTO so they never drift. */
export type SlackConsentState = SlackMonitoredChannel["consentState"];

interface StatusPresentation {
	label: string;
	icon: ReactNode;
	variant: "outline" | "secondary" | "destructive";
	/** Extra classes for the icon so ACTIVE reads green without relying on color alone. */
	iconClassName?: string;
}

/**
 * Word + icon for every state (never color-only) so the status survives WCAG 1.4.1.
 * There is no semantic "success" token in the kit, so ACTIVE reuses the same hard-coded
 * green as the connection indicator in {@link AdminSlackNotificationSettings}.
 */
function statusPresentation(state: SlackConsentState): StatusPresentation {
	switch (state) {
		case "ACTIVE":
			return {
				label: "Monitoring",
				icon: <CheckIcon aria-hidden />,
				variant: "outline",
				iconClassName: "text-green-600 dark:text-green-400",
			};
		case "PAUSED":
			return {
				label: "Paused",
				icon: <PauseIcon aria-hidden />,
				variant: "outline",
				iconClassName: "text-muted-foreground",
			};
		case "REVOKED":
			return { label: "Revoked", icon: <BanIcon aria-hidden />, variant: "destructive" };
		default:
			return { label: "Not started", icon: <ClockIcon aria-hidden />, variant: "secondary" };
	}
}

export interface SlackChannelRowProps {
	channel: SlackMonitoredChannel;
	/** Open the activation confirmation dialog for this channel (PENDING only). */
	onActivate: (channel: SlackMonitoredChannel) => void;
	/** Pause an ACTIVE channel (reversible, no confirm). */
	onPause: (channel: SlackMonitoredChannel) => void;
	/** Resume a PAUSED channel (reversible, no confirm). */
	onResume: (channel: SlackMonitoredChannel) => void;
	/** Open the type-to-confirm remove & erase dialog. */
	onRemove: (channel: SlackMonitoredChannel) => void;
	/** Open the consent-history sheet. */
	onViewHistory: (channel: SlackMonitoredChannel) => void;
}

/**
 * One allow-listed Slack channel: name/id, status badge, opted-out signal, announcement
 * time and a state-gated row action menu. Pure — every transition is delegated upward.
 */
export function SlackChannelRow({
	channel,
	onActivate,
	onPause,
	onResume,
	onRemove,
	onViewHistory,
}: SlackChannelRowProps) {
	const label = channel.channelName ?? channel.slackChannelId;
	const status = statusPresentation(channel.consentState);
	const isTerminal = channel.consentState === "REVOKED";

	return (
		<TableRow>
			<TableCell>
				<div className="font-medium">#{label}</div>
				<div className="text-muted-foreground font-mono text-xs">{channel.slackChannelId}</div>
			</TableCell>

			<TableCell>
				<Badge variant={status.variant} className="gap-1">
					<span className={status.iconClassName}>{status.icon}</span>
					{status.label}
				</Badge>
			</TableCell>

			<TableCell>
				{channel.optedOutMemberCount > 0 ? (
					<Tooltip>
						<TooltipTrigger
							render={
								<span className="cursor-help underline decoration-dotted underline-offset-2">
									{channel.optedOutMemberCount}
								</span>
							}
						/>
						<TooltipContent>
							{channel.optedOutMemberCount} workspace member
							{channel.optedOutMemberCount === 1 ? "" : "s"} opted out (App Home)
						</TooltipContent>
					</Tooltip>
				) : (
					<span className="text-muted-foreground">0</span>
				)}
			</TableCell>

			<TableCell className="text-muted-foreground text-sm">
				{channel.consentAnnouncedAt ? (
					formatDistanceToNow(new Date(channel.consentAnnouncedAt), { addSuffix: true })
				) : (
					<span aria-hidden>—</span>
				)}
			</TableCell>

			<TableCell className="text-right">
				<DropdownMenu>
					<DropdownMenuTrigger
						render={
							<Button variant="ghost" size="icon-sm" aria-label={`Actions for ${label}`}>
								<MoreHorizontalIcon className="size-4" />
							</Button>
						}
					/>
					<DropdownMenuContent align="end">
						{channel.consentState === "PENDING" && (
							<DropdownMenuItem onClick={() => onActivate(channel)}>
								<PlayIcon className="size-4" />
								Activate monitoring…
							</DropdownMenuItem>
						)}
						{channel.consentState === "ACTIVE" && (
							<DropdownMenuItem onClick={() => onPause(channel)}>
								<PauseIcon className="size-4" />
								Pause
							</DropdownMenuItem>
						)}
						{channel.consentState === "PAUSED" && (
							<DropdownMenuItem onClick={() => onResume(channel)}>
								<PlayIcon className="size-4" />
								Resume
							</DropdownMenuItem>
						)}
						{channel.consentState !== "PENDING" && (
							<DropdownMenuItem onClick={() => onViewHistory(channel)}>
								<HistoryIcon className="size-4" />
								View history
							</DropdownMenuItem>
						)}
						{!isTerminal && (
							<>
								<DropdownMenuSeparator />
								<DropdownMenuItem variant="destructive" onClick={() => onRemove(channel)}>
									<Trash2Icon className="size-4" />
									Remove &amp; erase…
								</DropdownMenuItem>
							</>
						)}
					</DropdownMenuContent>
				</DropdownMenu>
			</TableCell>
		</TableRow>
	);
}
