import { useQuery } from "@tanstack/react-query";
import { format } from "date-fns";
import { ArrowRightIcon } from "lucide-react";
import { listSlackChannelConsentEventsOptions } from "@/api/@tanstack/react-query.gen";
import type { SlackChannelConsentEvent, SlackMonitoredChannel } from "@/api/types.gen";
import {
	Sheet,
	SheetContent,
	SheetDescription,
	SheetHeader,
	SheetTitle,
} from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";

export interface ChannelHistorySheetProps {
	workspaceSlug: string;
	/** Non-null opens the sheet and selects the channel whose audit trail to load. */
	channel: SlackMonitoredChannel | null;
	onOpenChange: (open: boolean) => void;
}

/**
 * Lazy per-channel consent audit trail. The query is enabled only while the sheet is open,
 * so listing the channels never fans out N history requests.
 */
export function ChannelHistorySheet({
	workspaceSlug,
	channel,
	onOpenChange,
}: ChannelHistorySheetProps) {
	const open = channel != null;
	const label = channel ? (channel.channelName ?? channel.slackChannelId) : "";

	const { data, isLoading, error } = useQuery({
		...listSlackChannelConsentEventsOptions({
			path: { workspaceSlug, slackChannelId: channel?.slackChannelId ?? "" },
		}),
		enabled: open,
	});

	return (
		<Sheet open={open} onOpenChange={onOpenChange}>
			<SheetContent className="w-full sm:max-w-md">
				<SheetHeader>
					<SheetTitle>Consent history</SheetTitle>
					<SheetDescription>
						Every recorded consent transition for #{label}, newest first.
					</SheetDescription>
				</SheetHeader>

				<div className="overflow-y-auto px-4 pb-4">
					{isLoading && (
						<div className="space-y-3">
							<Skeleton className="h-12 w-full" />
							<Skeleton className="h-12 w-full" />
							<Skeleton className="h-12 w-full" />
						</div>
					)}

					{!isLoading && error && (
						<p className="text-destructive text-sm">Could not load the consent history.</p>
					)}

					{!isLoading && !error && (data?.length ?? 0) === 0 && (
						<p className="text-muted-foreground text-sm">No consent changes recorded yet.</p>
					)}

					{!isLoading && !error && data && data.length > 0 && (
						<ol className="border-border relative space-y-4 border-l pl-4">
							{data.map((event) => (
								<HistoryEntry key={event.id} event={event} />
							))}
						</ol>
					)}
				</div>
			</SheetContent>
		</Sheet>
	);
}

function HistoryEntry({ event }: { event: SlackChannelConsentEvent }) {
	return (
		<li className="space-y-1">
			<div className="flex items-center gap-1.5 text-sm font-medium">
				{event.fromState ? (
					<>
						<span className="text-muted-foreground">{event.fromState}</span>
						<ArrowRightIcon className="text-muted-foreground size-3.5" aria-hidden />
					</>
				) : null}
				<span>{event.toState}</span>
			</div>
			<div className="text-muted-foreground text-xs">
				{format(new Date(event.createdAt), "PPpp")}
			</div>
			{event.reason ? <p className="text-sm">{event.reason}</p> : null}
		</li>
	);
}
