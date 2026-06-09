import type { AuthEventView } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import {
	Sheet,
	SheetContent,
	SheetDescription,
	SheetHeader,
	SheetTitle,
} from "@/components/ui/sheet";
import { accountLabel, eventLabel, formatTimestamp, prettyDetails } from "./auditFormat";

interface AuditEventDetailSheetProps {
	event: AuthEventView | null;
	open: boolean;
	onOpenChange: (open: boolean) => void;
	/** Resolve a workspace id to its name (client-side, from the admin workspace list). */
	resolveWorkspaceName?: (id: number) => string | undefined;
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
	return (
		<div className="grid grid-cols-[8rem_1fr] gap-2 py-1.5 text-sm">
			<dt className="text-muted-foreground">{label}</dt>
			<dd className="min-w-0 break-words">{children}</dd>
		</div>
	);
}

/**
 * Full forensic view of a single audit event — every field the row can't fit (workspace, user agent,
 * pretty-printed details) plus both identities. A right-hand detail drawer is the pattern Datadog and
 * Tailscale use so wide records aren't crammed into columns or hidden in tooltips; a Sheet (vs. a modal
 * Dialog) keeps the audit table visible behind it, matching the "inspect a row" mental model.
 */
export function AuditEventDetailSheet({
	event,
	open,
	onOpenChange,
	resolveWorkspaceName,
}: AuditEventDetailSheetProps) {
	const ts = event ? formatTimestamp(event.occurredAt) : null;
	const account = event ? accountLabel(event.account, event.accountId) : null;
	const actor = event ? accountLabel(event.actor, event.actingAccountId) : null;
	const pretty = event ? prettyDetails(event.details) : null;
	const workspaceName =
		event?.workspaceId != null ? resolveWorkspaceName?.(event.workspaceId) : undefined;

	return (
		<Sheet open={open} onOpenChange={onOpenChange}>
			<SheetContent side="right" className="w-full overflow-y-auto sm:max-w-lg">
				<SheetHeader>
					<SheetTitle>{event ? eventLabel(event.eventType) : "Audit event"}</SheetTitle>
					<SheetDescription>
						{event ? `Event #${event.id} — ${event.eventType}` : ""}
					</SheetDescription>
				</SheetHeader>

				{event && ts && (
					<dl className="divide-y px-4 pb-4">
						<Row label="Time">
							<span>{ts.local}</span>
							<span className="ml-2 text-xs text-muted-foreground">({ts.isoUtc})</span>
						</Row>
						<Row label="Result">
							<Badge variant={event.result === "FAILURE" ? "destructive" : "outline"}>
								{event.result}
							</Badge>
						</Row>
						{event.failureReason && (
							<Row label="Failure reason">
								<span className="text-destructive">{event.failureReason}</span>
							</Row>
						)}
						<Row label="Account">
							{account ? (
								<span>
									{account}
									{event.account?.email && account !== event.account.email && (
										<span className="ml-1 text-xs text-muted-foreground">
											{event.account.email}
										</span>
									)}
								</span>
							) : (
								"—"
							)}
						</Row>
						<Row label="Actor">
							{actor ? (
								<span>
									{actor}
									{event.actingAccountId != null && (
										<span className="ml-1 text-xs text-muted-foreground">(impersonating)</span>
									)}
								</span>
							) : (
								"—"
							)}
						</Row>
						<Row label="Workspace">
							{event.workspaceId != null
								? workspaceName
									? `${workspaceName} (#${event.workspaceId})`
									: `#${event.workspaceId}`
								: "—"}
						</Row>
						<Row label="IP address">
							<span className="font-mono text-xs">{event.ipAddress ?? "—"}</span>
						</Row>
						<Row label="User agent">
							<span className="text-xs">{event.userAgent ?? "—"}</span>
						</Row>
						<Row label="Details">
							{pretty ? (
								<pre className="max-h-48 overflow-auto rounded bg-muted p-2 text-xs">{pretty}</pre>
							) : (
								"—"
							)}
						</Row>
					</dl>
				)}
			</SheetContent>
		</Sheet>
	);
}
