import { AlertCircleIcon, DatabaseIcon } from "lucide-react";
import type { SyncResourceState } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { relativeTime, resourceStateLabel } from "./sync-format";

export interface SyncResourcesTableProps {
	resources: SyncResourceState[];
	isLoading?: boolean;
	isError?: boolean;
	error?: unknown;
	onRetry?: () => void;
	resourceNoun?: string;
	resourceNounPlural?: string;
}

export function SyncResourcesTable({
	resources,
	isLoading = false,
	isError = false,
	error,
	onRetry,
	resourceNoun = "resource",
	resourceNounPlural = `${resourceNoun}s`,
}: SyncResourcesTableProps) {
	if (isError) {
		return (
			<QueryErrorAlert
				error={error}
				title={`We couldn't load the ${resourceNoun} sync state`}
				onRetry={onRetry}
			/>
		);
	}

	if (isLoading) {
		return (
			<div className="space-y-2">
				<Skeleton className="h-10 w-full" />
				<Skeleton className="h-10 w-full" />
				<Skeleton className="h-10 w-full" />
			</div>
		);
	}

	if (resources.length === 0) {
		return (
			<Empty className="border border-dashed">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<DatabaseIcon />
					</EmptyMedia>
					<EmptyTitle>No {resourceNounPlural} synced yet</EmptyTitle>
					<EmptyDescription>
						Synced {resourceNounPlural} and their state appear here once a sync job runs.
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}

	return (
		<Table>
			<TableHeader>
				<TableRow>
					<TableHead>Name</TableHead>
					<TableHead>State</TableHead>
					<TableHead>Last synced</TableHead>
					<TableHead className="text-right">Items</TableHead>
					<TableHead>Backfill</TableHead>
					<TableHead className="w-0 text-right">
						<span className="sr-only">Error</span>
					</TableHead>
				</TableRow>
			</TableHeader>
			<TableBody>
				{resources.map((resource) => {
					return (
						<TableRow key={resource.id}>
							<TableCell>
								<div className="font-medium">{resource.name}</div>
								<div className="text-muted-foreground font-mono text-xs">{resource.externalId}</div>
							</TableCell>
							<TableCell>
								<Badge variant="outline">{resourceStateLabel(resource.state)}</Badge>
							</TableCell>
							<TableCell className="text-muted-foreground">
								{relativeTime(resource.lastSyncedAt)}
							</TableCell>
							<TableCell className="text-right tabular-nums text-muted-foreground">
								{resource.itemCount != null
									? resource.upstreamCount != null
										? `${resource.itemCount}/${resource.upstreamCount}`
										: resource.itemCount
									: "–"}
							</TableCell>
							<TableCell>
								{resource.backfillPercent != null ? (
									<div className="flex items-center gap-2">
										<Progress
											value={resource.backfillPercent}
											className="w-20"
											aria-label={`Backfill progress for ${resource.name}`}
										/>
										<span className="text-muted-foreground text-xs tabular-nums">
											{resource.backfillPercent}%
										</span>
									</div>
								) : (
									<span className="text-muted-foreground">–</span>
								)}
							</TableCell>
							<TableCell className="text-right">
								{resource.lastError && (
									<Popover>
										<PopoverTrigger
											render={
												<Button
													variant="ghost"
													size="icon-sm"
													aria-label={`Error for ${resource.name}`}
												>
													<AlertCircleIcon className="size-4 text-destructive" />
												</Button>
											}
										/>
										<PopoverContent>{resource.lastError}</PopoverContent>
									</Popover>
								)}
							</TableCell>
						</TableRow>
					);
				})}
			</TableBody>
		</Table>
	);
}
