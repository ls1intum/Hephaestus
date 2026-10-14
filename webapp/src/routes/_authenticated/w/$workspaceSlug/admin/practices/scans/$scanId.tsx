import { createFileRoute } from "@tanstack/react-router";

import { ScanReportPage } from "@/components/admin/practice-scans/ScanReportPage";
import { scanReportSearchSchema } from "@/components/admin/practice-scans/scans-search";
import { useScanReport } from "@/hooks/use-scan-report";
import { useSearchPatch } from "@/lib/search-params";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/scans/$scanId",
)({
	validateSearch: scanReportSearchSchema,
	component: ScanReportRoute,
});

function ScanReportRoute() {
	const { workspaceSlug, scanId } = Route.useParams();
	const { tab = "problems", baseline, fileStatus } = Route.useSearch();
	const patch = useSearchPatch<{
		tab?: string;
		baseline?: string;
		fileStatus?: string;
	}>();
	const report = useScanReport(workspaceSlug, scanId, { baseline, fileStatus });
	return (
		<ScanReportPage
			workspaceSlug={workspaceSlug}
			{...report}
			tab={tab}
			onTabChange={(next) => patch({ tab: next === "problems" ? undefined : next })}
			problemFilter={baseline}
			onProblemFilterChange={(next) => patch({ baseline: next })}
			fileFilter={fileStatus}
			onFileFilterChange={(next) => patch({ fileStatus: next })}
		/>
	);
}
