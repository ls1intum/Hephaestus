import { Bot, History, UserCog } from "lucide-react";
import { useState } from "react";
import type { ConfigAuditEntryView } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { formatTimestamp, relativeTime } from "../audit-shared/timeFormat";
import { ConfigAuditDetailSheet } from "./ConfigAuditDetailSheet";
import { actionLabel, actorDisplay, changeSummary, subjectLabel } from "./configAuditFormat";

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

const ACTION_BADGE: Record<string, "default" | "secondary" | "outline"> = {
	CREATED: "default",
	UPDATED: "secondary",
	DELETED: "outline",
};

/**
 * Read-only table of configuration changes (newest first). Each row answers *who changed which setting,
 * when, and from what to what*: the Changes column renders the field-level diff the server computed, the
 * Actor column attributes impersonated changes to the operator. Open a row for the full before/after.
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

	if (isError) {
		return (
			<div className="flex flex-col items-center gap-3 py-8 text-center">
				<p className="text-sm text-destructive">Failed to load configuration changes.</p>
				{onRetry && (
					<Button variant="outline" size="sm" onClick={onRetry}>
						Try again
					</Button>
				)}
			</div>
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
			<div className="flex flex-col items-center gap-2 py-12 text-center text-muted-foreground">
				<History className="size-8" aria-hidden />
				{hasFilter ? (
					<p className="text-sm">No changes match the current filters.</p>
				) : (
					<div className="space-y-1">
						<p className="text-sm">No configuration changes yet.</p>
						<p className="text-xs">
							Changes to review settings, AI bindings, and agent configurations will appear here.
						</p>
					</div>
				)}
			</div>
		);
	}

	return (
		<div className="space-y-4">
			<div className="rounded-md border">
				<Table>
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
							const ts = formatTimestamp(entry.occurredAt);
							const actor = actorDisplay(entry);
							const subject = subjectLabel(entry);
							const summary = changeSummary(entry);
							const workspaceName =
								entry.workspaceId != null ? resolveWorkspaceName?.(entry.workspaceId) : undefined;
							return (
								<TableRow key={entry.id}>
									<TableCell
										className="whitespace-nowrap text-sm text-muted-foreground"
										title={`${ts.local} (${ts.isoUtc})`}
									>
										{relativeTime(entry.occurredAt)}
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
										<ActorCell actor={actor} entry={entry} onFilterActor={onFilterActor} />
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
											onClick={() => setDetail(entry)}
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
				open={detail !== null}
				onOpenChange={(open) => !open && setDetail(null)}
				resolveWorkspaceName={resolveWorkspaceName}
			/>
		</div>
	);
}

function ActorCell({
	actor,
	entry,
	onFilterActor,
}: {
	actor: ReturnType<typeof actorDisplay>;
	entry: ConfigAuditEntryView;
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
	// The responsible party is the signed-in user, or the operator for an impersonated change.
	const filterId = actor.kind === "IMPERSONATED" ? entry.actingAccountId : entry.actorAccountId;
	const name =
		onFilterActor && filterId != null ? (
			<button
				type="button"
				className="truncate rounded-sm text-left outline-none hover:underline focus-visible:ring-2 focus-visible:ring-ring"
				title={actor.primaryEmail ?? `Filter by ${actor.primary}`}
				onClick={() => onFilterActor(filterId)}
			>
				{actor.primary}
			</button>
		) : (
			<span title={actor.primaryEmail ?? undefined}>{actor.primary}</span>
		);
	return (
		<span className="flex items-center gap-1.5">
			{actor.kind === "IMPERSONATED" && <UserCog className="size-3.5 shrink-0" aria-hidden />}
			{name}
			{actor.actingAs && (
				<span className="truncate text-xs text-muted-foreground">acting as {actor.actingAs}</span>
			)}
		</span>
	);
}
