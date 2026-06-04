import { ScrollText } from "lucide-react";
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

export interface AdminAuditTableProps {
	events: AuthEventView[];
	isLoading: boolean;
	isError: boolean;
	hasFilter: boolean;
	hasNextPage: boolean;
	isFetchingNextPage: boolean;
	onLoadMore: () => void;
}

/** Render an epoch/ISO instant in the viewer's locale; defensive against string-vs-Date at runtime. */
function formatInstant(value: AuthEventView["occurredAt"]): string {
	const date = value instanceof Date ? value : new Date(value as unknown as string);
	return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

/**
 * Read-only table of auth audit events (newest first). Pure/presentational: all data + paging come
 * from the route. The actor column is the impersonator (RFC 8693 `act`) when the event happened under
 * impersonation, so every impersonated action stays attributable.
 */
export function AdminAuditTable({
	events,
	isLoading,
	isError,
	hasFilter,
	hasNextPage,
	isFetchingNextPage,
	onLoadMore,
}: AdminAuditTableProps) {
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
							<TableHead>Time</TableHead>
							<TableHead>Event</TableHead>
							<TableHead>Result</TableHead>
							<TableHead>Account</TableHead>
							<TableHead>Actor</TableHead>
							<TableHead>IP</TableHead>
							<TableHead>Details</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{events.map((e) => (
							<TableRow key={e.id}>
								<TableCell className="whitespace-nowrap text-sm tabular-nums text-muted-foreground">
									{formatInstant(e.occurredAt)}
								</TableCell>
								<TableCell>
									<Badge variant="outline" className="font-mono text-xs">
										{e.eventType}
									</Badge>
								</TableCell>
								<TableCell>
									<Badge variant={e.result === "FAILURE" ? "destructive" : "secondary"}>
										{e.result}
									</Badge>
								</TableCell>
								<TableCell className="tabular-nums">{e.accountId ?? "—"}</TableCell>
								<TableCell className="tabular-nums">
									{e.actingAccountId != null ? (
										<Badge variant="outline" className="text-xs">
											via #{e.actingAccountId}
										</Badge>
									) : (
										"—"
									)}
								</TableCell>
								<TableCell className="font-mono text-xs text-muted-foreground">
									{e.ipAddress ?? "—"}
								</TableCell>
								<TableCell className="max-w-xs">
									{e.details ? (
										<code
											className="block truncate text-xs text-muted-foreground"
											title={e.details}
										>
											{e.details}
										</code>
									) : (
										"—"
									)}
								</TableCell>
							</TableRow>
						))}
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
		</div>
	);
}
