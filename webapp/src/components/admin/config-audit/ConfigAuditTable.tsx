import { Bot, History, UserCog } from "lucide-react";
import { useState } from "react";
import type { ConfigAuditEntryView } from "@/api/types.gen";
import { RelativeTime } from "@/components/admin/integrations/RelativeTime";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Spinner } from "@/components/ui/spinner";
import {
	Table,
	TableBody,
	TableCaption,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { FilterLink } from "../audit-shared/FilterLink";
import { ConfigAuditDetailSheet } from "./ConfigAuditDetailSheet";
import {
	ACTION_BADGE,
	actionLabel,
	actorDisplay,
	changeSummary,
	subjectLabel,
} from "./configAuditFormat";

export interface ConfigAuditTableProps {
	entries: ConfigAuditEntryView[];
	isLoading: boolean;
	isError: boolean;
	hasFilter: boolean;
	hasNextPage: boolean;
	isFetchingNextPage: boolean;
	onLoadMore: () => void;
	onRetry?: () => void;
	/** Filter the log to an actor's changes (click their name). */
	onFilterActor?: (id: number) => void;
	/** Show the Workspace column (instance-admin view spans workspaces). */
	showWorkspace?: boolean;
	/** Resolve a workspace id to its name (client-side, from the admin workspace list). */
	resolveWorkspaceName?: (id: number) => string | undefined;
}

/**
 * Read-only table of configuration changes (newest first): who changed which setting, when, and — via
 * the field-level diff the server computed — from what to what. Open a row for the full before/after.
 */
export function ConfigAuditTable({
	entries,
	isLoading,
	isError,
	hasFilter,
	hasNextPage,
	isFetchingNextPage,
	onLoadMore,
	onRetry,
	onFilterActor,
	showWorkspace = false,
	resolveWorkspaceName,
}: ConfigAuditTableProps) {
	const [detail, setDetail] = useState<ConfigAuditEntryView | null>(null);
	const [detailOpen, setDetailOpen] = useState(false);

	if (isError) {
		return (
			<Empty className="border border-dashed">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<History />
					</EmptyMedia>
					<EmptyTitle>Failed to load configuration changes.</EmptyTitle>
				</EmptyHeader>
				{onRetry && (
					<EmptyContent>
						<Button variant="outline" size="sm" onClick={onRetry}>
							Try again
						</Button>
					</EmptyContent>
				)}
			</Empty>
		);
	}

	if (isLoading) {
		return (
			<div className="flex items-center justify-center py-12">
				<Spinner />
			</div>
		);
	}

	if (entries.length === 0) {
		return (
			<Empty className="border border-dashed">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<History />
					</EmptyMedia>
					<EmptyTitle>
						{hasFilter ? "No changes match the current filters." : "No configuration changes yet."}
					</EmptyTitle>
					{!hasFilter && (
						<EmptyDescription>
							Changes to review settings, AI bindings, and agent configurations will appear here.
						</EmptyDescription>
					)}
				</EmptyHeader>
			</Empty>
		);
	}

	return (
		<div className="space-y-4">
			<div className="rounded-md border">
				<Table>
					<TableCaption className="sr-only">Configuration changes, newest first</TableCaption>
					<TableHeader>
						<TableRow>
							<TableHead scope="col">Time</TableHead>
							<TableHead scope="col">Action</TableHead>
							<TableHead scope="col">Subject</TableHead>
							{showWorkspace && <TableHead scope="col">Workspace</TableHead>}
							<TableHead scope="col">Actor</TableHead>
							<TableHead scope="col">Changes</TableHead>
							<TableHead scope="col">
								<span className="sr-only">Details</span>
							</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{entries.map((entry) => {
							const actor = actorDisplay(entry);
							const subject = subjectLabel(entry);
							const summary = changeSummary(entry);
							const workspaceName =
								entry.workspaceId != null ? resolveWorkspaceName?.(entry.workspaceId) : undefined;
							return (
								<TableRow key={entry.id}>
									<TableCell className="whitespace-nowrap text-sm text-muted-foreground">
										<RelativeTime value={entry.occurredAt} />
									</TableCell>
									<TableCell>
										<Badge variant={ACTION_BADGE[entry.action ?? "UPDATED"]}>
											{actionLabel(entry.action)}
										</Badge>
									</TableCell>
									<TableCell
										className="max-w-[14rem] truncate"
										title={subject.hint ?? subject.label}
									>
										{subject.label}
									</TableCell>
									{showWorkspace && (
										<TableCell className="max-w-[10rem] truncate text-sm text-muted-foreground">
											{entry.workspaceId != null ? (workspaceName ?? `#${entry.workspaceId}`) : "—"}
										</TableCell>
									)}
									<TableCell className="max-w-[14rem] truncate">
										<ActorCell actor={actor} onFilterActor={onFilterActor} />
									</TableCell>
									<TableCell className="max-w-xs">
										<span className="block truncate text-xs text-muted-foreground" title={summary}>
											{summary}
										</span>
									</TableCell>
									<TableCell className="text-right">
										<Button
											type="button"
											variant="ghost"
											size="sm"
											aria-label={`View details of ${subject.label} change`}
											onClick={() => {
												setDetail(entry);
												setDetailOpen(true);
											}}
										>
											Details
										</Button>
									</TableCell>
								</TableRow>
							);
						})}
					</TableBody>
				</Table>
			</div>

			{hasNextPage && (
				<div className="flex justify-center">
					<Button variant="outline" onClick={onLoadMore} disabled={isFetchingNextPage}>
						{isFetchingNextPage ? <Spinner className="mr-2 size-3.5" /> : null}
						Load more
					</Button>
				</div>
			)}

			<ConfigAuditDetailSheet
				entry={detail}
				open={detailOpen}
				onOpenChange={setDetailOpen}
				resolveWorkspaceName={resolveWorkspaceName}
			/>
		</div>
	);
}

function ActorCell({
	actor,
	onFilterActor,
}: {
	actor: ReturnType<typeof actorDisplay>;
	onFilterActor?: (id: number) => void;
}) {
	if (actor.kind === "SYSTEM") {
		return (
			<span className="flex items-center gap-1.5 text-muted-foreground">
				<Bot className="size-3.5 shrink-0" aria-hidden />
				System
			</span>
		);
	}
	return (
		<span className="flex items-center gap-1.5">
			{actor.kind === "IMPERSONATED" && <UserCog className="size-3.5 shrink-0" aria-hidden />}
			{onFilterActor && actor.filterId != null ? (
				<FilterLink
					label={actor.primary}
					title={actor.primaryEmail ?? `Filter by ${actor.primary}`}
					onSelect={() => onFilterActor(actor.filterId as number)}
				/>
			) : (
				<span className="truncate" title={actor.primaryEmail ?? undefined}>
					{actor.primary}
				</span>
			)}
			{actor.actingAs && (
				<span className="truncate text-xs text-muted-foreground">acting as {actor.actingAs}</span>
			)}
		</span>
	);
}
