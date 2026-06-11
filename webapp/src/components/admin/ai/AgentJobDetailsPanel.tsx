import { formatDistanceToNow } from "date-fns";
import type { AgentJob } from "@/api/types.gen";
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
	configLabel,
	DELIVERY_STATUS_LABELS,
	deliveryBadgeVariant,
	formatCostUsd,
	formatTokens,
	isCancellable,
	isDeliveryRetryable,
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

function Row({ label, value }: { label: string; value: React.ReactNode }) {
	return (
		<div className="flex items-baseline justify-between gap-4 py-1.5">
			<span className="text-sm text-muted-foreground">{label}</span>
			<span className="text-sm font-medium text-right">{value}</span>
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
							<SheetTitle>Job {job.id}</SheetTitle>
							<SheetDescription>{job.jobType.replace(/_/g, " ").toLowerCase()}</SheetDescription>
						</SheetHeader>

						<ScrollArea className="flex-1 px-4">
							<div className="space-y-6 pb-6">
								<section>
									<h3 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">
										Overview
									</h3>
									<div className="divide-y">
										<Row
											label="Status"
											value={
												<Badge variant={statusBadgeVariant(job.status)}>
													{STATUS_LABELS[job.status]}
												</Badge>
											}
										/>
										<Row label="Model" value={configLabel(job)} />
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
									</div>
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
									<div className="divide-y">
										<Row label="Input tokens" value={formatTokens(job.llmTotalInputTokens)} />
										<Row label="Output tokens" value={formatTokens(job.llmTotalOutputTokens)} />
										<Row
											label="Reasoning tokens"
											value={formatTokens(job.llmTotalReasoningTokens)}
										/>
										<Row label="LLM calls" value={formatTokens(job.llmTotalCalls)} />
										<Row label="Cost" value={formatCostUsd(job.llmCostUsd)} />
									</div>
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
													{isCancelling ? "Cancelling…" : "Cancel job"}
												</Button>
											}
										/>
										<AlertDialogContent>
											<AlertDialogHeader>
												<AlertDialogTitle>Cancel this job?</AlertDialogTitle>
												<AlertDialogDescription>
													The running container will be stopped. This cannot be undone.
												</AlertDialogDescription>
											</AlertDialogHeader>
											<AlertDialogFooter>
												<AlertDialogCancel disabled={isCancelling}>Keep running</AlertDialogCancel>
												<AlertDialogAction
													variant="destructive"
													disabled={isCancelling}
													onClick={() => onCancel(job)}
												>
													Cancel job
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
						<SheetTitle className="sr-only">Job details</SheetTitle>
					</SheetHeader>
				)}
			</SheetContent>
		</Sheet>
	);
}
