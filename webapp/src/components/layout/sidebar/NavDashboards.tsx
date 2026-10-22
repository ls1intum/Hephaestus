import { Link, useMatchRoute } from "@tanstack/react-router";
import { Activity, Building2, ChartNoAxesGantt, Compass, UserRound, Users } from "lucide-react";

import { ACROSS_THE_WORKSPACE } from "@/components/practices-across-the-workspace/across-workspace-copy";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
	SidebarMenuSubButton,
	SidebarMenuSubItem,
	useSidebar,
} from "@/components/ui/sidebar";

import { NavSection } from "./NavSection";

export function NavDashboards({
	workspaceSlug,
	practicesEnabled,
}: {
	workspaceSlug: string;
	practicesEnabled: boolean;
}) {
	const matchRoute = useMatchRoute();
	const onActivity = Boolean(matchRoute({ to: "/w/$workspaceSlug/activity", fuzzy: true }));
	const onPracticeProfile = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/practice-profile", fuzzy: true }),
	);
	const onAcrossTheWorkspace = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/practices-across-the-workspace", fuzzy: true }),
	);
	const onWorkspaceActivity = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/workspace-activity", fuzzy: true }),
	);
	const onTeams = Boolean(matchRoute({ to: "/w/$workspaceSlug/teams", fuzzy: true }));
	// On a phone the sidebar is a sheet with full labels, never icon-only.
	const { isMobile, state } = useSidebar();
	const iconOnly = !isMobile && state === "collapsed";
	const profileLink = <Link to="/w/$workspaceSlug/practice-profile" params={{ workspaceSlug }} />;
	const acrossLink = (
		<Link to="/w/$workspaceSlug/practices-across-the-workspace" params={{ workspaceSlug }} />
	);

	return (
		<SidebarGroup>
			<SidebarGroupLabel>Dashboards</SidebarGroupLabel>
			<SidebarMenu>
				{/* Folds like the admin sections: the row opens and closes its pages, the profile among them.
				    Icon-only, the row is the profile's own link, so it stays one press away. */}
				{practicesEnabled && (
					<NavSection
						label="Practice profile"
						icon={<Compass />}
						// Anywhere in the section, the profile with its slide-in layers included, opens it.
						active={onPracticeProfile || onAcrossTheWorkspace}
						landingLink={profileLink}
						// Icon-only, the workspace view has its own icon below, so one entry is active.
						landingActive={onPracticeProfile}
					>
						<SidebarMenuSubItem>
							<SidebarMenuSubButton isActive={onPracticeProfile} render={profileLink}>
								<UserRound aria-hidden />
								<span>Practice profile</span>
							</SidebarMenuSubButton>
						</SidebarMenuSubItem>
						<SidebarMenuSubItem>
							<SidebarMenuSubButton isActive={onAcrossTheWorkspace} render={acrossLink}>
								<ChartNoAxesGantt aria-hidden />
								<span>{ACROSS_THE_WORKSPACE}</span>
							</SidebarMenuSubButton>
						</SidebarMenuSubItem>
					</NavSection>
				)}
				{/* Icon-only, nothing can unfold, so the view gets its own icon below instead. */}
				{practicesEnabled && iconOnly && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip={ACROSS_THE_WORKSPACE}
							isActive={onAcrossTheWorkspace}
							render={acrossLink}
						>
							<ChartNoAxesGantt />
							<span>{ACROSS_THE_WORKSPACE}</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Activity"
						isActive={onActivity}
						render={<Link to="/w/$workspaceSlug/activity" params={{ workspaceSlug }} />}
					>
						<Activity />
						<span>Activity</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Workspace activity"
						isActive={onWorkspaceActivity}
						render={<Link to="/w/$workspaceSlug/workspace-activity" params={{ workspaceSlug }} />}
					>
						<Building2 />
						<span>Workspace activity</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Teams"
						isActive={onTeams}
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
