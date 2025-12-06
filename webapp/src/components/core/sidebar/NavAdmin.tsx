import { Link } from "@tanstack/react-router";
import { BookUser, Settings2, Users } from "lucide-react";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
} from "@/components/ui/sidebar";

export function NavAdmin({ workspaceSlug }: { workspaceSlug: string }) {
	return (
		<SidebarGroup>
			<SidebarGroupLabel>Administration</SidebarGroupLabel>
			<SidebarMenu>
				<SidebarMenuItem>
					<SidebarMenuButton asChild tooltip="Manage workspace">
						<Link
							to="/w/$workspaceSlug/admin/settings"
							params={{ workspaceSlug }}
						>
							<Settings2 />
							<span>Manage workspace</span>
						</Link>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton asChild tooltip="Manage members">
						<Link
							to="/w/$workspaceSlug/admin/members"
							params={{ workspaceSlug }}
						>
							<BookUser />
							<span>Manage members</span>
						</Link>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton asChild tooltip="Manage teams">
						<Link to="/w/$workspaceSlug/admin/teams" params={{ workspaceSlug }}>
							<Users />
							<span>Manage teams</span>
						</Link>
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		</SidebarGroup>
	);
}
