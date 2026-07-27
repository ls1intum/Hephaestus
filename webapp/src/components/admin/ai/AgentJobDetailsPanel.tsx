import type { AgentJob } from "@/api/types.gen";
import { JOB_TYPE_LABELS } from "@/components/admin/usage/usage-utils";
import { DetailRow } from "@/components/common/DetailRow";
import { RelativeTime } from "@/components/common/RelativeTime";
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
	formatTokens,
	isCancellable,
	isDeliveryRetryable,
	modelLabel,
	STATUS_LABELS,
	statusBadgeVariant,
} from "./job-utils";

interface AgentJobDetailsPanelProps {
	job: AgentJob | null;
	open: boolean;
	onOpenChange: (open: boolean) => void;
	isCancelling: boolean;
	isRetrying: boolean;
	onCancel: (job: AgentJob) => void;
	onRetryDelivery: (job: AgentJob) => void;
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
			{/* The width overrides have to repeat `data-[side=right]:` to land at all. `SheetContent`'s own
			    widths are written as `data-[side=right]:w-3/4` / `data-[side=right]:sm:max-w-sm`, which are
			    attribute-qualified and so outrank a plain `w-full` / `sm:max-w-lg` on specificity —
			    tailwind-merge only drops the base class when the variant chain matches exactly. Spelled
			    this way the panel really is full-width on a phone (it was rendering at 75%, leaving the
			    label/value rows ~240 px wide) and really is `lg` from `sm` up. */}
			<SheetContent side="right" className="data-[side=right]:w-full data-[side=right]:sm:max-w-lg">
				{job ? (
					<>
						<SheetHeader>
							<SheetTitle>Run details</SheetTitle>
							<SheetDescription>
								{JOB_TYPE_LABELS[job.jobType]} ·{" "}
								{/* The "Created" row below carries the same instant with its absolute-time
								    tooltip; a second hover target for one value would be noise. */}
								<RelativeTime value={job.createdAt} tooltip={false} />
							</SheetDescription>
						</SheetHeader>

						<ScrollArea className="flex-1 px-4">
							<div className="space-y-6 pb-6">
								<section>
									<h3 className="mb-1 text-xs font-semibold uppercase text-muted-foreground">
										Overview
									</h3>
									<dl className="divide-y">
										<DetailRow label="Status">
											<Badge variant={statusBadgeVariant(job.status)}>
												{STATUS_LABELS[job.status]}
											</Badge>
										</DetailRow>
										<DetailRow label="Model">{modelLabel(job)}</DetailRow>
										<DetailRow label="Model name">
											{job.llmModel ?? job.llmModelVersion ?? "—"}
										</DetailRow>
										<DetailRow label="Created">
											<RelativeTime value={job.createdAt} />
										</DetailRow>
										{job.completedAt && (
											<DetailRow label="Completed">
												<RelativeTime value={job.completedAt} />
											</DetailRow>
										)}
										<DetailRow label="Delivery">
											{job.deliveryStatus ? (
												<Badge variant={deliveryBadgeVariant(job.deliveryStatus)}>
													{DELIVERY_STATUS_LABELS[job.deliveryStatus]}
												</Badge>
											) : (
												"—"
											)}
										</DetailRow>
										{job.exitCode != null && (
											<DetailRow label="Exit code">{job.exitCode}</DetailRow>
										)}
										{job.retryCount > 0 && <DetailRow label="Retries">{job.retryCount}</DetailRow>}
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
										<DetailRow label="Input tokens">
											{formatTokens(job.llmTotalInputTokens)}
										</DetailRow>
										<DetailRow label="Output tokens">
											{formatTokens(job.llmTotalOutputTokens)}
										</DetailRow>
										<DetailRow label="Reasoning tokens">
											{formatTokens(job.llmTotalReasoningTokens)}
										</DetailRow>
										<DetailRow label="Model calls">{formatTokens(job.llmTotalCalls)}</DetailRow>
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
												{/* Never disabled: a popup with both footer buttons out has no operable
											    control at all. The trigger behind it stays disabled until the request
											    settles. ADR 0027. */}
												<AlertDialogCancel>Keep running</AlertDialogCancel>
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
												<AlertDialogCancel>Cancel</AlertDialogCancel>
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
