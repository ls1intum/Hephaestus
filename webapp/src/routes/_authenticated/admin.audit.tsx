import { useQuery } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { ScrollTextIcon } from "lucide-react";
import { adminListWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import { AuthAuditPanel } from "@/components/admin/audit/AuthAuditPanel";
import { type AuditSearch, auditSearchSchema } from "@/components/admin/audit-shared/auditSearch";
import { AdminConfigAuditPanel } from "@/components/admin/config-audit/ConfigAuditPanel";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

export const Route = createFileRoute("/_authenticated/admin/audit")({
	component: AdminAuditPage,
	validateSearch: auditSearchSchema,
});

function AdminAuditPage() {
	const search = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	// Every filter change is a navigation, so the current view is always the thing in the address bar
	// and always shareable. `replace` keeps a long filtering session from burying the previous page
	// under dozens of history entries.
	const patchSearch = (patch: Partial<AuditSearch>) =>
		navigate({ search: (prev) => ({ ...prev, ...patch }), replace: true });

	// Workspace names are resolved client-side from the admin workspace list (dozens at most), so the
	// audit services don't have to reach into the workspace module just to label a row.
	const workspacesQuery = useQuery(adminListWorkspacesOptions());
	const workspaceNames = new Map(
		(workspacesQuery.data ?? []).map((w) => [w.id, w.displayName || w.workspaceSlug] as const),
	);
	const resolveWorkspaceName = (id: number) => workspaceNames.get(id);

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<header className="space-y-1">
				<div className="flex items-center gap-2">
					<ScrollTextIcon className="size-6 text-muted-foreground" aria-hidden />
					<h1 className="text-2xl font-semibold">Audit log</h1>
				</div>
				<p className="text-sm text-muted-foreground">
					Read-only record of who did what, and when, across the instance. Append-only — entries
					can't be edited or removed. Times are shown in your local timezone (hover for the exact
					UTC instant).
				</p>
			</header>

			<Tabs
				value={search.tab}
				onValueChange={(value) =>
					// The two tabs filter different dimensions; carrying one tab's selection into the other
					// would silently return an unrelated, empty result. Actor and date span both, so they stay.
					navigate({
						search: (prev) => ({
							tab: value as AuditSearch["tab"],
							actorId: prev.actorId,
							from: prev.from,
							to: prev.to,
						}),
						replace: true,
					})
				}
			>
				<TabsList>
					<TabsTrigger value="signins">Sign-ins &amp; accounts</TabsTrigger>
					<TabsTrigger value="settings">Settings changes</TabsTrigger>
				</TabsList>

				<TabsContent value="signins" className="pt-4">
					<AuthAuditPanel
						search={search}
						onSearchChange={patchSearch}
						resolveWorkspaceName={resolveWorkspaceName}
					/>
				</TabsContent>

				<TabsContent value="settings" className="pt-4">
					<AdminConfigAuditPanel
						search={search}
						onSearchChange={patchSearch}
						resolveWorkspaceName={resolveWorkspaceName}
					/>
				</TabsContent>
			</Tabs>
		</div>
	);
}
