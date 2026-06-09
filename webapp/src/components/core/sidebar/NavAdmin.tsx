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

export function NavAdmin({
	workspaceSlug,
	achievementsEnabled = true,
	practicesEnabled = true,
	mentorEnabled = false,
}: NavAdminProps) {
	const aiGroupVisible = practicesEnabled || mentorEnabled;

	return (
		<>
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
								render={
									<Link to="/w/$workspaceSlug/admin/achievements" params={{ workspaceSlug }} />
								}
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
				</SidebarMenu>
			</SidebarGroup>

			{aiGroupVisible && (
				<SidebarGroup>
					<SidebarGroupLabel>AI</SidebarGroupLabel>
					<SidebarMenu>
						<SidebarMenuItem>
							<SidebarMenuButton
								tooltip="Agents"
								render={<Link to="/w/$workspaceSlug/admin/ai/agents" params={{ workspaceSlug }} />}
							>
								<Bot />
								<span>Agents</span>
							</SidebarMenuButton>
						</SidebarMenuItem>
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
						<SidebarMenuItem>
							<SidebarMenuButton
								tooltip="Activity"
								render={
									<Link to="/w/$workspaceSlug/admin/ai/activity" params={{ workspaceSlug }} />
								}
							>
								<Activity />
								<span>Activity</span>
							</SidebarMenuButton>
						</SidebarMenuItem>
					</SidebarMenu>
				</SidebarGroup>
			)}
		</>
	);
}
