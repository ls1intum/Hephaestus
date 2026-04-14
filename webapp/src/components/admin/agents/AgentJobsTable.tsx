import { Eye, RefreshCw } from "lucide-react";
import type { AgentConfig, AgentJob, PageAgentJob } from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Card,
	CardAction,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { Field, FieldLabel } from "@/components/ui/field";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import {
	canCancelJob,
	canRetryDelivery,
	deliveryBadgeVariant,
	formatAgentType,
	formatDateTime,
	formatJobStatus,
	formatNumber,
	formatProvider,
	type JobStatusFilter,
	pageSummary,
	statusBadgeVariant,
	statusFilterOptions,
} from "./utils";

export interface AgentJobsTableProps {
	configs: AgentConfig[];
	jobsPage?: PageAgentJob;
	selectedJobId: string | null;
	jobsFilter: {
		status: JobStatusFilter;
		configId: string;
		page: number;
		size: number;
	};
	isLoading: boolean;
	error: Error | null;
	cancellingJobId: string | null;
	retryingJobId: string | null;
	onRefresh: () => void;
	onRequestCancelJob: (job: AgentJob) => void;
	onChangeJobsFilter: (next: Partial<AgentJobsTableProps["jobsFilter"]>) => void;
	onSelectJob: (jobId: string) => void;
	onRetryDelivery: (jobId: string) => Promise<void>;
}

