import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
	Pagination,
	PaginationContent,
	PaginationEllipsis,
	PaginationItem,
} from "@/components/ui/pagination";

export interface TablePaginationProps {
	/** Zero-based, matching the Spring page the table is showing. */
	page: number;
	totalPages: number;
	onPageChange: (page: number) => void;
	className?: string;
}

const WINDOW_THRESHOLD = 7;

/**
 * Windowed page tokens: first, last, current ±1, with "ellipsis" gaps between. A gap of exactly one
 * page renders that page instead — an ellipsis standing for a single number is wider than the
 * number it replaces, and unclickable where the number is not.
 */
function paginationItems(current: number, total: number): (number | "ellipsis")[] {
	if (total <= WINDOW_THRESHOLD) {
		return Array.from({ length: total }, (_, i) => i);
	}
	const pages = new Set<number>([0, total - 1, current, current - 1, current + 1]);
	const sorted = [...pages].filter((p) => p >= 0 && p < total).sort((a, b) => a - b);
	const items: (number | "ellipsis")[] = [];
	let previous: number | undefined;
	for (const page of sorted) {
		if (previous !== undefined) {
			if (page - previous === 2) {
				items.push(page - 1);
			} else if (page - previous > 2) {
				items.push("ellipsis");
			}
		}
		items.push(page);
		previous = page;
	}
	return items;
}

/**
 * The pager for a table that changes pages by calling back rather than by navigating.
 *
 * Every control is a `<button disabled>` rather than the kit's `PaginationLink`, which is an anchor:
 * an `href`-less anchor dimmed with `pointer-events-none` stays in the tab order and is still
 * announced as an available control (WCAG 2.2 SC 4.1.2).
 */
export function TablePagination({
	page,
	totalPages,
	onPageChange,
	className,
}: TablePaginationProps) {
	if (totalPages <= 1) {
		return null;
	}

	return (
		<Pagination className={className}>
			{/* Wrapping absorbs text-only zoom (WCAG 2.2 SC 1.4.4) instead of scrolling horizontally. */}
			<PaginationContent className="flex-wrap justify-center gap-y-1">
				<PaginationItem>
					<Button
						variant="ghost"
						className="pl-1.5!"
						aria-label="Go to previous page"
						disabled={page <= 0}
						onClick={() => onPageChange(Math.max(0, page - 1))}
					>
						<ChevronLeftIcon data-icon="inline-start" />
						<span className="hidden sm:block">Previous</span>
					</Button>
				</PaginationItem>
				{paginationItems(page, totalPages).map((item, index) =>
					item === "ellipsis" ? (
						<PaginationItem key={`ellipsis-${index}`}>
							<PaginationEllipsis />
						</PaginationItem>
					) : (
						<PaginationItem key={item}>
							<Button
								variant={item === page ? "outline" : "ghost"}
								size="icon"
								aria-label={`Go to page ${item + 1}`}
								aria-current={item === page ? "page" : undefined}
								onClick={() => onPageChange(item)}
							>
								{item + 1}
							</Button>
						</PaginationItem>
					),
				)}
				<PaginationItem>
					<Button
						variant="ghost"
						className="pr-1.5!"
						aria-label="Go to next page"
						disabled={page >= totalPages - 1}
						onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))}
					>
						<span className="hidden sm:block">Next</span>
						<ChevronRightIcon data-icon="inline-end" />
					</Button>
				</PaginationItem>
			</PaginationContent>
		</Pagination>
	);
}
