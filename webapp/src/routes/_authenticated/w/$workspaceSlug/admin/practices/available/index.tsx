import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { Library } from "lucide-react";
import { listAdoptablePracticesOptions } from "@/api/@tanstack/react-query.gen";
import { AvailablePracticeList } from "@/components/admin/practice-adoption/AvailablePracticeList";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Spinner } from "@/components/ui/spinner";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/available/")(
	{
		head: workspaceAdminHead("Available practices"),
		component: AvailablePracticesRoute,
	},
);

function AvailablePracticesRoute() {
	const { workspaceSlug } = Route.useParams();
	const practicesQuery = useQuery({
		...listAdoptablePracticesOptions({ path: { workspaceSlug } }),
	});

	return (
		<PageLayout>
			<PageHeader
				icon={<Library />}
				title="Available practices"
				description="Review and adopt practices offered by the instance catalog."
			/>
			{practicesQuery.isPending ? (
				<div className="flex h-64 items-center justify-center">
					<Spinner className="size-8" />
				</div>
			) : practicesQuery.isError ? (
				<QueryErrorAlert
					error={practicesQuery.error}
					title="Couldn't load available practices"
					onRetry={() => practicesQuery.refetch()}
				/>
			) : (
				<AvailablePracticeList workspaceSlug={workspaceSlug} practices={practicesQuery.data} />
			)}
		</PageLayout>
	);
}
