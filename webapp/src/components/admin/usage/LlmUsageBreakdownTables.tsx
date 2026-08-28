import type { LlmUsageByJobType, WorkspaceLlmUsageReport } from "@/api/types.gen";
import { formatTokens, MoneyCell } from "@/components/admin/ai/job-utils";
import { TableRowsSkeleton } from "@/components/admin/integrations/TableRowsSkeleton";
import {
	Table,
	TableBody,
	TableCaption,
	TableCell,
	TableFooter,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { formatCostUsd, formatRateUsd } from "@/lib/money";

import { type Fx, FxSpendLine } from "./fx";
import { formatUsageDay, JOB_TYPE_LABELS } from "./usage-utils";

/** Counts only — integers, so adding them is exact. Money never goes through here: see the footers. */
function sumBy<T>(rows: T[], pick: (row: T) => number): number {
	return rows.reduce((total, row) => total + pick(row), 0);
}

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
	/**
	 * The whole report, not a bare `rows` array: re-adding the row costs in float would drift from the
	 * exact total the server put on the same payload, which is what the budget gate enforces against.
	 */
	report?: WorkspaceLlmUsageReport;
	/** Totals row only. Body cells stay USD-only — an estimate on each would cost the column width. */
	fx?: Fx;
}

export function LlmUsageByJobTypeTable({ report, fx }: LlmUsageByJobTypeTableProps) {
	const rows = report?.byJobType;
	// One row earns no footer.
	const totals =
		report == null || rows == null || rows.length < 2
			? null
			: {
					priced: report.instanceTotalCostUsd,
					ownProvider: report.ownProviderTotalCostUsd,
					unpriced: sumBy(rows, (row) => row.unpricedEventCount),
					inputTokens: sumBy(rows, (row) => row.inputTokens),
					outputTokens: sumBy(rows, (row) => row.outputTokens),
					calls: sumBy(rows, (row) => row.totalCalls),
					events: sumBy(rows, (row) => row.events),
				};
	return (
		<Table containerClassName="rounded-md border">
			<TableCaption className="sr-only">AI spend by run type</TableCaption>
			<TableHeader>
				<TableRow>
					<TableHead scope="col">Run type</TableHead>
					<TableHead scope="col" className="text-right">
						Shared models
					</TableHead>
					<TableHead scope="col" className="text-right">
						Your provider
					</TableHead>
					<TableHead scope="col" className="text-right">
						Avg per run
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
						Runs
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
								<MoneyCell>{formatCostUsd(row.instanceTotalCostUsd)}</MoneyCell>
							</TableCell>
							<TableCell className="text-right tabular-nums">
								<MoneyCell>{formatCostUsd(row.ownProviderTotalCostUsd)}</MoneyCell>
							</TableCell>
							<TableCell className="text-right tabular-nums">
								<AvgPerRun row={row} />
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
			{totals != null && (
				<TableFooter>
					<TableRow>
						<TableCell>Total</TableCell>
						<TableCell className="text-right tabular-nums">
							<MoneyCell>{formatCostUsd(totals.priced)}</MoneyCell>
							<FxSpendLine usd={totals.priced} fx={fx} />
						</TableCell>
						<TableCell className="text-right tabular-nums">
							<MoneyCell>{formatCostUsd(totals.ownProvider)}</MoneyCell>
							<FxSpendLine usd={totals.ownProvider} fx={fx} />
						</TableCell>
						{/* No blended average: the two money streams are different money. */}
						<TableCell className="text-right text-muted-foreground">—</TableCell>
						<TableCell className="text-right tabular-nums">
							{totals.unpriced.toLocaleString()}
						</TableCell>
						<TableCell className="text-right tabular-nums">
							{formatTokens(totals.inputTokens)}
						</TableCell>
						<TableCell className="text-right tabular-nums">
							{formatTokens(totals.outputTokens)}
						</TableCell>
						<TableCell className="text-right tabular-nums">
							{totals.calls.toLocaleString()}
						</TableCell>
						<TableCell className="text-right tabular-nums">
							{totals.events.toLocaleString()}
						</TableCell>
					</TableRow>
				</TableFooter>
			)}
		</Table>
	);
}

/**
 * An average is a rate, not an amount spent, so it renders through `formatRateUsd`: rounding to cents
 * destroys the number this column exists to give. And no `≈` — on this page that means "converted".
 * The two money streams are never blended into one figure.
 */
function AvgPerRun({ row }: { row: LlmUsageByJobType }) {
	const parts = [
		{ key: "shared", label: "shared models", total: row.instanceTotalCostUsd },
		{ key: "provider", label: "your provider", total: row.ownProviderTotalCostUsd },
	].filter((part) => part.total > 0);

	if (row.events <= 0 || parts.length === 0) {
		return <span className="text-muted-foreground">—</span>;
	}
	return (
		<div className="flex flex-col items-end">
			{parts.map((part) => (
				<span key={part.key}>
					{formatRateUsd(part.total / row.events)}
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
	/** The whole report: the footer reads the server's exact total rather than re-adding floats. */
	report?: WorkspaceLlmUsageReport;
	/** Display-only conversion for the totals row; per-day cells stay USD-only. */
	fx?: Fx;
}

export function LlmUsageByDayTable({ report, fx }: LlmUsageByDayTableProps) {
	const rows = report?.byDay;
	const totals =
		report == null || rows == null || rows.length < 2
			? null
			: {
					priced: report.instanceTotalCostUsd,
					ownProvider: report.ownProviderTotalCostUsd,
					unpriced: sumBy(rows, (row) => row.unpricedEventCount),
					events: sumBy(rows, (row) => row.events),
				};
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
						Runs
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
								<MoneyCell>{formatCostUsd(row.instanceTotalCostUsd)}</MoneyCell>
							</TableCell>
							<TableCell className="text-right tabular-nums">
								<MoneyCell>{formatCostUsd(row.ownProviderTotalCostUsd)}</MoneyCell>
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
			{totals != null && (
				<TableFooter>
					<TableRow>
						<TableCell>Total</TableCell>
						<TableCell className="text-right tabular-nums">
							<MoneyCell>{formatCostUsd(totals.priced)}</MoneyCell>
							<FxSpendLine usd={totals.priced} fx={fx} />
						</TableCell>
						<TableCell className="text-right tabular-nums">
							<MoneyCell>{formatCostUsd(totals.ownProvider)}</MoneyCell>
							<FxSpendLine usd={totals.ownProvider} fx={fx} />
						</TableCell>
						<TableCell className="text-right tabular-nums">
							{totals.unpriced.toLocaleString()}
						</TableCell>
						<TableCell className="text-right tabular-nums">
							{totals.events.toLocaleString()}
						</TableCell>
					</TableRow>
				</TableFooter>
			)}
		</Table>
	);
}
