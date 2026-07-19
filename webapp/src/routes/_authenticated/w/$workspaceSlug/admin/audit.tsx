import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { ScrollTextIcon } from "lucide-react";
import {
	type ConfigAuditSearch,
	workspaceAuditSearchSchema,
} from "@/components/admin/audit-shared/auditSearch";
import { WorkspaceConfigAuditPanel } from "@/components/admin/config-audit/ConfigAuditPanel";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/audit")({
	component: WorkspaceAuditPage,
	validateSearch: workspaceAuditSearchSchema,
});

/**
 * The workspace's own audit log. Sign-in events are instance-scoped and stay with the instance
 * admin, so this surface is the settings trail alone — rendered by the same panel the instance tab
 * uses, narrowed to this workspace.
 */
function WorkspaceAuditPage() {
	// The slug is validated by the admin layout's beforeLoad, so it is always present here.
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	const patchSearch = (patch: Partial<ConfigAuditSearch>) =>
		navigate({ search: (prev) => ({ ...prev, ...patch }), replace: true });

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<header className="space-y-1">
				<div className="flex items-center gap-2">
					<ScrollTextIcon className="size-6 text-muted-foreground" aria-hidden />
					<h1 className="text-2xl font-semibold">Audit log</h1>
				</div>
				<p className="text-sm text-muted-foreground">
					Who changed which setting in this workspace, and when. Append-only — entries can't be
					edited or removed. Times are shown in your local timezone; open a row for the exact UTC
					instant.
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
