import type { LlmUsageByDay, LlmUsageByJobType } from "@/api/types.gen";
import { formatCostUsd, formatTokens } from "@/components/admin/ai/jobUtils";
import { TableRowsSkeleton } from "@/components/admin/integrations/TableRowsSkeleton";
import {
	Table,
	TableBody,
	TableCaption,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { formatUsageDay, JOB_TYPE_LABELS } from "./usageUtils";

const JOB_TYPE_SKELETON_COLUMNS = [
	"w-32",
	"w-16",
	"w-16",
	"w-20",
	"w-16",
	"w-20",
	"w-20",
	"w-12",
	"w-12",
];
const DAY_SKELETON_COLUMNS = ["w-16", "w-16", "w-16", "w-16", "w-12"];

export interface LlmUsageByJobTypeTableProps {
	/** Omit while the report is loading to keep the table shell stable. */
	rows?: LlmUsageByJobType[];
}

/** Per-job-type usage with spend split by who pays for the model. */
export function LlmUsageByJobTypeTable({ rows }: LlmUsageByJobTypeTableProps) {
	return (
		<Table containerClassName="rounded-md border">
			<TableCaption className="sr-only">AI spend by job type</TableCaption>
			<TableHeader>
				<TableRow>
					<TableHead scope="col">Job type</TableHead>
					<TableHead scope="col" className="text-right">
						Shared models
					</TableHead>
					<TableHead scope="col" className="text-right">
						Your provider
					</TableHead>
					<TableHead scope="col" className="text-right">
						Avg per event
					</TableHead>
					<TableHead scope="col" className="text-right">
						No price set
					</TableHead>
					<TableHead scope="col" className="text-right">
						Input tokens
					</TableHead>
					<TableHead scope="col" className="text-right">
						Output tokens
					</TableHead>
					<TableHead scope="col" className="text-right">
						Calls
					</TableHead>
					<TableHead scope="col" className="text-right">
						Events
					</TableHead>
				</TableRow>
			</TableHeader>
			{rows == null ? (
				<TableRowsSkeleton columns={JOB_TYPE_SKELETON_COLUMNS} rows={3} />
			) : (
				<TableBody>
					{rows.map((row) => (
						<TableRow key={row.jobType}>
							<TableCell className="font-medium">{JOB_TYPE_LABELS[row.jobType]}</TableCell>
							<TableCell className="text-right tabular-nums">
								{formatCostUsd(row.pricedTotalCostUsd)}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{formatCostUsd(row.byoTotalCostUsd)}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								<AvgPerEvent row={row} />
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{row.unpricedEventCount.toLocaleString()}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{formatTokens(row.inputTokens)}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{formatTokens(row.outputTokens)}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{row.totalCalls.toLocaleString()}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{row.events.toLocaleString()}
							</TableCell>
						</TableRow>
					))}
				</TableBody>
			)}
		</Table>
	);
}

/**
 * What one unit of this work costs on average — the figure people actually reason with ("a review
 * costs me about $0.14"), which a monthly total never gives them. The two money streams keep their
 * own averages: they are different money and are never added together, so when both are in play the
 * cell shows two labelled lines rather than one blended number.
 */
function AvgPerEvent({ row }: { row: LlmUsageByJobType }) {
	const parts = [
		{ key: "shared", label: "shared models", total: row.pricedTotalCostUsd },
		{ key: "provider", label: "your provider", total: row.byoTotalCostUsd },
	].filter((part) => part.total > 0);

	if (row.events <= 0 || parts.length === 0) {
		return <span className="text-muted-foreground">—</span>;
	}
	return (
		<div className="flex flex-col items-end">
			{parts.map((part) => (
				<span key={part.key}>
					≈ {formatCostUsd(part.total / row.events)}
					{parts.length > 1 && (
						<>
							{" "}
							<span className="text-xs text-muted-foreground">{part.label}</span>
						</>
					)}
				</span>
			))}
		</div>
	);
}

export interface LlmUsageByDayTableProps {
	/** Omit while the report is loading to keep the table shell stable. */
	rows?: LlmUsageByDay[];
}

/** Daily usage with the same two money streams as the job-type rollup. */
export function LlmUsageByDayTable({ rows }: LlmUsageByDayTableProps) {
	return (
		<Table containerClassName="rounded-md border">
			<TableCaption className="sr-only">AI spend by day</TableCaption>
			<TableHeader>
				<TableRow>
					<TableHead scope="col">Day</TableHead>
					<TableHead scope="col" className="text-right">
						Shared models
					</TableHead>
					<TableHead scope="col" className="text-right">
						Your provider
					</TableHead>
					<TableHead scope="col" className="text-right">
						No price set
					</TableHead>
					<TableHead scope="col" className="text-right">
						Events
					</TableHead>
				</TableRow>
			</TableHeader>
			{rows == null ? (
				<TableRowsSkeleton columns={DAY_SKELETON_COLUMNS} rows={3} />
			) : (
				<TableBody>
					{rows.map((row) => (
						<TableRow key={String(row.day)}>
							<TableCell className="font-medium">{formatUsageDay(row.day)}</TableCell>
							<TableCell className="text-right tabular-nums">
								{formatCostUsd(row.pricedTotalCostUsd)}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{formatCostUsd(row.byoTotalCostUsd)}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{row.unpricedEventCount.toLocaleString()}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{row.events.toLocaleString()}
							</TableCell>
						</TableRow>
					))}
				</TableBody>
			)}
		</Table>
	);
}
