import { createFileRoute } from "@tanstack/react-router";

import { scansSearchSchema } from "@/components/admin/practice-scans/scans-search";
import { ScansPage } from "@/components/admin/practice-scans/ScansPage";
import { useScanList } from "@/hooks/use-scans";
import { pageParam, useSearchState } from "@/lib/search-params";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/scans/")({
	validateSearch: scansSearchSchema,
	component: ScansRoute,
});

function ScansRoute() {
	const { workspaceSlug } = Route.useParams();
	const { page = 0, status, repositoryItemId } = Route.useSearch();
	const setSearch = useSearchState();
	const filters = { status, repositoryItemId };
	const scans = useScanList(workspaceSlug, page, filters, (next) => {
		void setSearch((previous) => ({ ...previous, page: pageParam(next) }));
	});
	return (
		<ScansPage
			workspaceSlug={workspaceSlug}
			scans={scans}
			filters={filters}
			// A new filter lists its scans from the first page.
			onFiltersChange={(next) => {
				void setSearch((previous) => ({ ...previous, ...next, page: undefined }));
			}}
		/>
	);
}
