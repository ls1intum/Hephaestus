import type { AgentJob } from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Spinner } from "@/components/ui/spinner";
import {
	canCancelJob,
	canRetryDelivery,
	deliveryBadgeVariant,
	formatAgentType,
	formatCost,
	formatDateTime,
	formatJobStatus,
	formatJson,
	formatNumber,
	formatProvider,
	statusBadgeVariant,
} from "./utils";

export interface AgentJobDetailsDialogProps {
	open: boolean;
	job?: AgentJob;
	isLoading: boolean;
	error: Error | null;
	cancellingJobId: string | null;
	retryingJobId: string | null;
	onRequestCancelJob: (job: AgentJob) => void;
	onOpenChange: (open: boolean) => void;
	onRetryDelivery: (jobId: string) => Promise<void>;
}

export function AgentJobDetailsDialog({
	open,
	job,
	isLoading,
	error,
	cancellingJobId,
	retryingJobId,
	onRequestCancelJob,
	onOpenChange,
	onRetryDelivery,
}: AgentJobDetailsDialogProps) {
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="max-h-[85vh] sm:max-w-4xl">
				<DialogHeader>
					<DialogTitle>{job ? (job.configName ?? "Agent job") : "Agent job details"}</DialogTitle>
					<DialogDescription>
						Inspect the frozen config snapshot, output payload, and delivery state for this run.
					</DialogDescription>
				</DialogHeader>

				<div className="max-h-[calc(85vh-11rem)] overflow-y-auto pr-1" role="document">
					<div className="space-y-4 pb-4 pr-3">
						{isLoading ? (
							<div
								className="flex min-h-48 items-center justify-center gap-2 text-muted-foreground"
								role="status"
								aria-live="polite"
							>
								<Spinner className="size-6" />
								<span>Loading job details...</span>
							</div>
						) : error ? (
							<Alert variant="destructive">
								<AlertTitle>Could not load job details</AlertTitle>
								<AlertDescription>{error.message}</AlertDescription>
							</Alert>
						) : job ? (
							<>
								<Card size="sm">
									<CardHeader>
										<CardTitle>Overview</CardTitle>
										<CardDescription>Runtime, status, and execution timestamps.</CardDescription>
									</CardHeader>
									<CardContent>
										<dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
											<ValueItem
												label="Status"
												value={
													<Badge variant={statusBadgeVariant(job.status)}>
														{formatJobStatus(job.status)}
													</Badge>
												}
											/>
											<ValueItem
												label="Delivery"
												value={
													<Badge variant={deliveryBadgeVariant(job.deliveryStatus)}>
														{job.deliveryStatus ? formatJobStatus(job.deliveryStatus) : "N/A"}
													</Badge>
												}
											/>
											<ValueItem
												label="Runtime"
												value={`${formatAgentType(job.configAgentType)} / ${formatProvider(job.configLlmProvider)}`}
											/>
											<ValueItem
												label="Model"
												value={job.llmModel ?? job.configModelName ?? "Default model"}
											/>
											<ValueItem label="Created" value={formatDateTime(job.createdAt)} />
											<ValueItem label="Completed" value={formatDateTime(job.completedAt)} />
										</dl>
									</CardContent>
								</Card>

								<Card size="sm">
									<CardHeader>
										<CardTitle>Usage</CardTitle>
										<CardDescription>
											Runtime-reported token, call, and cost totals.
										</CardDescription>
									</CardHeader>
									<CardContent>
										<dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
											<ValueItem label="Calls" value={formatNumber(job.llmTotalCalls)} />
											<ValueItem
												label="Input tokens"
												value={formatNumber(job.llmTotalInputTokens)}
											/>
											<ValueItem
												label="Output tokens"
												value={formatNumber(job.llmTotalOutputTokens)}
											/>
											<ValueItem
												label="Reasoning tokens"
												value={formatNumber(job.llmTotalReasoningTokens)}
											/>
											<ValueItem label="Cache read" value={formatNumber(job.llmCacheReadTokens)} />
											<ValueItem label="Estimated cost" value={formatCost(job.llmCostUsd)} />
										</dl>
									</CardContent>
								</Card>

								<JsonSection title="Config snapshot" value={job.configSnapshot} />
								<JsonSection title="Metadata" value={job.metadata} />
								<JsonSection title="Output" value={job.output} />
							</>
						) : null}
					</div>
				</div>

				<DialogFooter>
					{job && canCancelJob(job) && (
						<Button
							variant="outline"
							disabled={cancellingJobId === job.id}
							onClick={() => onRequestCancelJob(job)}
						>
							{cancellingJobId === job.id ? <Spinner className="mr-2 size-4" /> : null}
							Cancel job
						</Button>
					)}
					{job && canRetryDelivery(job) && (
						<Button disabled={retryingJobId === job.id} onClick={() => onRetryDelivery(job.id)}>
							{retryingJobId === job.id ? <Spinner className="mr-2 size-4" /> : null}
							Retry delivery
						</Button>
					)}
					<Button variant="outline" onClick={() => onOpenChange(false)}>
						Close
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}

function ValueItem(props: { label: string; value: React.ReactNode }) {
	return (
		<div className="space-y-1 rounded-lg border px-3 py-2">
			<dt className="text-xs uppercase tracking-wide text-muted-foreground">{props.label}</dt>
			<dd className="text-sm font-medium text-foreground">{props.value}</dd>
		</div>
	);
}

function JsonSection(props: { title: string; value: unknown }) {
	return (
		<Card size="sm">
			<CardHeader>
				<CardTitle>{props.title}</CardTitle>
			</CardHeader>
			<CardContent>
				<div className="rounded-lg border bg-muted/30">
					<pre className="max-h-64 overflow-auto p-3 text-xs whitespace-pre-wrap break-all font-mono">
						{formatJson(props.value)}
					</pre>
				</div>
			</CardContent>
		</Card>
	);
}
