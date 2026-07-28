import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { ScrollTextIcon } from "lucide-react";
import {
	type ConfigAuditSearch,
	workspaceAuditSearchSchema,
} from "@/components/admin/audit-shared/audit-search";
import { WorkspaceConfigAuditPanel } from "@/components/admin/config-audit/ConfigAuditPanel";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/audit")({
	head: workspaceAdminHead("Audit log"),
	component: WorkspaceAuditPage,
	validateSearch: workspaceAuditSearchSchema,
});

/** The settings trail alone: sign-in events are instance-scoped and stay with the instance admin. */
function WorkspaceAuditPage() {
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	const patchSearch = (patch: Partial<ConfigAuditSearch>) =>
		navigate({ search: (prev) => ({ ...prev, ...patch }), replace: true });

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6">
			<header className="space-y-1">
				<div className="flex items-center gap-2">
					<ScrollTextIcon className="size-6 text-muted-foreground" aria-hidden />
					<h1 className="text-2xl font-semibold">Audit log</h1>
				</div>
				<p className="text-sm text-muted-foreground">
					A permanent record of who changed which settings in this workspace.
				</p>
			</header>

			<WorkspaceConfigAuditPanel
				search={search}
				onSearchChange={patchSearch}
				workspaceSlug={workspaceSlug}
			/>
		</div>
	);
}
