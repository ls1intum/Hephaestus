import { cn } from "cn";
import { Skeleton } from "@/components/ui/skeleton";
import { TableBody, TableCell, TableRow } from "@/components/ui/table";
import { hasText } from "@/lib/text";

/** A column whose cells need more than a width: a column hidden below `lg`, or right-aligned figures. */
export interface TableSkeletonColumn {
	/** A Tailwind width class, or `null` for a column with nothing to promise. */
	width: string | null;
	/** The column's own classes, so a cell that the header hides below `lg` is hidden here too. */
	className?: string;
	numeric?: boolean;
}

export interface TableRowsSkeletonProps {
	/**
	 * One entry per column, in order — a Tailwind width class for a column that holds content, or
	 * `null` for one that doesn't (a trailing action slot has nothing to promise). Length must match
	 * the header's column count, or the placeholder columns won't line up with the real ones.
	 */
	columns: readonly (string | null | TableSkeletonColumn)[];
	rows?: number;
	/**
	 * The rows stand after rows already drawn, for the next page: a line then keeps the last row
	 * apart, since a body's last row drops its border, and the placeholders stay out of the
	 * accessibility tree while the table is busy.
	 */
	continues?: boolean;
}

/**
 * Placeholder `<tbody>` rows for a table whose `<thead>` is already mounted, so the skeleton reserves
 * the real column box. A stack of full-width grey bars would promise the wrong shape and shift the
 * layout when the real columns arrive; pairing this with the real header means only the text changes
 * on resolve.
 */
export function TableRowsSkeleton({
	columns,
	rows = 5,
	continues = false,
}: TableRowsSkeletonProps) {
	return (
		<TableBody>
			{continues && (
				<tr aria-hidden data-separator>
					<td colSpan={columns.length} className="border-t p-0" />
				</tr>
			)}
			{Array.from({ length: rows }, (_, rowIndex) => (
				<TableRow
					key={rowIndex}
					variant="static"
					aria-hidden={continues || undefined}
					data-loading-row={continues || undefined}
				>
					{columns.map((column, cellIndex) => {
						const plain = column === null || typeof column === "string";
						const width = plain ? column : column.width;
						return (
							<TableCell key={cellIndex} className={plain ? undefined : column.className}>
								{hasText(width) && (
									<SkeletonBar
										className={cn("h-5", width, !plain && column.numeric === true && "ml-auto")}
										capped={continues}
									/>
								)}
							</TableCell>
						);
					})}
				</TableRow>
			))}
		</TableBody>
	);
}

/**
 * A bar among rows already drawn takes no width of its own, so it never widens a column that the
 * real rows have set; it is cut at the cell's edge instead.
 */
function SkeletonBar({ className, capped }: { className: string; capped: boolean }) {
	if (!capped) {
		return <Skeleton className={className} />;
	}
	return (
		<div className="w-0 min-w-full overflow-hidden">
			<Skeleton className={cn(className, "max-w-full")} />
		</div>
	);
}
