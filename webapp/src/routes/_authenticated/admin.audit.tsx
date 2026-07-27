import { useQuery } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { ScrollTextIcon } from "lucide-react";
import { adminListWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import { AuthAuditPanel } from "@/components/admin/audit/AuthAuditPanel";
import { type AuditSearch, auditSearchSchema } from "@/components/admin/audit-shared/audit-search";
import { AdminConfigAuditPanel } from "@/components/admin/config-audit/ConfigAuditPanel";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { instanceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/admin/audit")({
	head: instanceAdminHead("Audit log"),
	component: AdminAuditPage,
	validateSearch: auditSearchSchema,
});

function AdminAuditPage() {
	const search = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	// `replace`, so filter churn does not bury the previous page under dozens of history entries. The
	// tab switch below deliberately does not: it discards the other tab's selection, and Back is the
	// only way to undo that.
	const patchSearch = (patch: Partial<AuditSearch>) =>
		navigate({ search: (prev) => ({ ...prev, ...patch }), replace: true });

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
					A permanent record of sign-ins, permission changes, and settings changes across this
					instance.
				</p>
			</header>

			<Tabs
				className="gap-4"
				value={search.tab}
				onValueChange={(value) =>
					// The tabs filter different dimensions, so only actor and date carry across; the rest
					// would silently return an unrelated, empty result.
					navigate({
						search: (prev) => ({
							tab: value as AuditSearch["tab"],
							actorId: prev.actorId,
							from: prev.from,
							to: prev.to,
						}),
					})
				}
			>
				<TabsList className="h-10 w-full p-1 sm:w-fit">
					<TabsTrigger value="signins">Access</TabsTrigger>
					<TabsTrigger value="settings">Settings</TabsTrigger>
				</TabsList>

				<TabsContent value="signins">
					<AuthAuditPanel
						search={search}
						onSearchChange={patchSearch}
						resolveWorkspaceName={resolveWorkspaceName}
					/>
				</TabsContent>

				<TabsContent value="settings">
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