export function AgentJobsTable({
	configs,
	jobsPage,
	selectedJobId,
	jobsFilter,
	isLoading,
	error,
	cancellingJobId,
	retryingJobId,
	onRefresh,
	onRequestCancelJob,
	onChangeJobsFilter,
	onSelectJob,
	onRetryDelivery,
}: AgentJobsTableProps) {
	const jobs = jobsPage?.content ?? [];

	return (
		<Card>
			<CardHeader>
				<CardTitle>Job History</CardTitle>
				<CardDescription>
					Filter workspace jobs, inspect frozen runtime snapshots, and retry failed delivery
					attempts.
				</CardDescription>
				<CardAction>
					<Button variant="outline" onClick={onRefresh}>
						<RefreshCw className="mr-2 size-4" />
						Refresh
					</Button>
				</CardAction>
			</CardHeader>
			<CardContent className="space-y-4">
				{error && (
					<Alert variant="destructive">
						<AlertTitle>Could not load jobs</AlertTitle>
						<AlertDescription>{error.message}</AlertDescription>
					</Alert>
				)}

				<div className="grid gap-3 md:grid-cols-[14rem_14rem_1fr]">
					<Field>
						<FieldLabel htmlFor="agent-jobs-status">Status</FieldLabel>
						<Select
							value={jobsFilter.status}
							onValueChange={(value) => {
								if (!value) return;
								onChangeJobsFilter({ status: value as JobStatusFilter, page: 0 });
							}}
						>
							<SelectTrigger
								id="agent-jobs-status"
								className="w-full"
								aria-label="Filter jobs by status"
							>
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								{statusFilterOptions.map((status) => (
									<SelectItem key={status} value={status}>
										{status === "ALL" ? "All statuses" : formatJobStatus(status)}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</Field>

					<Field>
						<FieldLabel htmlFor="agent-jobs-config">Runtime</FieldLabel>
						<Select
							value={jobsFilter.configId || "ALL"}
							onValueChange={(value) => {
								if (!value) return;
								onChangeJobsFilter({
									configId: value === "ALL" ? "" : value,
									page: 0,
								});
							}}
						>
							<SelectTrigger
								id="agent-jobs-config"
								className="w-full"
								aria-label="Filter jobs by runtime"
							>
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="ALL">All configs</SelectItem>
								{configs.map((config) => (
									<SelectItem key={config.id} value={String(config.id)}>
										{config.name}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</Field>

					<div className="flex items-end justify-between gap-3 rounded-xl border px-4 py-3">
						<div>
							<p className="text-sm font-medium">{pageSummary(jobsPage)}</p>
							<p className="text-sm text-muted-foreground">
								Showing {jobsPage?.numberOfElements ?? 0} of {jobsPage?.totalElements ?? 0} jobs
							</p>
						</div>
					</div>
				</div>

				<div className="overflow-x-auto rounded-xl border">
					<Table>
						<caption className="sr-only">
							Recent practice-review jobs for this workspace, including status, runtime, model,
							delivery, usage, and available actions.
						</caption>
						<TableHeader>
							<TableRow>
								<TableHead>Status</TableHead>
								<TableHead>Config</TableHead>
								<TableHead>Model</TableHead>
								<TableHead>Created</TableHead>
								<TableHead>Delivery</TableHead>
								<TableHead>Usage</TableHead>
								<TableHead className="text-right">Actions</TableHead>
							</TableRow>
						</TableHeader>
						<TableBody>
							{isLoading ? (
								<TableRow>
									<TableCell colSpan={7} className="h-32 text-center">
										<div className="flex items-center justify-center gap-2 text-muted-foreground">
											<Spinner className="size-4" />
											Loading agent jobs...
										</div>
									</TableCell>
								</TableRow>
							) : jobs.length === 0 ? (
								<TableRow>
									<TableCell colSpan={7} className="h-28 text-center text-muted-foreground">
										No jobs match the current filters.
									</TableCell>
								</TableRow>
							) : (
								jobs.map((job) => (
									<JobRow
										key={job.id}
										job={job}
										selected={selectedJobId === job.id}
										cancellingJobId={cancellingJobId}
										retryingJobId={retryingJobId}
										onRequestCancelJob={onRequestCancelJob}
										onSelectJob={onSelectJob}
										onRetryDelivery={onRetryDelivery}
									/>
								))
							)}
						</TableBody>
					</Table>
				</div>

				<div className="flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
					<p className="text-sm text-muted-foreground">
						Page {(jobsPage?.number ?? 0) + 1} of {Math.max(jobsPage?.totalPages ?? 1, 1)}
					</p>
					<div className="flex gap-2">
						<Button
							variant="outline"
							disabled={jobsFilter.page <= 0 || isLoading}
							onClick={() => onChangeJobsFilter({ page: Math.max(0, jobsFilter.page - 1) })}
						>
							Previous
						</Button>
						<Button
							variant="outline"
							disabled={Boolean(jobsPage?.last ?? true) || isLoading}
							onClick={() => onChangeJobsFilter({ page: jobsFilter.page + 1 })}
						>
							Next
						</Button>
					</div>
				</div>
			</CardContent>
		</Card>
	);
}

function JobRow({
	job,
	selected,
	cancellingJobId,
	retryingJobId,
	onRequestCancelJob,
	onSelectJob,
	onRetryDelivery,
}: {
	job: AgentJob;
	selected: boolean;
	cancellingJobId: string | null;
	retryingJobId: string | null;
	onRequestCancelJob: (job: AgentJob) => void;
	onSelectJob: (jobId: string) => void;
	onRetryDelivery: (jobId: string) => Promise<void>;
}) {
	return (
		<TableRow
			data-state={selected ? "selected" : undefined}
			className={selected ? "bg-muted/40" : undefined}
		>
			<TableCell>
				<Badge variant={statusBadgeVariant(job.status)}>{formatJobStatus(job.status)}</Badge>
			</TableCell>
			<TableCell>
				<div className="font-medium">{job.configName ?? "Deleted config"}</div>
				<div className="text-xs text-muted-foreground">{formatAgentType(job.configAgentType)}</div>
			</TableCell>
			<TableCell>
				<div>{job.llmModel ?? job.configModelName ?? "Default model"}</div>
				<div className="text-xs text-muted-foreground">
					{job.llmModelVersion ?? job.configModelVersion ?? formatProvider(job.configLlmProvider)}
				</div>
			</TableCell>
			<TableCell>{formatDateTime(job.createdAt)}</TableCell>
			<TableCell>
				<Badge variant={deliveryBadgeVariant(job.deliveryStatus)}>
					{job.deliveryStatus ? formatJobStatus(job.deliveryStatus) : "N/A"}
				</Badge>
			</TableCell>
			<TableCell>
				<div>{formatNumber(job.llmTotalCalls)} calls</div>
				<div className="text-xs text-muted-foreground">
					{formatNumber(job.llmTotalInputTokens)} in / {formatNumber(job.llmTotalOutputTokens)} out
				</div>
			</TableCell>
			<TableCell>
				<div className="flex min-w-44 justify-end gap-2">
					<Button
						variant="outline"
						size="sm"
						onClick={() => onSelectJob(job.id)}
						aria-label={`View job details for ${job.configName ?? "deleted runtime"}`}
					>
						<Eye className="mr-2 size-4" />
						Details
					</Button>
					{canCancelJob(job) && (
						<Button
							variant="outline"
							size="sm"
							disabled={cancellingJobId === job.id}
							onClick={() => onRequestCancelJob(job)}
						>
							{cancellingJobId === job.id ? <Spinner className="mr-2 size-4" /> : null}
							Cancel
						</Button>
					)}
					{canRetryDelivery(job) && (
						<Button
							size="sm"
							disabled={retryingJobId === job.id}
							onClick={() => onRetryDelivery(job.id)}
						>
							{retryingJobId === job.id ? <Spinner className="mr-2 size-4" /> : null}
							Retry delivery
						</Button>
					)}
				</div>
			</TableCell>
		</TableRow>
	);
}
