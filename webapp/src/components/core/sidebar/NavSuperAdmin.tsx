import { Link, useLocation } from "@tanstack/react-router";
import {
	BrainCircuit,
	Building2,
	CircleDollarSign,
	KeyRound,
	LibraryBig,
	ScrollText,
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
 * so an admin can reach it even with zero workspaces. Distinct from `NavAdmin`, which is the
 * per-workspace admin nav.
 */
const ADMIN_SECTIONS = [
	{ to: "/admin/users", label: "Users", icon: Users, tooltip: "Manage accounts" },
	{ to: "/admin/workspaces", label: "Workspaces", icon: Building2, tooltip: "All workspaces" },
	{
		to: "/admin/catalog",
		label: "Practice catalog",
		icon: LibraryBig,
		tooltip: "What new workspaces receive",
	},
	{
		to: "/admin/login-providers",
		label: "Login providers",
		icon: KeyRound,
		tooltip: "Sign-in options",
	},
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
	{
		to: "/admin/audit",
		label: ADMIN_NAV_LABELS.audit,
		icon: ScrollText,
		tooltip: "Who did what, and when",
	},
] as const;

export function NavSuperAdmin() {
	const { pathname } = useLocation();
	return (
		<NavContextHeader title="Back to app" backLink={<Link to="/" className="font-semibold" />}>
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
		</NavContextHeader>
	);
}
