import { Link } from "@tanstack/react-router";
import { BookUser, ClipboardCheck, Map as MapIcon, Settings2, Trophy, Users } from "lucide-react";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
} from "@/components/ui/sidebar";

export interface NavAdminProps {
	workspaceSlug: string;
	achievementsEnabled?: boolean;
	practicesEnabled?: boolean;
}

export function NavAdmin({
	workspaceSlug,
	achievementsEnabled = true,
	practicesEnabled = true,
}: NavAdminProps) {
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
				{achievementsEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Manage achievements"
							render={<Link to="/w/$workspaceSlug/admin/achievements" params={{ workspaceSlug }} />}
						>
							<Trophy />
							<span>Manage achievements</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				{practicesEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Manage practices"
							render={<Link to="/w/$workspaceSlug/admin/practices" params={{ workspaceSlug }} />}
						>
							<ClipboardCheck />
							<span>Manage practices</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				{achievementsEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Achievement Designer"
							render={
								<Link
									to="/w/$workspaceSlug/admin/achievement-designer"
									params={{ workspaceSlug }}
								/>
							}
						>
							<MapIcon />
							<span>Achievement Designer</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
			</SidebarMenu>
		</SidebarGroup>
	);
}
