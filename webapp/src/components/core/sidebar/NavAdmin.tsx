import { Link, useMatchRoute } from "@tanstack/react-router";
import {
	Activity,
	BookUser,
	Bot,
	ChevronRight,
	ClipboardCheck,
	ListChecks,
	Map as MapIcon,
	Settings2,
	SlidersHorizontal,
	Trophy,
	Users,
} from "lucide-react";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
	SidebarMenuSub,
	SidebarMenuSubButton,
	SidebarMenuSubItem,
} from "@/components/ui/sidebar";

export interface NavAdminProps {
	workspaceSlug: string;
	achievementsEnabled?: boolean;
	practicesEnabled?: boolean;
	mentorEnabled?: boolean;
}

// Workspace administration is one group. Practice detection is the AI-review feature and owns several
// surfaces (the rubric, its review settings, and the run/activity log), so it nests as a collapsible
// sub-tree rather than scattering those surfaces as flat siblings — this is where Review settings lives
// (a sidebar destination, not an in-page tab). "AI models" stays a top-level item because models are
// shared infrastructure (the mentor uses them too), not specific to practice detection. Individual
// practices/areas are deliberately NOT sidebar entries — there are dozens; the Rubric tree is their home.
export function NavAdmin({
	workspaceSlug,
	achievementsEnabled = true,
	practicesEnabled = true,
	mentorEnabled = false,
}: NavAdminProps) {
	const aiVisible = practicesEnabled || mentorEnabled;
	const matchRoute = useMatchRoute();

	const onSettings = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/ai/practice-detection/settings", fuzzy: true }),
	);
	const onActivity = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/ai/practice-detection/activity", fuzzy: true }),
	);
	const onSection = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/ai/practice-detection", fuzzy: true }),
	);
	// Rubric is the section's index; the catalog editor drills down under it, so anything in the section
	// that isn't Settings or Activity counts as Rubric.
	const onRubric = onSection && !onSettings && !onActivity;

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
				{practicesEnabled && (
					<Collapsible defaultOpen={onSection} render={<SidebarMenuItem />}>
						<CollapsibleTrigger
							render={
								<SidebarMenuButton
									tooltip="Practice detection"
									isActive={onSection}
									className="group/collapsible"
								>
									<ClipboardCheck />
									<span>Practice detection</span>
									<ChevronRight className="ml-auto transition-transform group-aria-expanded/collapsible:rotate-90" />
								</SidebarMenuButton>
							}
						/>
						<CollapsibleContent>
							<SidebarMenuSub>
								<SidebarMenuSubItem>
									<SidebarMenuSubButton
										isActive={onRubric}
										render={
											<Link
												to="/w/$workspaceSlug/admin/ai/practice-detection"
												params={{ workspaceSlug }}
											/>
										}
									>
										<ListChecks />
										<span>Rubric</span>
									</SidebarMenuSubButton>
								</SidebarMenuSubItem>
								<SidebarMenuSubItem>
									<SidebarMenuSubButton
										isActive={onSettings}
										render={
											<Link
												to="/w/$workspaceSlug/admin/ai/practice-detection/settings"
												params={{ workspaceSlug }}
											/>
										}
									>
										<SlidersHorizontal />
										<span>Review settings</span>
									</SidebarMenuSubButton>
								</SidebarMenuSubItem>
								<SidebarMenuSubItem>
									<SidebarMenuSubButton
										isActive={onActivity}
										render={
											<Link
												to="/w/$workspaceSlug/admin/ai/practice-detection/activity"
												params={{ workspaceSlug }}
											/>
										}
									>
										<Activity />
										<span>Activity</span>
									</SidebarMenuSubButton>
								</SidebarMenuSubItem>
							</SidebarMenuSub>
						</CollapsibleContent>
					</Collapsible>
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
			</SidebarMenu>
		</SidebarGroup>
	);
}
