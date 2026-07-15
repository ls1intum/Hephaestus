import { useQuery } from "@tanstack/react-query";
import { format } from "date-fns";
import { ArrowRightIcon, HistoryIcon } from "lucide-react";
import { Fragment } from "react";
import { listSlackChannelConsentEventsOptions } from "@/api/@tanstack/react-query.gen";
import type { SlackChannelConsentEvent, SlackMonitoredChannel } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import {
	Item,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemSeparator,
	ItemTitle,
} from "@/components/ui/item";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
	Sheet,
	SheetContent,
	SheetDescription,
	SheetHeader,
	SheetTitle,
} from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { asDate } from "../sync-format";
import { ConsentStateBadge } from "./consent-terms";

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

	const { data, isLoading, error, refetch } = useQuery({
		...listSlackChannelConsentEventsOptions({
			path: { workspaceSlug, slackChannelId: channel?.slackChannelId ?? "" },
		}),
		enabled: open,
	});

	const events = data ?? [];

	return (
		<Sheet open={open} onOpenChange={onOpenChange}>
			<SheetContent className="w-full sm:max-w-md">
				<SheetHeader>
					<SheetTitle>Consent history</SheetTitle>
					<SheetDescription>
						Every recorded consent transition for #{label}, newest first.
					</SheetDescription>
				</SheetHeader>

				<ScrollArea className="min-h-0 flex-1 px-4 pb-4">
					{isLoading && (
						<div className="space-y-3">
							<Skeleton className="h-12 w-full" />
							<Skeleton className="h-12 w-full" />
							<Skeleton className="h-12 w-full" />
						</div>
					)}

					{!isLoading && error && (
						<QueryErrorAlert
							error={error}
							title="Could not load the consent history"
							onRetry={() => refetch()}
						/>
					)}

					{!isLoading && !error && events.length === 0 && (
						<Empty>
							<EmptyHeader>
								<EmptyMedia variant="icon">
									<HistoryIcon />
								</EmptyMedia>
								<EmptyTitle>No consent changes recorded yet</EmptyTitle>
								<EmptyDescription>
									Every activation, pause, resume and removal lands here as an immutable audit
									entry.
								</EmptyDescription>
							</EmptyHeader>
						</Empty>
					)}

					{!isLoading && !error && events.length > 0 && (
						<ItemGroup>
							{events.map((event, index) => (
								<Fragment key={event.id}>
									{index > 0 && <ItemSeparator />}
									<HistoryEntry event={event} />
								</Fragment>
							))}
						</ItemGroup>
					)}
				</ScrollArea>
			</SheetContent>
		</Sheet>
	);
}

/** One audit entry: the transition it recorded, when, and the reason the admin gave. */
function HistoryEntry({ event }: { event: SlackChannelConsentEvent }) {
	return (
		<Item render={<li />} size="sm" className="items-start">
			<ItemContent>
				<ItemTitle className="gap-1.5">
					{event.fromState && (
						<>
							<ConsentStateBadge state={event.fromState} />
							<ArrowRightIcon className="text-muted-foreground size-3.5" aria-hidden />
						</>
					)}
					<ConsentStateBadge state={event.toState} />
				</ItemTitle>
				<ItemDescription>{format(asDate(event.createdAt) ?? new Date(), "PPpp")}</ItemDescription>
				{event.reason && (
					<ItemDescription className="text-foreground">{event.reason}</ItemDescription>
				)}
			</ItemContent>
		</Item>
	);
}
