import { formatDistanceToNow } from "date-fns";
import { AlertCircle, Bot, ChevronRight } from "lucide-react";
import { useId } from "react";
import type { AgentJob } from "@/api/types.gen";
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
	TableCaption,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import {
	DELIVERY_STATUS_LABELS,
	deliveryBadgeVariant,
	formatCostUsd,
	formatTokens,
	type JobStatus,
	modelLabel,
	STATUS_LABELS,
	statusBadgeVariant,
} from "./jobUtils";

const FILTER_ALL = "ALL";

export interface AgentJobsTableProps {
	jobs: AgentJob[];
	isLoading: boolean;
	isError?: boolean;
	statusFilter: JobStatus | "ALL";
	onStatusFilterChange: (status: JobStatus | "ALL") => void;
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
	isLoading,
	isError = false,
	statusFilter,
	onStatusFilterChange,
	onSelectJob,
	onRetry,
}: AgentJobsTableProps) {
	const statusFilterId = useId();
	return (
		<div className="space-y-4">
			<div className="flex flex-wrap items-center gap-3">
				{/* The visible "Status" text *is* the control's label, so the accessible name can never
				    drift from what a speech-control user reads out loud (WCAG SC 2.5.3). */}
				<Field orientation="horizontal" className="w-auto text-sm">
					<FieldLabel htmlFor={statusFilterId} className="text-muted-foreground">
						Status
					</FieldLabel>
					<Select
						items={STATUS_ITEMS}
						value={statusFilter}
						onValueChange={(value) =>
							onStatusFilterChange(value === FILTER_ALL ? "ALL" : (value as JobStatus))
						}
					>
						<SelectTrigger id={statusFilterId} size="sm" className="w-40">
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
				</Field>
			</div>

			{isError ? (
				<Alert variant="destructive">
					<AlertCircle />
					<AlertTitle>Couldn't load runs</AlertTitle>
					<AlertDescription>
						<p>Something went wrong on the way. Try again in a moment.</p>
						{onRetry && (
							<Button variant="outline" size="sm" className="mt-2" onClick={onRetry}>
								Retry
							</Button>
						)}
					</AlertDescription>
				</Alert>
			) : isLoading ? (
				<div className="flex h-40 items-center justify-center">
					<Spinner className="size-6" />
				</div>
			) : jobs.length === 0 ? (
				<Empty className="border">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<Bot />
						</EmptyMedia>
						<EmptyTitle>No runs yet</EmptyTitle>
						<EmptyDescription>
							A run appears here every time AI reviews a pull request, an issue, or a conversation.
						</EmptyDescription>
					</EmptyHeader>
				</Empty>
			) : (
				<Table>
					<TableCaption className="sr-only">AI runs for this workspace, newest first</TableCaption>
					<TableHeader>
						<TableRow>
							<TableHead scope="col">Status</TableHead>
							<TableHead scope="col">Model</TableHead>
							<TableHead scope="col">Model name</TableHead>
							<TableHead scope="col">Created</TableHead>
							<TableHead scope="col">Delivery</TableHead>
							<TableHead scope="col" className="text-right">
								Usage
							</TableHead>
							<TableHead scope="col" className="text-right">
								Details
							</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{jobs.map((job) => {
							const created = formatDistanceToNow(new Date(job.createdAt), { addSuffix: true });
							return (
								<TableRow key={job.id}>
									<TableCell>
										<Badge variant={statusBadgeVariant(job.status)}>
											{STATUS_LABELS[job.status]}
										</Badge>
									</TableCell>
									<TableCell className="max-w-40 truncate">{modelLabel(job)}</TableCell>
									<TableCell className="text-muted-foreground">
										{job.llmModel ?? job.llmModelVersion ?? "—"}
									</TableCell>
									<TableCell className="text-muted-foreground">{created}</TableCell>
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
										<span className="ml-2 tabular-nums">{formatCostUsd(job.llmCostUsd)}</span>
									</TableCell>
									<TableCell className="text-right">
										{/* The button is the only affordance: a click handler on the row itself is
										    unreachable by keyboard and invisible to assistive tech. */}
										<div className="flex justify-end">
											<Button
												variant="ghost"
												size="icon-sm"
												aria-label={`View details for the ${modelLabel(job)} run ${created}`}
												onClick={() => onSelectJob(job)}
											>
												<ChevronRight className="size-4" />
											</Button>
										</div>
									</TableCell>
								</TableRow>
							);
						})}
					</TableBody>
				</Table>
			)}
		</div>
	);
}
