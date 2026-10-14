import type { ScanPracticeReport } from "@/api/types.gen";
import { StatusBadge } from "@/components/common/StatusBadge";
import { REVIEW_PRACTICE_STATUS_DEFS } from "@/components/practice-vocabulary/review-status-defs";
import {
	REMOVAL_GUARD_DEFS,
	SCAN_COMPARISON_DEFS,
} from "@/components/practice-vocabulary/scan-baseline-defs";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";

import { ofTotal, withheldSentence } from "./scan-display";

export interface ScanPracticeTableProps {
	practices: readonly ScanPracticeReport[];
}

/**
 * Each practice the scan pinned, with where it stands, whether it can be compared with the scan
 * before, and its own pair counts. A practice whose listing did not finish says so: its totals are
 * what the scan reached, not every file.
 */
export function ScanPracticeTable({ practices }: ScanPracticeTableProps) {
	if (practices.length === 0) {
		return <p className="text-sm text-muted-foreground">This scan reached no practice.</p>;
	}
	return (
		<Table bordered aria-label="Practices in this scan">
			<TableHeader>
				<TableRow>
					<TableHead scope="col">Practice</TableHead>
					<TableHead scope="col">Status</TableHead>
					<TableHead scope="col">Comparison</TableHead>
					<TableHead scope="col" className="text-right">
						Reviewed
					</TableHead>
					<TableHead scope="col" className="text-right">
						Not met
					</TableHead>
					<TableHead scope="col" className="text-right">
						Problems
					</TableHead>
					<TableHead scope="col" className="text-right">
						Withheld
					</TableHead>
					<TableHead scope="col" className="text-right">
						Not reviewed
					</TableHead>
				</TableRow>
			</TableHeader>
			<TableBody>
				{practices.map((practice) => (
					<TableRow key={practice.practiceId}>
						<TableCell className="max-w-xs">
							<span className="block truncate font-medium">{practice.practiceName}</span>
							<span className="block text-xs text-muted-foreground">
								Version {practice.number}
								{!practice.listingComplete && ` · ${REMOVAL_GUARD_DEFS.INCOMPLETE_LISTING.label}`}
							</span>
						</TableCell>
						<TableCell>
							<StatusBadge def={REVIEW_PRACTICE_STATUS_DEFS[practice.status]} />
							{practice.status === "WITHHELD" && (
								<span className="mt-1 block text-xs text-muted-foreground">
									{withheldSentence(practice.reason)}
								</span>
							)}
						</TableCell>
						<TableCell>
							<StatusBadge
								def={SCAN_COMPARISON_DEFS[practice.comparison]}
								title={SCAN_COMPARISON_DEFS[practice.comparison].description}
							/>
						</TableCell>
						<TableCell numeric>
							{ofTotal(practice.counts.reviewed, practice.counts.practiceItemPairs)}
						</TableCell>
						<TableCell numeric>{practice.counts.notMet}</TableCell>
						<TableCell numeric>{practice.counts.problems}</TableCell>
						<TableCell numeric>{practice.counts.withheld}</TableCell>
						<TableCell numeric>{practice.counts.notReviewed}</TableCell>
					</TableRow>
				))}
			</TableBody>
		</Table>
	);
}
