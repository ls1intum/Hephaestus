import { Link } from "@tanstack/react-router";
import { Users } from "lucide-react";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
} from "@/components/ui/sidebar";

/**
 * Application-wide (super-admin / APP_ADMIN) navigation. Distinct from `NavAdmin`,
 * which is scoped to the active workspace. Rendered only when the signed-in user is an
 * application admin (see `AppSidebar.isAppAdmin`).
 */
export function NavSuperAdmin() {
	return (
		<SidebarGroup>
			<SidebarGroupLabel>Admin</SidebarGroupLabel>
			<SidebarMenu>
				<SidebarMenuItem>
					<SidebarMenuButton tooltip="Manage users" render={<Link to="/admin/users" />}>
						<Users />
						<span>Users</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		</SidebarGroup>
	);
}
