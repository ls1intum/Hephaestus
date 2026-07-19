import type { AuthEventView } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import {
	Sheet,
	SheetContent,
	SheetDescription,
	SheetHeader,
	SheetTitle,
} from "@/components/ui/sheet";
import { DetailRow } from "../audit-shared/DetailRow";
import {
	accountLabel,
	eventLabel,
	formatTimestamp,
	prettyDetails,
	resultLabel,
} from "./auditFormat";

interface AuditEventDetailSheetProps {
	event: AuthEventView | null;
	open: boolean;
	onOpenChange: (open: boolean) => void;
	/** Resolve a workspace id to its name (client-side, from the admin workspace list). */
	resolveWorkspaceName?: (id: number) => string | undefined;
}

/**
 * Full forensic view of a single audit event — every field the row can't fit (workspace, user agent,
 * pretty-printed details) plus both identities. A right-hand Sheet keeps the audit
 * table visible behind it, matching the "inspect a row" model.
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

				{event && (
					<dl className="divide-y px-4 pb-4">
						<DetailRow label="Time">
							{ts ? (
								<>
									<span>{ts.local}</span>
									<span className="ml-2 text-xs text-muted-foreground">({ts.isoUtc})</span>
								</>
							) : (
								"—"
							)}
						</DetailRow>
						<DetailRow label="Result">
							<Badge variant={event.result === "FAILURE" ? "destructive" : "outline"}>
								{resultLabel(event.result)}
							</Badge>
						</DetailRow>
						{event.failureReason && (
							<DetailRow label="Failure reason">
								<span className="text-destructive">{event.failureReason}</span>
							</DetailRow>
						)}
						<DetailRow label="Account">
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
						</DetailRow>
						<DetailRow label="Impersonated by">
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
						</DetailRow>
						<DetailRow label="Workspace">
							{event.workspaceId != null
								? workspaceName
									? `${workspaceName} (#${event.workspaceId})`
									: `#${event.workspaceId}`
								: "—"}
						</DetailRow>
						<DetailRow label="IP address">
							<span className="font-mono text-xs">{event.ipAddress ?? "—"}</span>
						</DetailRow>
						<DetailRow label="User agent">
							<span className="text-xs">{event.userAgent ?? "—"}</span>
						</DetailRow>
						<DetailRow label="Raw data">
							{pretty ? (
								<pre className="max-h-48 overflow-auto rounded bg-muted p-2 text-xs">{pretty}</pre>
							) : (
								"—"
							)}
						</DetailRow>
					</dl>
				)}
			</SheetContent>
		</Sheet>
	);
}
