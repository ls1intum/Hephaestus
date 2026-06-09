import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { Building2 } from "lucide-react";
import { useDeferredValue, useState } from "react";
import { adminListWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import type { AdminWorkspaceView } from "@/api/types.gen";
import { AdminWorkspacesTable } from "@/components/admin/workspaces/AdminWorkspacesTable";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export const Route = createFileRoute("/_authenticated/admin/workspaces")({
	component: AdminWorkspacesPage,
});

function AdminWorkspacesPage() {
	const [search, setSearch] = useState("");
	const deferredSearch = useDeferredValue(search);

	// The endpoint returns the full (metadata-only) list — there are at most dozens of workspaces, so
	// we load all and filter client-side (matches the admin overview's scale).
	const listQuery = useQuery(adminListWorkspacesOptions());
	const all: AdminWorkspaceView[] = listQuery.data ?? [];

	const term = deferredSearch.trim().toLowerCase();
	const workspaces = term
		? all.filter((ws) =>
				[
					ws.displayName,
					ws.workspaceSlug,
					ws.accountLogin,
					ws.ownerLogin,
					ws.status,
					ws.providerType,
				]
					.filter(Boolean)
					.some((field) => field?.toLowerCase().includes(term)),
			)
		: all;

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<header className="space-y-1">
				<div className="flex items-center gap-2">
					<Building2 className="size-6 text-muted-foreground" aria-hidden />
					<h1 className="text-2xl font-semibold">Workspaces</h1>
				</div>
				<p className="text-sm text-muted-foreground">
					Every workspace on this instance (metadata only). To act inside a workspace, impersonate a
					member — that path is audited.
				</p>
			</header>

			<div className="relative w-full sm:max-w-sm">
				<Label htmlFor="admin-workspaces-search" className="sr-only">
					Search workspaces
				</Label>
				<Building2 className="absolute left-3 top-2.5 size-4 text-muted-foreground" aria-hidden />
				<Input
					id="admin-workspaces-search"
					type="search"
					placeholder="Search by name, slug, owner, provider, or status…"
					value={search}
					onChange={(event) => setSearch(event.target.value)}
					className="pl-9"
				/>
			</div>

			<AdminWorkspacesTable
				workspaces={workspaces}
				isLoading={listQuery.isLoading}
				isError={listQuery.isError}
				hasSearch={term.length > 0}
			/>
		</div>
	);
}
