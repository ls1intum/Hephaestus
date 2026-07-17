import { Link, useLocation } from "@tanstack/react-router";
import {
	Building2,
	ChevronLeft,
	Gauge,
	KeyRound,
	ScrollText,
	Settings2,
	Users,
} from "lucide-react";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
	SidebarSeparator,
} from "@/components/ui/sidebar";

/**
 * Content of the dedicated instance-admin (APP_ADMIN) shell — the `context === "admin"` sidebar.
 * It is workspace-independent (no workspace switcher; a "Back to app" link returns to the dashboard)
 * so an admin can reach it even with zero workspaces. Distinct from `NavAdmin`, the per-workspace
 * admin nav — the instance and workspace levels are never interleaved.
 *
 * Grouped (#1386). A "Detection" group (catalog, labeling, evaluation) joins once #1361/#1365 land —
 * add a new entry to `ADMIN_NAV_GROUPS`.
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
		label: "Operations",
		items: [
			{ to: "/admin/audit", label: "Audit log", icon: ScrollText, tooltip: "Auth audit events" },
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
		<>
			<SidebarMenuButton render={<Link to="/" className="font-semibold" />}>
				<ChevronLeft className="h-4 w-4" />
				Back to app
			</SidebarMenuButton>
			<SidebarSeparator />
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
		</>
	);
}
