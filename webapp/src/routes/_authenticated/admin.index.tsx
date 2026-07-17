import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { Building2, Gauge, KeyRound, Users } from "lucide-react";
import {
	adminGetInstanceSettingsOptions,
	adminListAuthEventsOptions,
	adminListLoginProvidersOptions,
	adminListWorkspacesOptions,
} from "@/api/@tanstack/react-query.gen";
import { OverviewStatCard } from "@/components/admin/instance/OverviewStatCard";
import { RecentAuthActivityCard } from "@/components/admin/instance/RecentAuthActivityCard";
import { SilentModeStatusCard } from "@/components/admin/instance/SilentModeStatusCard";

export const Route = createFileRoute("/_authenticated/admin/")({
	component: AdminOverviewPage,
});

/**
 * The instance overview — the operator's "is everything fine" page (#1386). Composed client-side from
 * the existing admin queries; job/sync health and usage/cost tiles slot into the grid when their
 * backends land (#1368, sync-jobs UI).
 */
function AdminOverviewPage() {
	const settingsQuery = useQuery(adminGetInstanceSettingsOptions());
	const workspacesQuery = useQuery(adminListWorkspacesOptions());
	const providersQuery = useQuery(adminListLoginProvidersOptions());
	const eventsQuery = useQuery(adminListAuthEventsOptions({ query: { page: 0, size: 8 } }));

	const workspaces = workspacesQuery.data ?? [];
	const activeWorkspaces = workspaces.filter((ws) => ws.status === "ACTIVE").length;
	// Sum of per-workspace membership counts — a user in N workspaces counts N times, so this is
	// "memberships", not distinct accounts (there is no instance-wide account count endpoint yet).
	const memberships = workspaces.reduce((sum, ws) => sum + ws.memberCount, 0);
	const providers = providersQuery.data ?? [];
	const enabledProviders = providers.filter((p) => p.enabled !== false).length;

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<header className="space-y-1">
				<div className="flex items-center gap-2">
					<Gauge className="size-6 text-muted-foreground" aria-hidden />
					<h1 className="text-2xl font-semibold">Instance overview</h1>
				</div>
				<p className="text-sm text-muted-foreground">
					Delivery state, tenancy at a glance, and the latest auth activity on this instance.
				</p>
			</header>

			<SilentModeStatusCard settings={settingsQuery.data} isLoading={settingsQuery.isLoading} />

			<div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
				<OverviewStatCard
					label="Workspaces"
					value={workspaces.length}
					hint={workspaces.length > 0 ? `${activeWorkspaces} active` : "None created yet"}
					icon={Building2}
					to="/admin/workspaces"
					isLoading={workspacesQuery.isLoading}
				/>
				<OverviewStatCard
					label="Memberships"
					value={memberships}
					hint="Total across workspaces"
					icon={Users}
					to="/admin/users"
					isLoading={workspacesQuery.isLoading}
				/>
				<OverviewStatCard
					label="Login providers"
					value={providers.length}
					hint={providers.length > 0 ? `${enabledProviders} enabled` : "None configured yet"}
					icon={KeyRound}
					to="/admin/login-providers"
					isLoading={providersQuery.isLoading}
				/>
			</div>

			<RecentAuthActivityCard
				events={eventsQuery.data?.content ?? []}
				isLoading={eventsQuery.isLoading}
			/>
		</div>
	);
}
