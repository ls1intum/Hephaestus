import { createFileRoute, useNavigate } from "@tanstack/react-router";

import {
	GUARDED_SCAN_LEVEL_KINDS,
	SCAN_LEVEL_KINDS,
	scansSearchSchema,
} from "@/components/admin/practice-scans/scans-search";
import { ScansPage } from "@/components/admin/practice-scans/ScansPage";
import { StartScanForm } from "@/components/admin/practice-scans/StartScanForm";
import { parseDetailStack } from "@/components/layout/detail-drawer/detail-stack";
import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import { LevelCancel } from "@/components/layout/detail-drawer/LevelCancel";
import { useDetailStack } from "@/components/layout/detail-drawer/use-detail-stack";
import { useScanList, useStartScan } from "@/hooks/use-scans";
import { pageParam, useSearchState } from "@/lib/search-params";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/scans/")({
	validateSearch: scansSearchSchema,
	component: ScansRoute,
});

function ScansRoute() {
	const { workspaceSlug } = Route.useParams();
	const { detail, page = 0 } = Route.useSearch();
	const setSearch = useSearchState();
	const stack = parseDetailStack(detail, SCAN_LEVEL_KINDS);
	const stackControls = useDetailStack();
	const scans = useScanList(workspaceSlug, page, (next) => {
		void setSearch((previous) => ({ ...previous, page: pageParam(next) }));
	});
	return (
		<ScansPage workspaceSlug={workspaceSlug} scans={scans}>
			<DetailDrawerStack
				stack={stack}
				guardedKinds={GUARDED_SCAN_LEVEL_KINDS}
				onClose={stackControls.close}
			>
				{(_entry, level) => <StartScanLevel workspaceSlug={workspaceSlug} nested={level.nested} />}
			</DetailDrawerStack>
		</ScansPage>
	);
}

function StartScanLevel({ workspaceSlug, nested }: { workspaceSlug: string; nested: boolean }) {
	const navigate = useNavigate();
	const startScan = useStartScan(workspaceSlug, (scan) => {
		void navigate({
			to: "/w/$workspaceSlug/admin/practices/scans/$scanId",
			params: { workspaceSlug, scanId: scan.id },
		});
	});
	return (
		<StartScanForm
			workspaceSlug={workspaceSlug}
			nested={nested}
			options={startScan.options}
			pending={startScan.pending}
			refusal={startScan.refusal}
			expectedVersion={startScan.expectedVersion}
			cancel={<LevelCancel />}
			onSubmit={startScan.start}
		/>
	);
}
