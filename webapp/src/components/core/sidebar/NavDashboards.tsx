import { Link } from "@tanstack/react-router";
import { Radar, Sparkles, User, Users } from "lucide-react";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
} from "@/components/ui/sidebar";

export function NavDashboards({
	workspaceSlug,
	achievementsEnabled = true,
	practicesEnabled = true,
	isAdmin = false,
	healthVisibility = "MENTORS_ONLY",
}: {
	workspaceSlug: string;
	achievementsEnabled?: boolean;
	practicesEnabled?: boolean;
	isAdmin?: boolean;
	healthVisibility?: "MENTORS_ONLY" | "EVERYONE";
}) {
	const canSeePracticeOverview = isAdmin || healthVisibility === "EVERYONE";

	return (
		<SidebarGroup>
			<SidebarGroupLabel>Dashboards</SidebarGroupLabel>
			<SidebarMenu>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Profile"
						render={<Link to="/w/$workspaceSlug" params={{ workspaceSlug }} />}
					>
						<User />
						<span>Profile</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				{practicesEnabled && canSeePracticeOverview && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Practice Overview"
							render={<Link to="/w/$workspaceSlug/practice-overview" params={{ workspaceSlug }} />}
						>
							<Radar />
							<span>Practice Overview</span>
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
