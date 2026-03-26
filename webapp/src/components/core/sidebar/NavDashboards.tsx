import { Link } from "@tanstack/react-router";
import { Sparkles, Trophy, User, Users } from "lucide-react";
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
	achievementsEnabled = true,
	leaderboardEnabled = true,
}: {
	username: string;
	workspaceSlug: string;
	achievementsEnabled?: boolean;
	leaderboardEnabled?: boolean;
}) {
	return (
		<SidebarGroup>
			<SidebarGroupLabel>Dashboards</SidebarGroupLabel>
			<SidebarMenu>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Profile"
						render={
							<Link
								to="/w/$workspaceSlug/user/$username"
								params={{ username: username ?? "", workspaceSlug }}
							/>
						}
					>
						<User />
						<span>Profile</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				{achievementsEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Achievements"
							render={<Link to="/w/$workspaceSlug/achievements" params={{ workspaceSlug }} />}
						>
							<Sparkles />
							<span>Achievements</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				{leaderboardEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Leaderboard"
							render={<Link to="/w/$workspaceSlug" params={{ workspaceSlug }} />}
						>
							<Trophy />
							<span>Leaderboard</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Teams"
						render={<Link to="/w/$workspaceSlug/teams" params={{ workspaceSlug }} />}
					>
						<Users />
						<span>Teams</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		</SidebarGroup>
	);
}
