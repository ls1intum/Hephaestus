import type { LlmUsageByDay, LlmUsageByJobType } from "@/api/types.gen";
import {
	formatCostUsd,
	formatRateUsd,
	formatTokens,
	MoneyCell,
} from "@/components/admin/ai/jobUtils";
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
import { type Fx, FxSpendLine } from "./fx";
import { formatUsageDay, JOB_TYPE_LABELS } from "./usageUtils";

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
	/** Omit while the report is loading to keep the table shell stable. */
	rows?: LlmUsageByJobType[];
	/**
	 * Display-only conversion for the totals row. Body cells stay USD-only: they are sub-dollar
	 * figures in an already wide table, and an estimate on each would cost the column width that
	 * makes the table readable at all.
	 */
	fx?: Fx;
}

/** Per-job-type usage with spend split by who pays for the model. */
export function LlmUsageByJobTypeTable({ rows, fx }: LlmUsageByJobTypeTableProps) {
	// Convert the total, never the sum of converted rows — rounding each row first and adding those
	// up produces a figure that disagrees with the USD total sitting right next to it.
	// One row needs no footer: its "total" would restate the line directly above it.
	const totals =
		rows == null || rows.length < 2
			? null
			: {
					priced: sumBy(rows, (row) => row.instanceTotalCostUsd),
					ownProvider: sumBy(rows, (row) => row.ownProviderTotalCostUsd),
					unpriced: sumBy(rows, (row) => row.unpricedEventCount),
					inputTokens: sumBy(rows, (row) => row.inputTokens),
					outputTokens: sumBy(rows, (row) => row.outputTokens),
					calls: sumBy(rows, (row) => row.totalCalls),
					events: sumBy(rows, (row) => row.events),
				};
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
								<MoneyCell>{formatCostUsd(row.instanceTotalCostUsd)}</MoneyCell>
							</TableCell>
							<TableCell className="text-right tabular-nums">
								<MoneyCell>{formatCostUsd(row.ownProviderTotalCostUsd)}</MoneyCell>
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
						{/* No blended average: the two money streams are different money and averaging
						    across job types on top of that would mean nothing to anyone. */}
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
 * What one unit of this work costs on average — the figure people actually reason with ("a review
 * costs me about $0.14"), which a monthly total never gives them. The two money streams keep their
 * own averages: they are different money and are never added together, so when both are in play the
 * cell shows two labelled lines rather than one blended number.
 *
 * An average is a rate, not an amount spent, so it renders through `formatRateUsd`: rounding it to
 * cents — or worse, flooring it to the `<$0.01` bound — destroys the exact number this column
 * exists to give. And no `≈`: on this page that glyph means "converted currency" (see `fx.tsx`),
 * the header already says "Avg", and a bare `≈` is announced as "tilde operator" anyway.
 */
function AvgPerEvent({ row }: { row: LlmUsageByJobType }) {
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
	/** Omit while the report is loading to keep the table shell stable. */
	rows?: LlmUsageByDay[];
	/** Display-only conversion for the totals row; per-day cells stay USD-only. */
	fx?: Fx;
}

/** Daily usage with the same two money streams as the job-type rollup. */
export function LlmUsageByDayTable({ rows, fx }: LlmUsageByDayTableProps) {
	// Same rule as the job-type rollup: one conversion applied to the USD total, and no footer for a
	// single row.
	const totals =
		rows == null || rows.length < 2
			? null
			: {
					priced: sumBy(rows, (row) => row.instanceTotalCostUsd),
					ownProvider: sumBy(rows, (row) => row.ownProviderTotalCostUsd),
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
