import { Skeleton } from "@/components/ui/skeleton";

export interface ReviewResultsSkeletonProps {
	label: string;
}

export function ReviewResultsSkeleton({ label }: ReviewResultsSkeletonProps) {
	return (
		<div className="space-y-2 rounded-lg border p-4" role="status">
			<span className="sr-only">{label}</span>
			{Array.from({ length: 5 }, (_, index) => (
				<div key={index} className="flex items-center gap-4 py-3">
					<Skeleton className="h-4 flex-1" />
					<Skeleton className="h-5 w-24" />
					<Skeleton className="hidden h-4 w-40 md:block" />
				</div>
			))}
		</div>
	);
}
