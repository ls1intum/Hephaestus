import { useQuery } from "@tanstack/react-query";
import { getMyPracticeReportOptions } from "@/api/@tanstack/react-query.gen";
import { FocusQueue, FocusQueueSkeleton } from "@/components/practices/FocusQueue";
import { Button } from "@/components/ui/button";

export interface MyPracticesPageProps {
	workspaceSlug: string;
}

/**
 * The developer self view container: fetches the caller's own practice report and hands it to
 * the {@link FocusQueue}. The transparency line below the title states plainly who else can
 * see this data and that detailed views are recorded.
 */
export function MyPracticesPage({ workspaceSlug }: MyPracticesPageProps) {
	const reportQuery = useQuery(getMyPracticeReportOptions({ path: { workspaceSlug } }));

	return (
		<div className="container mx-auto max-w-4xl py-6">
			<div className="mb-6 flex flex-col gap-1">
				<h1 className="text-3xl font-bold">My practices</h1>
				<p className="text-sm text-muted-foreground">
					What to focus on this cycle, grounded in your own pull requests and issues.
				</p>
				<p className="text-xs text-muted-foreground">
					Workspace admins can see your practice status to support mentoring, and every detailed
					view is recorded.
				</p>
			</div>
			{reportQuery.isLoading && <FocusQueueSkeleton />}
			{reportQuery.isError && (
				<div className="flex max-w-xl flex-col items-start gap-2 rounded-lg border border-dashed px-4 py-6">
					<p className="text-sm text-muted-foreground">
						Your practice report could not be loaded right now.
					</p>
					<Button variant="outline" size="sm" onClick={() => reportQuery.refetch()}>
						Try again
					</Button>
				</div>
			)}
			{reportQuery.isSuccess && <FocusQueue cards={reportQuery.data} />}
		</div>
	);
}
