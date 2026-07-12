import { Link } from "@tanstack/react-router";
import { Radar, Sparkles, Sprout, Trophy, User, Users } from "lucide-react";
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
	practicesEnabled = true,
	isAdmin = false,
}: {
	username: string;
	workspaceSlug: string;
	achievementsEnabled?: boolean;
	leaderboardEnabled?: boolean;
	practicesEnabled?: boolean;
	/** Gates the mentor-facing practice overview entry (the route and API are admin/owner only). */
	isAdmin?: boolean;
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
				{practicesEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="My practices"
							render={<Link to="/w/$workspaceSlug/practices" params={{ workspaceSlug }} />}
						>
							<Sprout />
							<span>My practices</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				{practicesEnabled && isAdmin && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Practice overview"
							render={<Link to="/w/$workspaceSlug/practice-overview" params={{ workspaceSlug }} />}
						>
							<Radar />
							<span>Practice overview</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
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
