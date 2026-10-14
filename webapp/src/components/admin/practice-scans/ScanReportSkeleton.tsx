import { PracticeTabsSkeleton } from "@/components/common/practice-tabs";
import { PageLayout } from "@/components/layout/PageLayout";
import { Skeleton } from "@/components/ui/skeleton";

/** The report's shape while its first read is on the way: header, counts, tabs and a table. */
export function ScanReportSkeleton() {
	return (
		<PageLayout aria-busy>
			<span className="sr-only">Loading scan report</span>
			<div className="space-y-2">
				<Skeleton className="h-4 w-16" />
				<Skeleton className="h-8 w-72 max-w-full" />
				<Skeleton className="h-5 w-96 max-w-full" />
			</div>
			<div className="space-y-4 rounded-lg border p-4">
				<div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
					{Array.from({ length: 2 }, (_, index) => (
						<Skeleton key={index} className="h-12 w-28" />
					))}
				</div>
				<div className="grid grid-cols-2 gap-4 border-t pt-4 sm:grid-cols-4 lg:grid-cols-6">
					{Array.from({ length: 9 }, (_, index) => (
						<Skeleton key={index} className="h-12 w-24" />
					))}
				</div>
			</div>
			<PracticeTabsSkeleton tabs={3} />
			<div className="space-y-2">
				{Array.from({ length: 5 }, (_, index) => (
					<Skeleton key={index} className="h-10 w-full" />
				))}
			</div>
		</PageLayout>
	);
}
