import { Skeleton } from "@/components/ui/skeleton";

/**
 * The shapes the practice surfaces resolve into, drawn before the data arrives.
 *
 * A skeleton earns its place by holding the space the real content will take, so the page does not
 * jump when it arrives — [eBay](https://playbook.ebay.com/design-system/components/loading-skeleton)
 * puts that obligation on the author. So every row count here is a required prop: a default is a
 * count that can silently disagree with the caller's page size, which is the jump the skeleton
 * exists to prevent.
 *
 * No live region. These mount with their content already inside, and
 * [ARIA22](https://www.w3.org/WAI/WCAG22/Techniques/aria/ARIA22) requires the container to carry the
 * role *before* the message appears, so a `role="status"` added here would announce nothing. The
 * heading and landmark structure the reader navigates by is already mounted around them.
 */
export interface PracticeListSkeletonProps {
	/** How many rows the resolved list will render. */
	rows: number;
}

/** The shape of a practice row inside an area group. */
export function PracticeListSkeleton({ rows }: PracticeListSkeletonProps) {
	return (
		<div className="space-y-2" aria-hidden>
			{Array.from({ length: rows }, (_, index) => (
				<div key={index} className="flex items-center gap-3 rounded-lg border p-3">
					<Skeleton className="size-8 shrink-0 rounded-md" />
					<div className="min-w-0 flex-1 space-y-2">
						<Skeleton className="h-4 w-full max-w-64" />
						<Skeleton className="h-3 w-full max-w-40" />
					</div>
					<Skeleton className="h-5 w-16 shrink-0 rounded-md" />
				</div>
			))}
		</div>
	);
}

export interface PracticeTreeSkeletonProps {
	/** How many area groups the resolved tree will render. */
	areas: number;
	/** How many practices each group draws. */
	practicesPerArea: number;
}

/** The shape of the whole practice tree: a group heading, then its rows. */
export function PracticeTreeSkeleton({ areas, practicesPerArea }: PracticeTreeSkeletonProps) {
	return (
		<div className="space-y-6" aria-hidden>
			{Array.from({ length: areas }, (_, index) => (
				<div key={index} className="space-y-3">
					<div className="flex items-center gap-2">
						<Skeleton className="size-6 rounded-md" />
						<Skeleton className="h-5 w-full max-w-56" />
					</div>
					<PracticeListSkeleton rows={practicesPerArea} />
				</div>
			))}
		</div>
	);
}

/**
 * The shape of a practice panel's body: the rationale paragraph, then the example, then the
 * disclosure rows. Mirrors {@link PracticeDefinitionPreview}, so the panel does not reflow when the
 * definition lands.
 */
export function PracticeDefinitionSkeleton() {
	return (
		<div className="space-y-6" aria-hidden>
			<div className="space-y-2">
				<Skeleton className="h-5 w-full max-w-2xl" />
				<Skeleton className="h-5 w-full max-w-xl" />
			</div>
			<div className="space-y-2">
				<Skeleton className="h-4 w-32" />
				<Skeleton className="h-4 w-full max-w-lg" />
			</div>
			<div className="space-y-px pt-2">
				{Array.from({ length: 3 }, (_, index) => (
					<Skeleton key={index} className="h-12 w-full" />
				))}
			</div>
		</div>
	);
}
