import { Link, useLocation } from "@tanstack/react-router";
import { Building2, ChevronLeft, ScrollText, Users } from "lucide-react";
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
 * so an admin can reach it even with zero workspaces. Distinct from `NavAdmin`, which is the
 * per-workspace admin nav.
 */
const ADMIN_SECTIONS = [
	{ to: "/admin/users", label: "Users", icon: Users, tooltip: "Manage accounts" },
	{ to: "/admin/workspaces", label: "Workspaces", icon: Building2, tooltip: "All workspaces" },
	{ to: "/admin/audit", label: "Audit log", icon: ScrollText, tooltip: "Auth audit events" },
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
					{ADMIN_SECTIONS.map((section) => (
						<SidebarMenuItem key={section.to}>
							<SidebarMenuButton
								tooltip={section.tooltip}
								isActive={pathname.startsWith(section.to)}
								render={<Link to={section.to} />}
							>
								<section.icon />
								<span>{section.label}</span>
							</SidebarMenuButton>
						</SidebarMenuItem>
					))}
				</SidebarMenu>
			</SidebarGroup>
		</>
	);
}
