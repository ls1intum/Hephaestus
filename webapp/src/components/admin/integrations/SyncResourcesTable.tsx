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
import { relativeTime, stateLabel } from "./sync-format";

/**
 * The header, shared verbatim by the loading and loaded states, so the `<thead>` doesn't appear out of
 * nowhere on resolve.
 */
function ResourcesTableHeader() {
	return (
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
	);
}

/**
 * Counts as "processed of upstream", grouped so a large mirror reads as 1,234,567/2,000,000 rather
 * than an undelimited run of digits.
 */
function formatCounts(resource: SyncResourceState): string {
	if (resource.itemCount == null) {
		return "–";
	}
	return resource.upstreamCount != null
		? `${resource.itemCount.toLocaleString()}/${resource.upstreamCount.toLocaleString()}`
		: resource.itemCount.toLocaleString();
}

export interface SyncResourcesTableProps {
	resources: SyncResourceState[];
	isLoading?: boolean;
	isError?: boolean;
	error?: unknown;
	onRetry?: () => void;
	resourceNoun: string;
	resourceNounPlural: string;
}

export function SyncResourcesTable({
	resources,
	isLoading = false,
	isError = false,
	error,
	onRetry,
	resourceNoun,
	resourceNounPlural,
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
			<Table>
				<ResourcesTableHeader />
				<TableBody>
					{Array.from({ length: 5 }, (_, rowIndex) => (
						<TableRow key={rowIndex}>
							{/* Name is two stacked lines in the loaded row (name over external id), so the
							    placeholder is too — one grey bar here would make every row shrink on resolve. */}
							<TableCell>
								<Skeleton className="h-4 w-40" />
								<Skeleton className="mt-1 h-3 w-24" />
							</TableCell>
							<TableCell>
								<Skeleton className="h-5 w-16 rounded-full" />
							</TableCell>
							<TableCell>
								<Skeleton className="h-4 w-24" />
							</TableCell>
							<TableCell>
								<Skeleton className="ml-auto h-4 w-14" />
							</TableCell>
							<TableCell>
								<Skeleton className="h-4 w-28" />
							</TableCell>
							<TableCell />
						</TableRow>
					))}
				</TableBody>
			</Table>
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
			<ResourcesTableHeader />
			<TableBody>
				{resources.map((resource) => {
					return (
						<TableRow key={resource.id}>
							{/* Names and external ids are upstream-controlled and can be arbitrarily long; truncate
							    and keep the full value in `title` rather than let one row blow out the layout. */}
							<TableCell>
								<div className="max-w-[28ch] truncate font-medium" title={resource.name}>
									{resource.name}
								</div>
								<div
									className="max-w-[28ch] truncate text-muted-foreground font-mono text-xs"
									title={resource.externalId}
								>
									{resource.externalId}
								</div>
							</TableCell>
							<TableCell>
								<Badge variant="outline">{stateLabel(resource.state)}</Badge>
							</TableCell>
							<TableCell className="text-muted-foreground">
								{relativeTime(resource.lastSyncedAt)}
							</TableCell>
							<TableCell className="text-right tabular-nums text-muted-foreground">
								{formatCounts(resource)}
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
