import type { ScanCounts as ScanCountsData } from "@/api/types.gen";
import { OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import { SCAN_ENTRY_DEFS } from "@/components/practice-vocabulary/scan-entry-defs";

export interface ScanCountsProps {
	counts: ScanCountsData;
	recorded: boolean;
}

const UNKNOWN = "Not known yet";

function Figure({ label, value, note }: { label: string; value: string | number; note?: string }) {
	return (
		<div className="min-w-0 space-y-0.5">
			<dt className="text-xs text-muted-foreground">{label}</dt>
			<dd className="text-lg font-semibold tabular-nums">
				{value}
				{note !== undefined && (
					<>
						{" "}
						<span className="text-xs font-normal text-muted-foreground">{note}</span>
					</>
				)}
			</dd>
		</div>
	);
}

// File counts are distinct files; other counts are practice-file pairs. Unknown totals cannot supply percentages.
export function ScanCounts({ counts, recorded }: ScanCountsProps) {
	return (
		<section aria-label="Counts" className="space-y-4 rounded-lg border p-4">
			<dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
				<Figure label="Files" value={counts.files ?? UNKNOWN} />
				<Figure label="Practice and file pairs" value={counts.practiceItemPairs ?? UNKNOWN} />
			</dl>
			<dl className="grid grid-cols-2 gap-4 border-t pt-4 sm:grid-cols-4 lg:grid-cols-6">
				<Figure label={SCAN_ENTRY_DEFS.JUDGED.label} value={counts.reviewed} />
				<Figure label={OUTCOME_DEFS.MET.label} value={counts.met} />
				<Figure label={OUTCOME_DEFS.NOT_MET.label} value={counts.notMet} />
				<Figure label={OUTCOME_DEFS.DOES_NOT_APPLY.label} value={counts.doesNotApply} />
				<Figure label={OUTCOME_DEFS.COULD_NOT_DETERMINE.label} value={counts.couldNotDetermine} />
				<Figure
					label="Problems"
					value={counts.problems}
					note={counts.openProblems === counts.problems ? undefined : `${counts.openProblems} open`}
				/>
				<Figure label={SCAN_ENTRY_DEFS.REUSED.label} value={counts.reused} />
				<Figure label={SCAN_ENTRY_DEFS.WITHHELD.label} value={counts.withheld} />
				<Figure label={SCAN_ENTRY_DEFS.NOT_REVIEWED.label} value={counts.notReviewed} />
			</dl>
			<p className="text-sm text-muted-foreground">
				Each file can be reviewed against more than one practice. Reviewed, withheld and not
				reviewed count practice and file pairs. Reused results are included in reviewed.
				{recorded && " As recorded when the scan closed."}
			</p>
		</section>
	);
}
