import { Link, useLocation } from "@tanstack/react-router";
import {
	BrainCircuit,
	Building2,
	ChevronLeft,
	CircleDollarSign,
	KeyRound,
	ScrollText,
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
 * so an admin can reach it even with zero workspaces. Distinct from `NavAdmin`, which is the
 * per-workspace admin nav.
 */
// Ordered to share a spine with the per-workspace `NavAdmin`: people, then scope, then sign-in, then
// the AI pair, then the log. Both consoles use the same word for the same object ("AI models",
// "AI usage", "Audit log") — the sidebar's group label is what carries the scope.
const ADMIN_SECTIONS = [
	{ to: "/admin/users", label: "Users", icon: Users, tooltip: "Manage accounts" },
	{ to: "/admin/workspaces", label: "Workspaces", icon: Building2, tooltip: "All workspaces" },
	{
		to: "/admin/login-providers",
		label: "Login providers",
		icon: KeyRound,
		tooltip: "Sign-in options",
	},
	{
		to: "/admin/models",
		label: "AI models",
		icon: BrainCircuit,
		tooltip: "Providers and shared models",
	},
	{
		to: "/admin/usage",
		label: "AI usage",
		icon: CircleDollarSign,
		tooltip: "AI spend and budget caps",
	},
	{
		to: "/admin/audit",
		label: "Audit log",
		icon: ScrollText,
		tooltip: "Who did what, and when",
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
