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

/** Below this many pages every page gets its own token and no window is needed. */
const WINDOW_THRESHOLD = 7;

/**
 * Windowed page tokens: first, last, current ±1, with "ellipsis" gaps between.
 *
 * A gap of exactly one page renders that page instead of an ellipsis. At eight pages on page four
 * the window is `1 _ 3 4 5 _ 8`, and an ellipsis in the first hole stands for page 2 alone — wider
 * on screen than the number it replaces, and unclickable where the number is not. Filling it costs
 * one token and only ever in this shape, since a hole of two or more still elides.
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
 * Pager for a table that changes pages by calling back, not by navigating. The one pager for all of
 * them: the client-paged admin tables (users, achievements) and the server-paged ones (agent
 * activity, sync jobs) all mount this rather than each assembling its own out of the kit's parts.
 *
 * Every control is a `<button disabled>`, not a styled `<a>`: the kit's `PaginationLink` is an anchor
 * for href-driven pagination, and an anchor with no `href` is neither focusable nor announced as a
 * control — dimming it with `pointer-events-none` leaves it reachable by keyboard and reported to a
 * screen reader as enabled, which is the WCAG 2.2 SC 4.1.2 failure the native `disabled` attribute
 * exists to avoid. This is the shape the shadcn data-table guide uses for action pagination.
 *
 * The numbered tokens also carry `aria-current="page"`, so a caller needs no separate "Page N of M"
 * line to say where the reader is.
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
			{/* The windowed pager fits one line at default text size (~206 px at a 320 px viewport), so
			    this is not about the reflow width. It is about SC 1.4.4 Resize Text: the targets are
			    `rem`-sized, so text-only zoom grows them while the viewport stays put, and past ~150 %
			    they no longer fit. Wrapping absorbs that instead of pushing the page into horizontal
			    scrolling. `justify-center` keeps the wrapped rows aligned with the nav. */}
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
