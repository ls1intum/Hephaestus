import { Skeleton } from "@/components/ui/skeleton";
import { TableBody, TableCell, TableRow } from "@/components/ui/table";

export interface TableRowsSkeletonProps {
	/**
	 * One entry per column, in order — a Tailwind width class for a column that holds content, or
	 * `null` for one that doesn't (a trailing action slot has nothing to promise). Length must match
	 * the header's column count, or the placeholder columns won't line up with the real ones.
	 */
	columns: (string | null)[];
	rows?: number;
}

/**
 * Placeholder `<tbody>` rows for a table whose `<thead>` is already mounted.
 *
 * A skeleton is only worth rendering if it reserves the box the content will occupy; a stack of
 * full-width grey bars standing in for a table promises the wrong shape and guarantees a shift when
 * the real header and columns arrive. Pairing this with the table's real header means the only thing
 * that changes on resolve is the text.
 */
export function TableRowsSkeleton({ columns, rows = 5 }: TableRowsSkeletonProps) {
	return (
		<TableBody>
			{Array.from({ length: rows }, (_, rowIndex) => (
				<TableRow key={rowIndex}>
					{columns.map((width, cellIndex) => (
						<TableCell key={cellIndex}>
							{width && <Skeleton className={`h-5 ${width}`} />}
						</TableCell>
					))}
				</TableRow>
			))}
		</TableBody>
	);
}
