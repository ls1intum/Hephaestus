import { Skeleton } from "@/components/ui/skeleton";

export interface ReviewResultsSkeletonProps {
	label: string;
	/** Matches the page size the list is about to show, so the page does not jump when it arrives. */
	rows?: number;
}

/**
 * The shape of {@link ReviewRow}, before the rows exist.
 *
 * <p>It draws the same four regions in the same places: the icon tile, a title bar, a meta line, and
 * the chips on the right. The skeleton it replaces drew three equal bars in a bordered box that
 * matched no list on any screen — which is the inconsistency the product owner flagged twice, and it
 * was inevitable while each list had two renderings and the skeleton had a third.
 */
export function ReviewResultsSkeleton({ label, rows = 5 }: ReviewResultsSkeletonProps) {
	return (
		<div className="divide-y rounded-lg border" role="status">
			<span className="sr-only">{label}</span>
			{Array.from({ length: rows }, (_, index) => (
				<div key={index} className="flex items-start gap-3 p-4">
					<Skeleton className="mt-0.5 size-8 shrink-0 rounded-md" />
					<div className="flex min-w-0 flex-1 flex-wrap items-start justify-between gap-x-4 gap-y-2">
						<div className="min-w-0 flex-1 basis-64 space-y-2">
							<Skeleton className="h-4 w-full max-w-80" />
							<Skeleton className="h-3 w-full max-w-56" />
						</div>
						<div className="flex gap-1.5">
							<Skeleton className="h-5 w-20 rounded-md" />
							<Skeleton className="hidden h-5 w-16 rounded-md sm:block" />
						</div>
					</div>
				</div>
			))}
		</div>
	);
}
