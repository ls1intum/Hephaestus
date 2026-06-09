import { Link } from "@tanstack/react-router";
import {
	Activity,
	BookUser,
	Bot,
	ClipboardCheck,
	Map as MapIcon,
	Settings2,
	Trophy,
	Users,
} from "lucide-react";
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
	mentorEnabled?: boolean;
}

// The AI config (models, practice detection, activity) is workspace administration, so it lives in the
// one Administration group rather than a separate "AI" section — with only a few items a second group
// adds a heading and (in the icon-collapsed rail, where group labels vanish) zero disambiguation.
export function NavAdmin({
	workspaceSlug,
	achievementsEnabled = true,
	practicesEnabled = true,
	mentorEnabled = false,
}: NavAdminProps) {
	const aiVisible = practicesEnabled || mentorEnabled;

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
				{achievementsEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Achievement designer"
							render={
								<Link
									to="/w/$workspaceSlug/admin/achievement-designer"
									params={{ workspaceSlug }}
								/>
							}
						>
							<MapIcon />
							<span>Achievement designer</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				{aiVisible && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="AI models"
							render={<Link to="/w/$workspaceSlug/admin/ai/agents" params={{ workspaceSlug }} />}
						>
							<Bot />
							<span>AI models</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				{practicesEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Practice detection"
							render={
								<Link
									to="/w/$workspaceSlug/admin/ai/practice-detection"
									params={{ workspaceSlug }}
								/>
							}
						>
							<ClipboardCheck />
							<span>Practice detection</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				{aiVisible && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Activity"
							render={<Link to="/w/$workspaceSlug/admin/ai/activity" params={{ workspaceSlug }} />}
						>
							<Activity />
							<span>Activity</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
			</SidebarMenu>
		</SidebarGroup>
	);
}
