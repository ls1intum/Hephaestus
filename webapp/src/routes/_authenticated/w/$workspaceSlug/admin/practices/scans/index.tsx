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
	const { page = 0 } = Route.useSearch();
	const setSearch = useSearchState();
	const scans = useScanList(workspaceSlug, page, (next) => {
		void setSearch((previous) => ({ ...previous, page: pageParam(next) }));
	});
	return <ScansPage workspaceSlug={workspaceSlug} scans={scans} />;
}
