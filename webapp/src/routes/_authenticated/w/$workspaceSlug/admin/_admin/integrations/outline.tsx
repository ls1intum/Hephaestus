import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { listConnectionSyncJobsOptions, listOptions } from "@/api/@tanstack/react-query.gen";
import { AdminOutlineSettings } from "@/components/admin/integrations/AdminOutlineSettings";
import { IntegrationPageHeader } from "@/components/admin/integrations/IntegrationPageHeader";
import { SyncJobsTable } from "@/components/admin/integrations/SyncJobsTable";
import { OutlineIcon } from "@/components/icons/brand";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/integrations/outline",
)({
	component: OutlineIntegrationPage,
});

const JOBS_PAGE_SIZE = 10;

function OutlineIntegrationPage() {
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";
	const [jobsPage, setJobsPage] = useState(0);

	// Shares the cache key with `AdminOutlineSettings`' own `listOptions` fetch — no duplicate
	// network call — just to resolve the connection id for the job-history table below.
	const { data: connections } = useQuery({
		...listOptions({ path: { workspaceSlug: slug } }),
		enabled: Boolean(workspaceSlug),
	});
	const connectionId = connections?.find(
		(connection) => connection.kind === "OUTLINE" && connection.state === "ACTIVE",
	)?.id;

	const {
		data: jobsPageData,
		isLoading: isJobsLoading,
		isError: isJobsError,
		error: jobsError,
		refetch: refetchJobs,
	} = useQuery({
		...listConnectionSyncJobsOptions({
			path: { workspaceSlug: slug, connectionId: connectionId ?? -1 },
			query: { page: jobsPage, size: JOBS_PAGE_SIZE },
		}),
		enabled: Boolean(workspaceSlug) && connectionId != null,
	});

	return (
		<div className="container mx-auto max-w-5xl space-y-8 py-6">
			<IntegrationPageHeader
				icon={<OutlineIcon className="size-6" />}
				title="Outline"
				description="Mirror Outline collections so their documents reach practice detection as context."
			/>

			{workspaceSlug != null && <AdminOutlineSettings workspaceSlug={slug} />}

			{connectionId != null && (
				<Card>
					<CardHeader>
						<h2 data-slot="card-title" className="text-base leading-snug font-medium">
							Job history
						</h2>
					</CardHeader>
					<CardContent>
						<SyncJobsTable
							jobs={jobsPageData?.content ?? []}
							isLoading={isJobsLoading}
							isError={isJobsError}
							error={jobsError}
							onRetry={() => refetchJobs()}
							page={jobsPage}
							totalPages={jobsPageData?.totalPages ?? 1}
							onPageChange={setJobsPage}
						/>
					</CardContent>
				</Card>
			)}
		</div>
	);
}
