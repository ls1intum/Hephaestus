import { Link } from "@tanstack/react-router";
import { CheckCheck, Trophy, User, Users } from "lucide-react";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
} from "@/components/ui/sidebar";

export function NavDashboards({
	username,
	workspaceSlug,
}: {
	username: string;
	workspaceSlug: string;
}) {
	return (
		<SidebarGroup>
			<SidebarGroupLabel>Dashboards</SidebarGroupLabel>
			<SidebarMenu>
				<SidebarMenuItem>
					<SidebarMenuButton asChild tooltip="Leaderboard">
						<Link to="/w/$workspaceSlug" params={{ workspaceSlug }}>
							<Trophy />
							<span>Leaderboard</span>
						</Link>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton asChild tooltip="Best practices">
						<Link
							to="/w/$workspaceSlug/user/$username/best-practices"
							params={{ workspaceSlug, username }}
						>
							<CheckCheck />
							<span>Best practices</span>
						</Link>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton asChild tooltip="Profile">
						<Link
							to="/w/$workspaceSlug/user/$username"
							params={{ username: username ?? "", workspaceSlug }}
						>
							<User />
							<span>Profile</span>
						</Link>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton asChild tooltip="Teams">
						<Link to="/w/$workspaceSlug/teams" params={{ workspaceSlug }}>
							<Users />
							<span>Teams</span>
						</Link>
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		</SidebarGroup>
	);
}
