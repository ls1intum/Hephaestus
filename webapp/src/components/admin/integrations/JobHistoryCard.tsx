import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { IntegrationCardHeading } from "./IntegrationCardHeading";
import { SyncJobsTable, type SyncJobsTableProps } from "./SyncJobsTable";

/**
 * "Job history" card shell shared by the SCM, Slack and Outline integration routes: a `Card` with
 * an `<h2>` heading wrapping the paginated {@link SyncJobsTable}.
 */
export function JobHistoryCard(props: SyncJobsTableProps) {
	return (
		<Card>
			<CardHeader>
				<IntegrationCardHeading>Job history</IntegrationCardHeading>
			</CardHeader>
			<CardContent>
				<SyncJobsTable {...props} />
			</CardContent>
		</Card>
	);
}
