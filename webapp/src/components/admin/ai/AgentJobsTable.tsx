import { AlertCircle, Bot, ChevronRight } from "lucide-react";
import { useId } from "react";
import type { AgentJob } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
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
import { cn } from "@/lib/utils";
import {
	DELIVERY_STATUS_LABELS,
	deliveryBadgeVariant,
	formatTokens,
	holdReasonCopy,
	type JobStatus,
	jobWait,
	modelLabel,
	STATUS_LABELS,
	statusBadgeVariant,
} from "./job-utils";

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
				{/* `w-40` is 10rem, which under SC 1.4.4 text-only zoom at 200 % becomes 320 px — the
				    entire reflow viewport, leaving no room for the label beside it. `max-w-full` caps the
				    control against the row and `flex-wrap` lets it drop below its label instead of
				    overflowing once the two no longer fit side by side. */}
				<Field orientation="horizontal" className="w-auto max-w-full flex-wrap text-sm">
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
						<SelectTrigger id={statusFilterId} size="sm" className="w-40 max-w-full">
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
					</EmptyHeader>
				</Empty>
			) : (
				// Seven nowrap columns cannot reflow to 320 px, so this table takes the SC 1.4.10 data
				// exception: it scrolls inside its own bordered container while the page around it does not.
				<Table containerClassName="rounded-md border">
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
							const wait = jobWait(job);
							return (
								<TableRow key={job.id}>
									<TableCell>
										{/* A hold is a sub-state of QUEUED, not a peer of it, so it qualifies the status
										    badge from underneath instead of standing beside it as a second badge. The
										    server's `JobStatus` is what the filter above sends, and a "Held" badge would
										    advertise a value that filter cannot ask for. */}
										<Badge variant={statusBadgeVariant(job.status)}>
											{STATUS_LABELS[job.status]}
										</Badge>
										{wait && (
											<div
												className={cn(
													"mt-1 max-w-56 text-xs",
													// A hold needs someone to lift it; a backoff clears itself, so it stays
													// muted. Neither is destructive — the run is waiting, not broken.
													wait.kind === "hold" ? "text-warning" : "text-muted-foreground",
												)}
											>
												{wait.kind === "hold"
													? `Held · ${holdReasonCopy(wait.reason).label} · due `
													: "Backing off · due "}
												{/* Same reason as the Created cell: a tooltip trigger is a button, and one
												    per row would sit between every row and its Details action. */}
												<RelativeTime value={job.availableAt} tooltip={false} />
											</div>
										)}
									</TableCell>
									<TableCell className="max-w-40 truncate">{modelLabel(job)}</TableCell>
									<TableCell className="text-muted-foreground">
										{job.llmModel ?? job.llmModelVersion ?? "—"}
									</TableCell>
									<TableCell className="text-muted-foreground">
										{/* No tooltip: its trigger is a button, and one per row would put a tab stop
										    between every row and its Details action. */}
										<RelativeTime value={job.createdAt} tooltip={false} />
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
									</TableCell>
									<TableCell className="text-right">
										{/* The button is the only affordance: a click handler on the row itself is
										    unreachable by keyboard and invisible to assistive tech. */}
										<div className="flex justify-end">
											<Button
												variant="ghost"
												size="icon-sm"
												// Never the relative phrase: an accessible name would go stale the moment the
												// shared clock ticked past it.
												aria-label={`View details for the ${STATUS_LABELS[job.status].toLowerCase()} ${modelLabel(job)} run`}
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
