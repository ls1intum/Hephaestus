import { formatDistanceToNow } from "date-fns";
import { AlertCircle, Bot, ChevronRight } from "lucide-react";
import type { AgentConfig, AgentJob } from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
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
	configLabel,
	DELIVERY_STATUS_LABELS,
	deliveryBadgeVariant,
	formatCostUsd,
	formatTokens,
	type JobStatus,
	STATUS_LABELS,
	statusBadgeVariant,
} from "./jobUtils";

const FILTER_ALL = "ALL";

export interface AgentJobsTableProps {
	jobs: AgentJob[];
	configs: AgentConfig[];
	isLoading: boolean;
	isError?: boolean;
	statusFilter: JobStatus | "ALL";
	configFilter: number | "ALL";
	onStatusFilterChange: (status: JobStatus | "ALL") => void;
	onConfigFilterChange: (configId: number | "ALL") => void;
	onSelectJob: (job: AgentJob) => void;
	onRetry?: () => void;
}

const STATUSES: JobStatus[] = [
	"QUEUED",
	"RUNNING",
	"COMPLETED",
	"FAILED",
	"TIMED_OUT",
	"CANCELLED",
];

const STATUS_ITEMS = [
	{ value: FILTER_ALL, label: "All statuses" },
	...STATUSES.map((s) => ({ value: s, label: STATUS_LABELS[s] })),
];

export function AgentJobsTable({
	jobs,
	configs,
	isLoading,
	isError = false,
	statusFilter,
	configFilter,
	onStatusFilterChange,
	onConfigFilterChange,
	onSelectJob,
	onRetry,
}: AgentJobsTableProps) {
	const runtimeItems = [
		{ value: FILTER_ALL, label: "All models" },
		...configs.map((c) => ({ value: String(c.id), label: c.name })),
	];

	return (
		<div className="space-y-4">
			<div className="flex flex-wrap items-center gap-3">
				<div className="flex items-center gap-2 text-sm">
					<span className="text-muted-foreground">Status</span>
					<Select
						items={STATUS_ITEMS}
						value={statusFilter}
						onValueChange={(value) =>
							onStatusFilterChange(value === FILTER_ALL ? "ALL" : (value as JobStatus))
						}
					>
						<SelectTrigger size="sm" className="w-40" aria-label="Filter by status">
							<SelectValue />
						</SelectTrigger>
						<SelectContent>
							<SelectItem value={FILTER_ALL}>All statuses</SelectItem>
							{STATUSES.map((s) => (
								<SelectItem key={s} value={s}>
									{STATUS_LABELS[s]}
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</div>

				<div className="flex items-center gap-2 text-sm">
					<span className="text-muted-foreground">Model</span>
					<Select
						items={runtimeItems}
						value={configFilter === "ALL" ? FILTER_ALL : String(configFilter)}
						onValueChange={(value) =>
							onConfigFilterChange(value === FILTER_ALL ? "ALL" : Number(value))
						}
					>
						<SelectTrigger size="sm" className="w-44" aria-label="Filter by model">
							<SelectValue />
						</SelectTrigger>
						<SelectContent>
							<SelectItem value={FILTER_ALL}>All models</SelectItem>
							{configs.map((c) => (
								<SelectItem key={c.id} value={String(c.id)}>
									{c.name}
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</div>
			</div>

			{isError ? (
				<Alert variant="destructive">
					<AlertCircle />
					<AlertTitle>Failed to load jobs</AlertTitle>
					<AlertDescription>
						<p>The agent jobs could not be loaded.</p>
						{onRetry && (
							<Button variant="outline" size="sm" className="mt-2" onClick={onRetry}>
								Retry
							</Button>
						)}
					</AlertDescription>
				</Alert>
			) : isLoading ? (
				<div className="flex h-40 items-center justify-center">
					<Spinner className="h-6 w-6" />
				</div>
			) : jobs.length === 0 ? (
				<Empty className="border border-dashed">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<Bot />
						</EmptyMedia>
						<EmptyTitle>No reviews yet</EmptyTitle>
						<EmptyDescription>Reviews appear here once practice detection runs.</EmptyDescription>
					</EmptyHeader>
				</Empty>
			) : (
				<Table>
					<TableHeader>
						<TableRow>
							<TableHead>Status</TableHead>
							<TableHead>Model</TableHead>
							<TableHead>Model name</TableHead>
							<TableHead>Created</TableHead>
							<TableHead>Delivery</TableHead>
							<TableHead className="text-right">Usage</TableHead>
							<TableHead className="text-right">Details</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{jobs.map((job) => (
							<TableRow key={job.id} className="cursor-pointer" onClick={() => onSelectJob(job)}>
								<TableCell>
									<Badge variant={statusBadgeVariant(job.status)}>
										{STATUS_LABELS[job.status]}
									</Badge>
								</TableCell>
								<TableCell className="max-w-40 truncate">{configLabel(job)}</TableCell>
								<TableCell className="text-muted-foreground">
									{job.llmModel ?? job.llmModelVersion ?? "—"}
								</TableCell>
								<TableCell className="text-muted-foreground">
									{formatDistanceToNow(new Date(job.createdAt), { addSuffix: true })}
								</TableCell>
								<TableCell>
									{job.deliveryStatus ? (
										<Badge variant={deliveryBadgeVariant(job.deliveryStatus)}>
											{DELIVERY_STATUS_LABELS[job.deliveryStatus]}
										</Badge>
									) : (
										<span className="text-muted-foreground">—</span>
									)}
								</TableCell>
								<TableCell className="text-right text-muted-foreground">
									<span className="tabular-nums">
										{formatTokens(job.llmTotalInputTokens)} /{" "}
										{formatTokens(job.llmTotalOutputTokens)}
									</span>
									<span className="ml-2">{formatCostUsd(job.llmCostUsd)}</span>
								</TableCell>
								<TableCell className="text-right">
									<div className="flex justify-end">
										<Button
											variant="ghost"
											size="icon-sm"
											aria-label={`View job ${job.id} details`}
											onClick={(e) => {
												e.stopPropagation();
												onSelectJob(job);
											}}
										>
											<ChevronRight className="h-4 w-4" />
										</Button>
									</div>
								</TableCell>
							</TableRow>
						))}
					</TableBody>
				</Table>
			)}
		</div>
	);
}
