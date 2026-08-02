import { Link, useLocation } from "@tanstack/react-router";
import {
	BrainCircuit,
	Building2,
	CircleDollarSign,
	Gauge,
	KeyRound,
	LibraryBig,
	ScrollText,
	Settings2,
	Users,
} from "lucide-react";
import { ADMIN_NAV_LABELS } from "@/components/core/sidebar/admin-nav-labels";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
} from "@/components/ui/sidebar";
import { NavContextHeader } from "./NavContextHeader";

/**
 * Content of the dedicated instance-admin (APP_ADMIN) shell — the `context === "admin"` sidebar.
 * It is workspace-independent (no workspace switcher; a "Back to app" link returns to the dashboard)
 * so an admin can reach it even with zero workspaces. Distinct from `NavAdmin`, the per-workspace
 * admin nav — the instance and workspace levels are never interleaved.
 *
 * Grouped (#1386) so the surfaces the 1.0 backlog keeps adding have a home rather than extending one
 * flat list: who can get in (Access), what we detect (Detection), what it costs (AI), and running the
 * instance (Operations). Labeling and evaluation join Detection as they land.
 */
const ADMIN_NAV_GROUPS = [
	{
		label: "Access",
		items: [
			{ to: "/admin/users", label: "Users", icon: Users, tooltip: "Manage accounts" },
			{ to: "/admin/workspaces", label: "Workspaces", icon: Building2, tooltip: "All workspaces" },
			{
				to: "/admin/login-providers",
				label: "Login providers",
				icon: KeyRound,
				tooltip: "Sign-in options",
			},
		],
	},
	{
		label: "Detection",
		items: [
			{
				to: "/admin/catalog",
				label: "Practice catalog",
				icon: LibraryBig,
				tooltip: "What new workspaces receive",
			},
		],
	},
	{
		label: "AI",
		items: [
			{
				to: "/admin/models",
				label: ADMIN_NAV_LABELS.models,
				icon: BrainCircuit,
				tooltip: "Providers and shared models",
			},
			{
				to: "/admin/usage",
				label: ADMIN_NAV_LABELS.usage,
				icon: CircleDollarSign,
				tooltip: "AI spend and budget caps",
			},
		],
	},
	{
		label: "Operations",
		items: [
			{
				to: "/admin/audit",
				label: ADMIN_NAV_LABELS.audit,
				icon: ScrollText,
				tooltip: "Who did what, and when",
			},
			{
				to: "/admin/settings",
				label: "Instance settings",
				icon: Settings2,
				tooltip: "Silent mode and instance controls",
			},
		],
	},
] as const;

export function NavSuperAdmin() {
	const { pathname } = useLocation();
	return (
		<NavContextHeader title="Back to app" backLink={<Link to="/" className="font-semibold" />}>
			<SidebarGroup>
				<SidebarGroupLabel>Instance administration</SidebarGroupLabel>
				<SidebarMenu>
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Instance overview"
							isActive={pathname === "/admin" || pathname === "/admin/"}
							render={<Link to="/admin" />}
						>
							<Gauge />
							<span>Overview</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				</SidebarMenu>
			</SidebarGroup>
			{ADMIN_NAV_GROUPS.map((group) => (
				<SidebarGroup key={group.label}>
					<SidebarGroupLabel>{group.label}</SidebarGroupLabel>
					<SidebarMenu>
						{group.items.map((item) => (
							<SidebarMenuItem key={item.to}>
								<SidebarMenuButton
									tooltip={item.tooltip}
									isActive={pathname.startsWith(item.to)}
									render={<Link to={item.to} />}
								>
									<item.icon />
									<span>{item.label}</span>
								</SidebarMenuButton>
							</SidebarMenuItem>
						))}
					</SidebarMenu>
				</SidebarGroup>
			))}
		</NavContextHeader>
	);
}
