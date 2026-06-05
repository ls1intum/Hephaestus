import { ScrollText } from "lucide-react";
import { useState } from "react";
import type { AuthEventView } from "@/api/types.gen";
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
import { AuditEventDetailDialog } from "./AuditEventDetailDialog";
import {
	type AuditSeverity,
	accountLabel,
	eventLabel,
	eventSeverity,
	formatTimestamp,
	humanizeDetails,
	relativeTime,
} from "./auditFormat";

export interface AdminAuditTableProps {
	events: AuthEventView[];
	isLoading: boolean;
	isError: boolean;
	hasFilter: boolean;
	hasNextPage: boolean;
	isFetchingNextPage: boolean;
	onLoadMore: () => void;
	/** Drill-downs: filter the log to a subject account / to an actor's impersonation session. */
	onFilterAccount?: (id: number) => void;
	onFilterActor?: (id: number) => void;
	/** Resolve a workspace id to its name for the detail drawer (client-side, from the admin list). */
	resolveWorkspaceName?: (id: number) => string | undefined;
}

const SEVERITY_DOT: Record<AuditSeverity, string> = {
	error: "bg-destructive",
	warning: "bg-amber-500",
	info: "bg-muted-foreground/40",
};

/**
 * Read-only table of auth audit events (newest first). The actor column attributes impersonated
 * actions to the operator (RFC 8693 `act`); accounts resolve to names (email on hover) and fall back to
 * `#id` for deleted accounts. Click an account/actor to filter; open a row for the full forensic record.
 */
export function AdminAuditTable({
	events,
	isLoading,
	isError,
	hasFilter,
	hasNextPage,
	isFetchingNextPage,
	onLoadMore,
	onFilterAccount,
	onFilterActor,
	resolveWorkspaceName,
}: AdminAuditTableProps) {
	const [detail, setDetail] = useState<AuthEventView | null>(null);

	if (isError) {
		return (
			<p className="py-8 text-center text-sm text-destructive">
				Failed to load audit events. Please try again.
			</p>
		);
	}

	if (isLoading) {
		return (
			<div className="flex items-center justify-center py-12">
				<Spinner />
			</div>
		);
	}

	if (events.length === 0) {
		return (
			<div className="flex flex-col items-center gap-2 py-12 text-center text-muted-foreground">
				<ScrollText className="size-8" aria-hidden />
				<p className="text-sm">
					{hasFilter ? "No matching audit events." : "No audit events yet."}
				</p>
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
							<TableHead scope="col">Event</TableHead>
							<TableHead scope="col">Result</TableHead>
							<TableHead scope="col">Account</TableHead>
							<TableHead scope="col">Actor</TableHead>
							<TableHead scope="col">IP</TableHead>
							<TableHead scope="col">Summary</TableHead>
							<TableHead scope="col">
								<span className="sr-only">Details</span>
							</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{events.map((e) => {
							const ts = formatTimestamp(e.occurredAt);
							const severity = eventSeverity(e.eventType, e.result);
							const account = accountLabel(e.account, e.accountId);
							const actor = accountLabel(e.actor, e.actingAccountId);
							const summary =
								e.result === "FAILURE" && e.failureReason
									? e.failureReason
									: humanizeDetails(e.details);
							return (
								<TableRow key={e.id}>
									<TableCell
										className="whitespace-nowrap text-sm text-muted-foreground"
										title={`${ts.local} (${ts.isoUtc})`}
									>
										{relativeTime(e.occurredAt)}
									</TableCell>
									<TableCell>
										<span className="flex items-center gap-2" title={e.eventType}>
											<span
												className={`size-1.5 shrink-0 rounded-full ${SEVERITY_DOT[severity]}`}
												aria-hidden
											/>
											<span className="text-sm">{eventLabel(e.eventType)}</span>
										</span>
									</TableCell>
									<TableCell>
										<Badge variant={e.result === "FAILURE" ? "destructive" : "secondary"}>
											{e.result}
										</Badge>
									</TableCell>
									<TableCell className="max-w-[12rem] truncate">
										{account ? (
											onFilterAccount && e.accountId != null ? (
												<button
													type="button"
													className="truncate text-left hover:underline"
													title={e.account?.email ?? `Filter by ${account}`}
													onClick={() => onFilterAccount(e.accountId as number)}
												>
													{account}
												</button>
											) : (
												<span title={e.account?.email ?? undefined}>{account}</span>
											)
										) : (
											"—"
										)}
									</TableCell>
									<TableCell className="max-w-[12rem] truncate">
										{actor ? (
											<span className="text-muted-foreground">
												via{" "}
												{onFilterActor && e.actingAccountId != null ? (
													<button
														type="button"
														className="hover:underline"
														title={e.actor?.email ?? `Filter by ${actor}`}
														onClick={() => onFilterActor(e.actingAccountId as number)}
													>
														{actor}
													</button>
												) : (
													actor
												)}
											</span>
										) : (
											"—"
										)}
									</TableCell>
									<TableCell className="font-mono text-xs text-muted-foreground">
										{e.ipAddress ?? "—"}
									</TableCell>
									<TableCell className="max-w-xs">
										{summary ? (
											<span
												className={`block truncate text-xs ${
													e.result === "FAILURE" ? "text-destructive" : "text-muted-foreground"
												}`}
												title={summary}
											>
												{summary}
											</span>
										) : (
											"—"
										)}
									</TableCell>
									<TableCell className="text-right">
										<Button
											type="button"
											variant="ghost"
											size="sm"
											aria-label={`View details of ${eventLabel(e.eventType)} event`}
											onClick={() => setDetail(e)}
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

			<AuditEventDetailDialog
				event={detail}
				open={detail !== null}
				onOpenChange={(open) => !open && setDetail(null)}
				resolveWorkspaceName={resolveWorkspaceName}
			/>
		</div>
	);
}
