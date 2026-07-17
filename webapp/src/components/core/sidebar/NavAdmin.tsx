import { Link, useMatchRoute } from "@tanstack/react-router";
import {
	BookUser,
	Bot,
	ChevronRight,
	CircleDollarSign,
	ClipboardCheck,
	ListChecks,
	Map as MapIcon,
	PlayCircle,
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

// Workspace administration is one group. "Practices" is the domain — a workspace curates a catalog of
// practices and the observations/feedback they produce. It owns several surfaces (the catalog, how
// reviews run, and the run log), so it nests as a collapsible sub-tree. The model/agent/"AI" plumbing is
// deliberately NOT the headline: "Models" is a separate workspace-infrastructure item (shared with the
// mentor). Individual practices/areas are not sidebar entries — there are dozens; the catalog is their
// home, and a practice drills down to its own detail page.
export function NavAdmin({
	workspaceSlug,
	achievementsEnabled = true,
	practicesEnabled = true,
	mentorEnabled = false,
}: NavAdminProps) {
	const modelsVisible = practicesEnabled || mentorEnabled;
	const matchRoute = useMatchRoute();

	const onSettings = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/practices/settings", fuzzy: true }),
	);
	const onRuns = Boolean(matchRoute({ to: "/w/$workspaceSlug/admin/practices/runs", fuzzy: true }));
	const onSection = Boolean(matchRoute({ to: "/w/$workspaceSlug/admin/practices", fuzzy: true }));
	// Catalog is the section's index; a practice's detail page drills down under it, so anything in the
	// section that isn't Review settings or Runs counts as Catalog.
	const onCatalog = onSection && !onSettings && !onRuns;

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
									tooltip="Practices"
									isActive={onSection}
									className="group/collapsible"
								>
									<ClipboardCheck />
									<span>Practices</span>
									<ChevronRight className="ml-auto transition-transform group-aria-expanded/collapsible:rotate-90" />
								</SidebarMenuButton>
							}
						/>
						<CollapsibleContent>
							<SidebarMenuSub>
								<SidebarMenuSubItem>
									<SidebarMenuSubButton
										isActive={onCatalog}
										render={
											<Link to="/w/$workspaceSlug/admin/practices" params={{ workspaceSlug }} />
										}
									>
										<ListChecks />
										<span>Catalog</span>
									</SidebarMenuSubButton>
								</SidebarMenuSubItem>
								<SidebarMenuSubItem>
									<SidebarMenuSubButton
										isActive={onSettings}
										render={
											<Link
												to="/w/$workspaceSlug/admin/practices/settings"
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
										isActive={onRuns}
										render={
											<Link
												to="/w/$workspaceSlug/admin/practices/runs"
												params={{ workspaceSlug }}
											/>
										}
									>
										<PlayCircle />
										<span>Runs</span>
									</SidebarMenuSubButton>
								</SidebarMenuSubItem>
							</SidebarMenuSub>
						</CollapsibleContent>
					</Collapsible>
				)}
				{modelsVisible && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Models"
							render={<Link to="/w/$workspaceSlug/admin/models" params={{ workspaceSlug }} />}
						>
							<Bot />
							<span>Models</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="AI usage"
						render={<Link to="/w/$workspaceSlug/admin/usage" params={{ workspaceSlug }} />}
					>
						<CircleDollarSign />
						<span>Usage</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		</SidebarGroup>
	);
}
