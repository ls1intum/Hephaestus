import { useState } from "react";
import type { ConfigAuditEntryView } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Sheet,
	SheetContent,
	SheetDescription,
	SheetHeader,
	SheetTitle,
} from "@/components/ui/sheet";
import { formatTimestamp } from "../audit-shared/timeFormat";
import {
	actionLabel,
	actorDisplay,
	entityTypeLabel,
	fieldChanges,
	prettySnapshot,
	subjectLabel,
} from "./configAuditFormat";

interface ConfigAuditDetailSheetProps {
	entry: ConfigAuditEntryView | null;
	open: boolean;
	onOpenChange: (open: boolean) => void;
	/** Resolve a workspace id to its name for the instance-admin view (client-side, from the admin list). */
	resolveWorkspaceName?: (id: number) => string | undefined;
}

const ACTION_BADGE: Record<string, "default" | "secondary" | "outline"> = {
	CREATED: "default",
	UPDATED: "secondary",
	DELETED: "outline",
};

function Row({ label, children }: { label: string; children: React.ReactNode }) {
	return (
		<div className="grid grid-cols-[8rem_1fr] gap-2 py-1.5 text-sm">
			<dt className="text-muted-foreground">{label}</dt>
			<dd className="min-w-0 break-words">{children}</dd>
		</div>
	);
}

/**
 * Full record of one configuration change: the field-by-field before/after diff first (the reason this
 * surface exists), then the raw snapshots behind a disclosure for forensics. A right-hand Sheet keeps
 * the change list visible behind it — the "inspect a row" model Datadog and Tailscale use for audit logs.
 */
export function ConfigAuditDetailSheet({
	entry,
	open,
	onOpenChange,
	resolveWorkspaceName,
}: ConfigAuditDetailSheetProps) {
	const [showRaw, setShowRaw] = useState(false);
	const ts = entry ? formatTimestamp(entry.occurredAt) : null;
	const changes = entry ? fieldChanges(entry) : [];
	const actor = entry ? actorDisplay(entry) : null;
	const subject = entry ? subjectLabel(entry) : null;
	const workspaceName =
		entry?.workspaceId != null ? resolveWorkspaceName?.(entry.workspaceId) : undefined;
	const oldRaw = prettySnapshot(entry?.oldValue);
	const newRaw = prettySnapshot(entry?.newValue);

	return (
		<Sheet open={open} onOpenChange={onOpenChange}>
			<SheetContent side="right" className="w-full overflow-y-auto sm:max-w-lg">
				<SheetHeader>
					<SheetTitle>{entry ? subject?.label : "Configuration change"}</SheetTitle>
					<SheetDescription>
						{entry ? `${actionLabel(entry.action)} — ${entityTypeLabel(entry.entityType)}` : ""}
					</SheetDescription>
				</SheetHeader>

				{entry && ts && actor && (
					<div className="space-y-4 px-4 pb-4">
						<dl className="divide-y">
							<Row label="Time">
								<span>{ts.local}</span>
								<span className="ml-2 text-xs text-muted-foreground">({ts.isoUtc})</span>
							</Row>
							<Row label="Action">
								<Badge variant={ACTION_BADGE[entry.action ?? "UPDATED"]}>
									{actionLabel(entry.action)}
								</Badge>
							</Row>
							<Row label="Actor">
								{actor.kind === "SYSTEM" ? (
									<span className="text-muted-foreground">System</span>
								) : (
									<span>
										{actor.primary}
										{actor.primaryEmail && actor.primaryEmail !== actor.primary && (
											<span className="ml-1 text-xs text-muted-foreground">
												{actor.primaryEmail}
											</span>
										)}
									</span>
								)}
							</Row>
							{actor.actingAs && <Row label="Impersonating">{actor.actingAs}</Row>}
							{workspaceName !== undefined || entry.workspaceId != null ? (
								<Row label="Workspace">
									{entry.workspaceId != null
										? workspaceName
											? `${workspaceName} (#${entry.workspaceId})`
											: `#${entry.workspaceId}`
										: "—"}
								</Row>
							) : null}
						</dl>

						<div>
							<h3 className="mb-2 text-sm font-medium">
								{entry.action === "CREATED"
									? "Initial values"
									: entry.action === "DELETED"
										? "Final values"
										: "Changes"}
							</h3>
							{changes.length === 0 ? (
								<p className="text-sm text-muted-foreground">No field-level changes recorded.</p>
							) : (
								<dl className="divide-y rounded-md border">
									{changes.map((change) => (
										<div key={change.path} className="grid grid-cols-[10rem_1fr] gap-2 p-2 text-sm">
											<dt
												className="truncate font-mono text-xs text-muted-foreground"
												title={change.path}
											>
												{change.path}
											</dt>
											<dd className="min-w-0 break-words">
												{entry.action === "CREATED" ? (
													<span>{change.after ?? "—"}</span>
												) : entry.action === "DELETED" ? (
													<span>{change.before ?? "—"}</span>
												) : (
													<span>
														<span className="text-muted-foreground line-through decoration-muted-foreground/50">
															{change.before ?? "—"}
														</span>
														<span aria-hidden className="mx-1.5 text-muted-foreground">
															→
														</span>
														<span className="font-medium">{change.after ?? "—"}</span>
													</span>
												)}
											</dd>
										</div>
									))}
								</dl>
							)}
						</div>

						{(oldRaw || newRaw) && (
							<div>
								<Button
									type="button"
									variant="ghost"
									size="sm"
									className="px-0 text-xs text-muted-foreground"
									aria-expanded={showRaw}
									onClick={() => setShowRaw((v) => !v)}
								>
									{showRaw ? "Hide" : "Show"} raw snapshots
								</Button>
								{showRaw && (
									<div className="mt-2 space-y-2">
										{oldRaw && (
											<div>
												<p className="mb-1 text-xs text-muted-foreground">Before</p>
												<pre className="max-h-48 overflow-auto rounded bg-muted p-2 text-xs">
													{oldRaw}
												</pre>
											</div>
										)}
										{newRaw && (
											<div>
												<p className="mb-1 text-xs text-muted-foreground">After</p>
												<pre className="max-h-48 overflow-auto rounded bg-muted p-2 text-xs">
													{newRaw}
												</pre>
											</div>
										)}
									</div>
								)}
							</div>
						)}
					</div>
				)}
			</SheetContent>
		</Sheet>
	);
}
