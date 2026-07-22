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

const JOB_TYPE_SKELETON_COLUMNS = ["w-32", "w-16", "w-16", "w-16", "w-20", "w-20", "w-12", "w-12"];
const DAY_SKELETON_COLUMNS = ["w-16", "w-16", "w-16", "w-16", "w-12"];

export interface LlmUsageByJobTypeTableProps {
	/** Omit while the report is loading to keep the table shell stable. */
	rows?: LlmUsageByJobType[];
}

/** Per-job-type usage with spend split by who funds the model. */
export function LlmUsageByJobTypeTable({ rows }: LlmUsageByJobTypeTableProps) {
	return (
		<Table containerClassName="rounded-md border">
			<TableCaption className="sr-only">AI spend by job type</TableCaption>
			<TableHeader>
				<TableRow>
					<TableHead scope="col">Job type</TableHead>
					<TableHead scope="col" className="text-right">
						Instance-funded
					</TableHead>
					<TableHead scope="col" className="text-right">
						Workspace-owned
					</TableHead>
					<TableHead scope="col" className="text-right">
						Unpriced calls
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

export interface LlmUsageByDayTableProps {
	/** Omit while the report is loading to keep the table shell stable. */
	rows?: LlmUsageByDay[];
}

/** Daily usage with the same funding split as the job-type rollup. */
export function LlmUsageByDayTable({ rows }: LlmUsageByDayTableProps) {
	return (
		<Table containerClassName="rounded-md border">
			<TableCaption className="sr-only">AI spend by day</TableCaption>
			<TableHeader>
				<TableRow>
					<TableHead scope="col">Day</TableHead>
					<TableHead scope="col" className="text-right">
						Instance-funded
					</TableHead>
					<TableHead scope="col" className="text-right">
						Workspace-owned
					</TableHead>
					<TableHead scope="col" className="text-right">
						Unpriced calls
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
