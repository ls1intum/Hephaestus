import { Link } from "@tanstack/react-router";
import { BookUser, Map as MapIcon, Settings2, Trophy, Users } from "lucide-react";
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
					<SidebarMenuButton
						tooltip="Manage workspace"
						render={<Link to="/w/$workspaceSlug/admin/settings" params={{ workspaceSlug }} />}
					>
						<Settings2 />
						<span>Manage workspace</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Manage members"
						render={<Link to="/w/$workspaceSlug/admin/members" params={{ workspaceSlug }} />}
					>
						<BookUser />
						<span>Manage members</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Manage teams"
						render={<Link to="/w/$workspaceSlug/admin/teams" params={{ workspaceSlug }} />}
					>
						<Users />
						<span>Manage teams</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Manage achievements"
						render={<Link to="/w/$workspaceSlug/admin/achievements" params={{ workspaceSlug }} />}
					>
						<Trophy />
						<span>Manage achievements</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Achievement Designer"
						render={
							<Link to="/w/$workspaceSlug/admin/achievement-designer" params={{ workspaceSlug }} />
						}
					>
						<MapIcon />
						<span>Achievement Designer</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		</SidebarGroup>
	);
}
