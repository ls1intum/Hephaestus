import { formatDistanceToNow } from "date-fns";
import type { AgentJob } from "@/api/types.gen";
import { JOB_TYPE_LABELS } from "@/components/admin/usage/usageUtils";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
	Sheet,
	SheetContent,
	SheetDescription,
	SheetHeader,
	SheetTitle,
} from "@/components/ui/sheet";
import {
	DELIVERY_STATUS_LABELS,
	deliveryBadgeVariant,
	formatCostUsd,
	formatTokens,
	isCancellable,
	isDeliveryRetryable,
	modelLabel,
	STATUS_LABELS,
	statusBadgeVariant,
} from "./jobUtils";

interface AgentJobDetailsPanelProps {
	job: AgentJob | null;
	open: boolean;
	onOpenChange: (open: boolean) => void;
	isCancelling: boolean;
	isRetrying: boolean;
	onCancel: (job: AgentJob) => void;
	onRetryDelivery: (job: AgentJob) => void;
}

/**
 * One label/value pair of a `<dl>`. `dt`/`dd` is what associates the two programmatically — a pair
 * of neighbouring spans reads as two unrelated strings to a screen reader.
 */
function Row({ label, value }: { label: string; value: React.ReactNode }) {
	return (
		<div className="flex items-baseline justify-between gap-4 py-1.5">
			<dt className="text-sm text-muted-foreground">{label}</dt>
			<dd className="text-sm font-medium text-right">{value}</dd>
		</div>
	);
}

function snapshotText(snapshot: unknown): string {
	if (snapshot == null) return "—";
	try {
		return JSON.stringify(snapshot, null, 2);
	} catch {
		return String(snapshot);
	}
}

export function AgentJobDetailsPanel({
	job,
	open,
	onOpenChange,
	isCancelling,
	isRetrying,
	onCancel,
	onRetryDelivery,
}: AgentJobDetailsPanelProps) {
	return (
		<Sheet open={open} onOpenChange={onOpenChange}>
			<SheetContent side="right" className="w-full sm:max-w-lg">
				{job ? (
					<>
						<SheetHeader>
							<SheetTitle>Run details</SheetTitle>
							<SheetDescription>
								{JOB_TYPE_LABELS[job.jobType]} ·{" "}
								{formatDistanceToNow(new Date(job.createdAt), { addSuffix: true })}
							</SheetDescription>
						</SheetHeader>

						<ScrollArea className="flex-1 px-4">
							<div className="space-y-6 pb-6">
								<section>
									<h3 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">
										Overview
									</h3>
									<dl className="divide-y">
										<Row
											label="Status"
											value={
												<Badge variant={statusBadgeVariant(job.status)}>
													{STATUS_LABELS[job.status]}
												</Badge>
											}
										/>
										<Row label="Model" value={modelLabel(job)} />
										<Row label="Model name" value={job.llmModel ?? job.llmModelVersion ?? "—"} />
										<Row
											label="Created"
											value={formatDistanceToNow(new Date(job.createdAt), { addSuffix: true })}
										/>
										{job.completedAt && (
											<Row
												label="Completed"
												value={formatDistanceToNow(new Date(job.completedAt), {
													addSuffix: true,
												})}
											/>
										)}
										<Row
											label="Delivery"
											value={
												job.deliveryStatus ? (
													<Badge variant={deliveryBadgeVariant(job.deliveryStatus)}>
														{DELIVERY_STATUS_LABELS[job.deliveryStatus]}
													</Badge>
												) : (
													"—"
												)
											}
										/>
										{job.exitCode != null && <Row label="Exit code" value={job.exitCode} />}
										{job.retryCount > 0 && <Row label="Retries" value={job.retryCount} />}
									</dl>
								</section>

								{job.errorMessage && (
									<section>
										<h3 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">
											Error
										</h3>
										<p className="rounded-md bg-destructive/10 p-3 text-sm text-destructive whitespace-pre-wrap break-words">
											{job.errorMessage}
										</p>
									</section>
								)}

								<section>
									<h3 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">
										Usage
									</h3>
									<dl className="divide-y">
										<Row label="Input tokens" value={formatTokens(job.llmTotalInputTokens)} />
										<Row label="Output tokens" value={formatTokens(job.llmTotalOutputTokens)} />
										<Row
											label="Reasoning tokens"
											value={formatTokens(job.llmTotalReasoningTokens)}
										/>
										<Row label="Model calls" value={formatTokens(job.llmTotalCalls)} />
										<Row label="Cost" value={formatCostUsd(job.llmCostUsd)} />
									</dl>
								</section>

								<section>
									<h3 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">
										Config snapshot
									</h3>
									<pre className="max-h-80 overflow-auto rounded-md bg-muted p-3 text-xs">
										{snapshotText(job.configSnapshot)}
									</pre>
								</section>
							</div>
						</ScrollArea>

						{(isCancellable(job.status) || isDeliveryRetryable(job)) && (
							<div className="flex gap-2 border-t p-4">
								{isCancellable(job.status) && (
									<AlertDialog>
										<AlertDialogTrigger
											render={
												<Button variant="outline" disabled={isCancelling}>
													{isCancelling ? "Cancelling…" : "Cancel run"}
												</Button>
											}
										/>
										<AlertDialogContent>
											<AlertDialogHeader>
												<AlertDialogTitle>Cancel this run?</AlertDialogTitle>
												<AlertDialogDescription>
													The running container stops. This can't be undone.
												</AlertDialogDescription>
											</AlertDialogHeader>
											<AlertDialogFooter>
												<AlertDialogCancel disabled={isCancelling}>Keep running</AlertDialogCancel>
												<AlertDialogAction
													variant="destructive"
													disabled={isCancelling}
													onClick={() => onCancel(job)}
												>
													Cancel run
												</AlertDialogAction>
											</AlertDialogFooter>
										</AlertDialogContent>
									</AlertDialog>
								)}

								{isDeliveryRetryable(job) && (
									<AlertDialog>
										<AlertDialogTrigger
											render={
												<Button disabled={isRetrying}>
													{isRetrying ? "Retrying…" : "Retry delivery"}
												</Button>
											}
										/>
										<AlertDialogContent>
											<AlertDialogHeader>
												<AlertDialogTitle>Retry delivery?</AlertDialogTitle>
												<AlertDialogDescription>
													The agent's feedback will be re-posted to the PR/MR.
												</AlertDialogDescription>
											</AlertDialogHeader>
											<AlertDialogFooter>
												<AlertDialogCancel disabled={isRetrying}>Cancel</AlertDialogCancel>
												<AlertDialogAction
													disabled={isRetrying}
													onClick={() => onRetryDelivery(job)}
												>
													Retry delivery
												</AlertDialogAction>
											</AlertDialogFooter>
										</AlertDialogContent>
									</AlertDialog>
								)}
							</div>
						)}
					</>
				) : (
					// Always render a title so base-ui never warns about a titleless dialog.
					<SheetHeader>
						<SheetTitle className="sr-only">Run details</SheetTitle>
					</SheetHeader>
				)}
			</SheetContent>
		</Sheet>
	);
}
